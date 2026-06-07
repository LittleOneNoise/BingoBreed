package fr.lilone.bingobreed.sniffer.parser

import fr.lilone.bingobreed.sniffer.model.DofusFrame

/** Sélection d'un serveur de jeu détectée sur le flux serveur de connexion. */
data class GameServerSelection(
    val host: String,
    val port: Int,
)

/**
 * Interprète les frames du serveur de connexion (déjà réassemblées) pour en
 * extraire le serveur de jeu choisi par le joueur.
 *
 * Implémentation cible une fois les `.proto` ajoutés dans le module `:proto` :
 *
 * ```kotlin
 * class LoginConnectionMessageInterpreter : ConnectionMessageInterpreter {
 *     override fun interpret(frame: DofusFrame): GameServerSelection? {
 *         // On ne lit que le sens serveur -> client pour la sélection de serveur
 *         if (frame.direction != Direction.SERVER_TO_CLIENT) return null
 *         val msg = LoginMessage.parseFrom(frame.protobuf)   // classe générée (:proto)
 *         // TODO: selon le .proto, repérer le message de sélection de serveur
 *         //       (probablement via le type_url d'un google.protobuf.Any)
 *         //       et en extraire host + port.
 *         return null
 *     }
 * }
 * ```
 *
 * Stub courant : [NoOpConnectionMessageInterpreter].
 */
fun interface ConnectionMessageInterpreter {
    /** Renvoie une sélection si cette frame la révèle, sinon null. */
    fun interpret(frame: DofusFrame): GameServerSelection?
}

/** Implémentation neutre tant que le parsing protobuf n'est pas branché. */
class NoOpConnectionMessageInterpreter : ConnectionMessageInterpreter {
    override fun interpret(frame: DofusFrame): GameServerSelection? = null
}
