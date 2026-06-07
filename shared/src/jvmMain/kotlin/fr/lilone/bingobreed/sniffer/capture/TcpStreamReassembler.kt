package fr.lilone.bingobreed.sniffer.capture

import fr.lilone.bingobreed.sniffer.model.CapturedPacket
import fr.lilone.bingobreed.sniffer.model.Direction
import fr.lilone.bingobreed.sniffer.model.DofusFrame
import fr.lilone.bingobreed.sniffer.model.ServerEndpoint
import fr.lilone.bingobreed.sniffer.model.StreamKey
import fr.lilone.bingobreed.sniffer.parser.ProtobufFrameDecoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.InetSocketAddress

/**
 * Réassemble des [CapturedPacket] en [DofusFrame].
 *
 * Tient **un [ProtobufFrameDecoder] par [StreamKey]** (flux unidirectionnel), ce
 * qui évite le mélange des connexions/directions de l'ancien buffer global.
 *
 * Sûr sans synchronisation : la map n'est touchée que depuis la coroutine qui
 * collecte le [Flow] (collecte séquentielle). Une instance par listener.
 */
class TcpStreamReassembler(serverEndpoints: Collection<ServerEndpoint>) {

    private val serverSockets: Set<Pair<String, Int>> =
        serverEndpoints.map { it.ip to it.port }.toSet()

    private val decoders = HashMap<StreamKey, ProtobufFrameDecoder>()

    fun reassemble(packets: Flow<CapturedPacket>): Flow<DofusFrame> = flow {
        packets.collect { packet ->
            if (packet.payload.isEmpty()) return@collect

            val key = StreamKey(packet.source, packet.destination)
            val decoder = decoders.getOrPut(key) { ProtobufFrameDecoder() }
            val direction = directionOf(packet.source)

            for (protobuf in decoder.decode(packet.payload)) {
                emit(DofusFrame(key, direction, protobuf))
            }
        }
    }

    private fun directionOf(source: InetSocketAddress): Direction =
        if ((source.address.hostAddress to source.port) in serverSockets) {
            Direction.SERVER_TO_CLIENT
        } else {
            Direction.CLIENT_TO_SERVER
        }
}
