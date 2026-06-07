package fr.lilone.bingobreed.sniffer.model

import java.net.InetSocketAddress

/** Sens d'un flux par rapport au serveur Dofus. */
enum class Direction { CLIENT_TO_SERVER, SERVER_TO_CLIENT }

/**
 * Identifie un flux TCP **unidirectionnel** (un sens d'une connexion).
 * Les deux sens d'une même connexion ont des clés distinctes (src/dst inversés),
 * donc chacun a son propre buffer de réassemblage — contrairement à l'ancien
 * buffer global keyé par "CONNECTION".
 */
data class StreamKey(
    val source: InetSocketAddress,
    val destination: InetSocketAddress,
)

/**
 * Une frame applicative complète extraite du flux TCP : les octets bruts d'UN
 * message protobuf (sans le préfixe varint de taille), prêts pour `.parseFrom`.
 */
class DofusFrame(
    val stream: StreamKey,
    val direction: Direction,
    val protobuf: ByteArray,
) {
    val size: Int get() = protobuf.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DofusFrame) return false
        return stream == other.stream &&
            direction == other.direction &&
            protobuf.contentEquals(other.protobuf)
    }

    override fun hashCode(): Int {
        var result = stream.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + protobuf.contentHashCode()
        return result
    }

    override fun toString(): String =
        "DofusFrame($direction, ${protobuf.size} bytes, $stream)"
}
