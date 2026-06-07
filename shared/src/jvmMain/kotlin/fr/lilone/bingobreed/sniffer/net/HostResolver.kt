package fr.lilone.bingobreed.sniffer.net

import fr.lilone.bingobreed.sniffer.model.ServerEndpoint
import fr.lilone.bingobreed.sniffer.util.logger
import java.net.InetAddress

/**
 * Résout un host (load balancer Ankama) vers l'ensemble de ses IPs (enregistrements A).
 * Utilisé pour les serveurs de connexion ET les serveurs de jeu.
 */
class HostResolver {
    private val log = logger()

    /** Toutes les IPs derrière [host]. */
    fun resolve(host: String): Set<InetAddress> =
        InetAddress.getAllByName(host).toSet()
            .also { log.debug("Résolution {} -> {}", host, it.map(InetAddress::getHostAddress)) }

    /** Construit la liste d'endpoints (IP, port) à surveiller pour [host]:[port]. */
    fun resolveEndpoints(host: String, port: Int): List<ServerEndpoint> =
        resolve(host).map { ServerEndpoint(it, port) }
}
