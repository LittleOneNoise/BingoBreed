package fr.lilone.bingobreed.sniffer.parser

/**
 * Découpe un flux TCP (un sens d'une connexion) en frames protobuf.
 *
 * Format : chaque message protobuf est précédé de sa taille encodée en **varint**.
 * On lit `[varint taille][taille octets de protobuf]` en boucle.
 *
 * À état : conserve son propre buffer et le reliquat incomplet entre deux appels
 * (= entre deux paquets TCP). **Non thread-safe** : une instance par flux, pilotée
 * séquentiellement par le [fr.lilone.bingobreed.sniffer.capture.TcpStreamReassembler].
 */
class ProtobufFrameDecoder {

    private var buffer = ByteArray(0)

    /** Ajoute [chunk] au buffer et renvoie toutes les frames complètes désormais disponibles. */
    fun decode(chunk: ByteArray): List<ByteArray> {
        if (chunk.isNotEmpty()) buffer += chunk
        if (buffer.isEmpty()) return emptyList()

        val frames = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < buffer.size) {
            val varInt = readVarInt(buffer, offset) ?: break // varint incomplet -> on attend la suite
            val payloadStart = offset + varInt.bytesRead
            val size = varInt.value

            if (size <= 0) break // taille 0/invalide : désynchro probable, on s'arrête (cf. note)
            if (payloadStart + size > buffer.size) break // frame fragmentée : on attend le prochain paquet

            frames += buffer.copyOfRange(payloadStart, payloadStart + size)
            offset = payloadStart + size
        }

        // On ne conserve que le reliquat non consommé.
        buffer = if (offset == 0) buffer else buffer.copyOfRange(offset, buffer.size)
        return frames
    }

    /** Vide complètement le buffer (ex. reset après désynchronisation). */
    fun reset() {
        buffer = ByteArray(0)
    }

    private data class VarInt(val value: Int, val bytesRead: Int)

    /** Lit un varint protobuf à partir de [start]. Renvoie null s'il est incomplet. */
    private fun readVarInt(buf: ByteArray, start: Int): VarInt? {
        var result = 0
        var shift = 0
        var pos = start
        while (pos < buf.size) {
            val b = buf[pos].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            pos++
            if (b and 0x80 == 0) return VarInt(result, pos - start)
            shift += 7
            if (shift >= 35) return VarInt(result, pos - start) // garde-fou (varint > int32)
        }
        return null // bit de continuation posé mais buffer épuisé
    }
}
