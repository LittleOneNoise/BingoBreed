package fr.lilone.bingobreed.sniffer.capture

import fr.lilone.bingobreed.sniffer.model.ServerEndpoint

/** Construction des expressions de filtre BPF (syntaxe libpcap/Npcap). */
object BpfFilters {

    /**
     * `tcp and (host A or host B or ...) and (port p1 or port p2 or ...)`
     * Restreint la capture aux IPs des serveurs visés sur leurs ports.
     */
    fun tcpHostsAndPorts(endpoints: List<ServerEndpoint>): String {
        require(endpoints.isNotEmpty()) { "endpoints vide" }
        val hosts = endpoints.map { it.ip }.distinct().joinToString(" or ") { "host $it" }
        val ports = endpoints.map { it.port }.distinct().joinToString(" or ") { "port $it" }
        return "tcp and ($hosts) and ($ports)"
    }
}
