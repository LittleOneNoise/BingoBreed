package fr.lilone.bingobreed.sniffer.model

/**
 * Événements émis par le [fr.lilone.bingobreed.sniffer.SnifferEngine]
 * pour l'UI / les logs. Flux unique et observable de bout en bout.
 */
sealed interface SnifferEvent {

    /** Interface réseau auto-détectée au lancement. */
    data class InterfaceSelected(val name: String, val localIp: String) : SnifferEvent

    /** Contrôle d'écart entre la version du client local et la baseline de build de l'appli. */
    data class VersionChecked(val check: VersionCheck) : SnifferEvent

    /** Endpoints du/des serveur(s) de connexion résolus depuis la config Ankama. */
    data class ConnectionEndpointsResolved(val endpoints: List<ServerEndpoint>) : SnifferEvent

    /** Un serveur de jeu vient d'être sélectionné par le joueur (détecté sur le flux connexion). */
    data class GameServerDetected(
        val host: String,
        val endpoints: List<ServerEndpoint>,
    ) : SnifferEvent

    /** Une frame réassemblée sur le flux serveur de connexion. */
    data class ConnectionFrame(val frame: DofusFrame) : SnifferEvent

    /** Une frame réassemblée sur le flux d'un serveur de jeu donné. */
    data class GameFrame(val host: String, val frame: DofusFrame) : SnifferEvent

    /**
     * Un message de jeu décodé : [message] = Any extrait de l'enveloppe (toujours
     * présent), [dynamic] = décodage structuré via descripteur (null si code
     * inconnu du descripteur protodec).
     */
    data class GameMessage(
        val host: String,
        val message: DecodedGameAny,
        val dynamic: com.google.protobuf.Message?,
    ) : SnifferEvent

    /** Un état d'enclos (paddock) vient d'être décodé depuis un message de jeu. */
    data class PaddockUpdated(
        val host: String,
        val paddock: fr.lilone.bingobreed.sniffer.model.breeding.Paddock,
    ) : SnifferEvent

    /** Erreur non fatale d'un listener (l'engine continue grâce au SupervisorJob). */
    data class Failure(val context: String, val cause: Throwable) : SnifferEvent
}
