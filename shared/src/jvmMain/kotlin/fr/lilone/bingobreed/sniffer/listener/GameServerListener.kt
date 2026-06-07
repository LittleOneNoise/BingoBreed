package fr.lilone.bingobreed.sniffer.listener

import fr.lilone.bingobreed.sniffer.capture.PacketCapture
import fr.lilone.bingobreed.sniffer.capture.TcpStreamReassembler
import fr.lilone.bingobreed.sniffer.model.DofusFrame
import fr.lilone.bingobreed.sniffer.util.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

/**
 * Écoute le flux d'UN serveur de jeu : capture → réassemblage → frames.
 * Plusieurs instances tournent en parallèle (un par serveur de jeu), chacune
 * dans sa coroutine pilotée par le [fr.lilone.bingobreed.sniffer.SnifferEngine].
 *
 * TODO(protobuf) : `GameMessage.parseFrom(frame.protobuf)` pour le décodage métier.
 */
class GameServerListener(
    private val capture: PacketCapture,
    private val reassembler: TcpStreamReassembler,
) {
    private val log = logger()

    fun listen(): Flow<DofusFrame> = reassembler
        .reassemble(capture.packets())
        .onStart { log.info("Écoute serveur de jeu démarrée") }
}
