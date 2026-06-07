package fr.lilone.bingobreed.sniffer.model

import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Une entrée de `connectionHosts` de la config Ankama.
 *
 * Format brut attendu : `"JMBouftou:<host>:<port1,port2,...>"`
 * (en pratique souvent `5555,443` ; on privilégie 5555 = flux non chiffré).
 */
data class ConnectionHost(
    val name: String,
    val host: String,
    val ports: List<Int>,
) {
    /** Port non chiffré privilégié (5555), sinon le premier disponible. */
    val preferredPort: Int
        get() = if (UNENCRYPTED_PORT in ports) UNENCRYPTED_PORT else ports.first()

    companion object {
        const val UNENCRYPTED_PORT = 5555

        /** Parse une ligne `"name:host:p1,p2"`. */
        fun parse(raw: String): ConnectionHost {
            val firstColon = raw.indexOf(':')
            val lastColon = raw.lastIndexOf(':')
            require(firstColon in 0 until lastColon) { "Ligne connectionHost invalide: '$raw'" }

            val name = raw.substring(0, firstColon)
            val host = raw.substring(firstColon + 1, lastColon)
            val ports = raw.substring(lastColon + 1)
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }

            require(host.isNotBlank() && ports.isNotEmpty()) { "Ligne connectionHost invalide: '$raw'" }
            return ConnectionHost(name = name, host = host, ports = ports)
        }
    }
}

/** Un couple (IP, port) que l'on va surveiller. */
data class ServerEndpoint(
    val address: InetAddress,
    val port: Int,
) {
    val ip: String get() = address.hostAddress
}

/** Un paquet TCP capturé, déjà décodé en couches IP/TCP. */
data class CapturedPacket(
    val timestampMs: Long,
    val source: InetSocketAddress,
    val destination: InetSocketAddress,
    /** Payload applicatif (peut être vide : handshake, ACK...). */
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CapturedPacket) return false
        return timestampMs == other.timestampMs &&
            source == other.source &&
            destination == other.destination &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = timestampMs.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + destination.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
