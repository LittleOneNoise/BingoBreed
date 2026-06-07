package fr.lilone.bingobreed.sniffer.net

import fr.lilone.bingobreed.sniffer.util.logger
import org.pcap4j.core.PcapNetworkInterface
import org.pcap4j.core.Pcaps
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Étape 1 — auto-détection de l'interface réseau en 2 temps :
 *
 *  1. on déduit l'IP locale "qui sort" en ouvrant un socket UDP vers 8.8.8.8:10002
 *     (aucun paquet n'est réellement envoyé : `connect` sur un DatagramSocket force
 *     juste l'OS à choisir l'interface/route de sortie, donc l'adresse source) ;
 *  2. on parcourt les interfaces pcap pour trouver celle qui porte cette IP.
 */
class NetworkInterfaceDetector(
    private val probeHost: String = PROBE_HOST,
    private val probePort: Int = PROBE_PORT,
) {
    private val log = logger()

    /** Temps 1 : IP locale de l'interface utilisée pour joindre Internet. */
    fun detectLocalAddress(): InetAddress =
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName(probeHost), probePort)
            socket.localAddress.also { log.debug("IP locale détectée: {} (via {}:{})", it.hostAddress, probeHost, probePort) }
        }

    /** Temps 2 : interface pcap qui porte [localAddress]. */
    fun findInterfaceFor(localAddress: InetAddress): PcapNetworkInterface {
        val devs = Pcaps.findAllDevs()
        log.debug("{} interface(s) pcap disponibles", devs.size)
        return devs.firstOrNull { dev ->
            dev.addresses.any { it.address == localAddress }
        }?.also { log.info("Interface sélectionnée: {} ({})", it.name, it.description ?: "sans description") }
            ?: error("Aucune interface pcap ne porte l'IP locale ${localAddress.hostAddress}. " +
                "Npcap est-il installé ?")
    }

    /** Enchaîne les 2 temps. */
    fun detect(): DetectedInterface {
        val localAddress = detectLocalAddress()
        val nif = findInterfaceFor(localAddress)
        return DetectedInterface(nif, localAddress)
    }

    companion object {
        const val PROBE_HOST = "8.8.8.8"
        const val PROBE_PORT = 10002
    }
}

data class DetectedInterface(
    val nif: PcapNetworkInterface,
    val localAddress: InetAddress,
)
