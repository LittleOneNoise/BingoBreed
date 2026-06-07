package fr.lilone.bingobreed.sniffer.capture

import fr.lilone.bingobreed.sniffer.model.CapturedPacket
import fr.lilone.bingobreed.sniffer.model.ServerEndpoint
import fr.lilone.bingobreed.sniffer.util.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.pcap4j.core.BpfProgram.BpfCompileMode
import org.pcap4j.core.NotOpenException
import org.pcap4j.core.PcapNetworkInterface
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.TcpPacket
import java.net.InetSocketAddress
import java.util.concurrent.TimeoutException

/**
 * Capture pcap4j sur une interface donnée, filtrée sur un ensemble d'endpoints,
 * exposée sous forme de [Flow] froid.
 *
 * Le flux s'exécute sur [Dispatchers.IO]. La lecture native bloque au plus
 * [timeoutMs] puis lève [TimeoutException] : cela donne un point de coopération
 * régulier pour réagir à l'annulation de la coroutine (fermeture propre du handle).
 */
class PacketCapture(
    private val nif: PcapNetworkInterface,
    private val endpoints: List<ServerEndpoint>,
    private val snapLen: Int = SNAP_LEN,
    private val timeoutMs: Int = READ_TIMEOUT_MS,
) {
    private val log = logger()

    fun packets(): Flow<CapturedPacket> = flow {
        require(endpoints.isNotEmpty()) { "Aucun endpoint à surveiller" }
        val filter = BpfFilters.tcpHostsAndPorts(endpoints)
        val handle = nif.openLive(snapLen, PromiscuousMode.PROMISCUOUS, timeoutMs)
        log.debug("Capture ouverte sur {} | filtre BPF: {}", nif.name, filter)
        var count = 0L
        try {
            handle.setFilter(filter, BpfCompileMode.OPTIMIZE)
            while (currentCoroutineContext().isActive) {
                val raw = try {
                    handle.getNextPacketEx()
                } catch (_: TimeoutException) {
                    continue // timeout de lecture : on reboucle pour tester l'annulation
                } catch (_: NotOpenException) {
                    break
                }
                raw.toCapturedPacket()?.let { emit(it); count++ }
            }
        } finally {
            if (handle.isOpen) handle.close()
            log.debug("Capture fermée sur {} ({} paquet(s) capturé(s))", nif.name, count)
        }
    }.flowOn(Dispatchers.IO)

    /** Décode les couches IPv4/TCP. Renvoie null si ce n'est pas du TCP/IPv4. */
    private fun org.pcap4j.packet.Packet.toCapturedPacket(): CapturedPacket? {
        val ip = get(IpV4Packet::class.java) ?: return null
        val tcp = get(TcpPacket::class.java) ?: return null
        val payload = tcp.payload?.rawData ?: ByteArray(0)
        return CapturedPacket(
            timestampMs = System.currentTimeMillis(),
            source = InetSocketAddress(ip.header.srcAddr, tcp.header.srcPort.valueAsInt()),
            destination = InetSocketAddress(ip.header.dstAddr, tcp.header.dstPort.valueAsInt()),
            payload = payload,
        )
    }

    companion object {
        const val SNAP_LEN = 65_536
        const val READ_TIMEOUT_MS = 50
    }
}

/** Fabrique injectable (facilite les tests et l'orchestration). */
class PacketCaptureFactory {
    fun create(nif: PcapNetworkInterface, endpoints: List<ServerEndpoint>): PacketCapture =
        PacketCapture(nif, endpoints)
}
