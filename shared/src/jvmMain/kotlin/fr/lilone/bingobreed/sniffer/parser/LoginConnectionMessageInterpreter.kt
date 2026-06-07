package fr.lilone.bingobreed.sniffer.parser

import com.chatofus.proto.login_message.LoginMessage
import fr.lilone.bingobreed.sniffer.model.ConnectionHost
import fr.lilone.bingobreed.sniffer.model.Direction
import fr.lilone.bingobreed.sniffer.model.DofusFrame
import fr.lilone.bingobreed.sniffer.util.logger

/**
 * Interpréteur réel du flux serveur de connexion.
 *
 * Le serveur de jeu choisi arrive dans un message
 * `LoginMessage.response.selectServer.success` (sens serveur → client) :
 *   - `host`  = adresse du serveur de jeu,
 *   - `ports` = ports proposés (on privilégie 5555, non chiffré).
 */
class LoginConnectionMessageInterpreter : ConnectionMessageInterpreter {
    private val log = logger()

    override fun interpret(frame: DofusFrame): GameServerSelection? {
        // La sélection de serveur est envoyée par le serveur de connexion.
        if (frame.direction != Direction.SERVER_TO_CLIENT) return null

        val msg = try {
            LoginMessage.parseFrom(frame.protobuf)
        } catch (e: Exception) {
            log.debug("Frame non décodable en LoginMessage ({} bytes) — ignorée", frame.size)
            return null
        }

        if (!msg.hasResponse()) return null
        val response = msg.response
        if (!response.hasSelectServer()) return null
        val selectServer = response.selectServer
        if (!selectServer.hasSuccess()) return null

        val success = selectServer.success
        val host = success.host
        val ports = success.portsList
        if (host.isNullOrBlank() || ports.isEmpty()) {
            log.warn("SelectServerResponse.Success sans host/ports exploitables: host='{}' ports={}", host, ports)
            return null
        }

        val port = if (ConnectionHost.UNENCRYPTED_PORT in ports) ConnectionHost.UNENCRYPTED_PORT else ports.first()
        log.info("Sélection serveur de jeu décodée: {}:{} (ports proposés: {})", host, port, ports)
        return GameServerSelection(host, port)
    }
}
