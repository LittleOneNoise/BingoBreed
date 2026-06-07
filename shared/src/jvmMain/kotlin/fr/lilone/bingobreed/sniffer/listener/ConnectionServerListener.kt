package fr.lilone.bingobreed.sniffer.listener

import fr.lilone.bingobreed.sniffer.capture.PacketCapture
import fr.lilone.bingobreed.sniffer.capture.TcpStreamReassembler
import fr.lilone.bingobreed.sniffer.model.DofusFrame
import fr.lilone.bingobreed.sniffer.parser.ConnectionMessageInterpreter
import fr.lilone.bingobreed.sniffer.parser.GameServerSelection
import fr.lilone.bingobreed.sniffer.util.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Ce qu'observe l'écoute du serveur de connexion. */
sealed interface ConnectionEvent {
    data class Frame(val frame: DofusFrame) : ConnectionEvent
    data class GameServer(val selection: GameServerSelection) : ConnectionEvent
}

/**
 * Écoute (long-lived) le flux du serveur de connexion : capture → réassemblage →
 * interprétation. Émet chaque frame et, dès qu'un serveur de jeu est sélectionné,
 * un [ConnectionEvent.GameServer].
 *
 * Ne lance PAS l'écoute du serveur de jeu : c'est le
 * [fr.lilone.bingobreed.sniffer.SnifferEngine] qui réagit, pour que cette écoute
 * continue à tourner.
 */
class ConnectionServerListener(
    private val capture: PacketCapture,
    private val reassembler: TcpStreamReassembler,
    private val interpreter: ConnectionMessageInterpreter,
) {
    private val log = logger()

    fun listen(): Flow<ConnectionEvent> = flow {
        log.info("Écoute serveur de connexion démarrée")
        reassembler.reassemble(capture.packets()).collect { frame ->
            emit(ConnectionEvent.Frame(frame))
            interpreter.interpret(frame)?.let {
                log.info("Serveur de jeu détecté: {}:{}", it.host, it.port)
                emit(ConnectionEvent.GameServer(it))
            }
        }
    }
}
