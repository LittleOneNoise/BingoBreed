package fr.lilone.bingobreed.sniffer.diagnostics

import com.google.protobuf.ByteString
import com.google.protobuf.UnknownFieldSet

/**
 * Décode des octets protobuf **sans connaître le schéma** (équivalent de
 * `protoc --decode_raw`), en s'appuyant sur le format wire auto-descriptif.
 *
 * Pour chaque champ : numéro + wire-type + valeur. Les champs length-delimited
 * sont interprétés récursivement comme message imbriqué, sinon en string, sinon
 * en hex. Sert à vérifier à l'œil la cohérence d'un `.proto` avec les octets reçus.
 */
object RawProtobufDumper {

    fun dump(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "(vide)"
        return try {
            val set = UnknownFieldSet.parseFrom(bytes)
            buildString { render(set, this, 0) }.ifBlank { "(aucun champ)" }
        } catch (e: Exception) {
            "⚠️ non décodable en protobuf (${e.message}) — hex: ${hex(bytes, 48)}"
        }
    }

    private fun render(set: UnknownFieldSet, sb: StringBuilder, indent: Int) {
        val pad = "  ".repeat(indent)
        for ((number, field) in set.asMap()) {
            field.varintList.forEach { sb.appendLine("$pad#$number varint: $it") }
            field.fixed32List.forEach { sb.appendLine("$pad#$number fixed32: $it") }
            field.fixed64List.forEach { sb.appendLine("$pad#$number fixed64: $it") }
            field.lengthDelimitedList.forEach { renderLengthDelimited(number, it, sb, indent) }
            field.groupList.forEach {
                sb.appendLine("$pad#$number group {")
                render(it, sb, indent + 1)
                sb.appendLine("$pad}")
            }
        }
    }

    private fun renderLengthDelimited(number: Int, bs: ByteString, sb: StringBuilder, indent: Int) {
        val pad = "  ".repeat(indent)
        val bytes = bs.toByteArray()

        val nested = tryParseMessage(bytes)
        if (nested != null) {
            sb.appendLine("$pad#$number message (${bytes.size}b) {")
            render(nested, sb, indent + 1)
            sb.appendLine("$pad}")
            return
        }
        val str = asPrintableString(bytes)
        if (str != null) {
            sb.appendLine("$pad#$number string: \"$str\"")
        } else {
            sb.appendLine("$pad#$number bytes(${bytes.size}): ${hex(bytes, 32)}")
        }
    }

    /** Heuristique : parse comme message et n'accepte que si la re-sérialisation est de même taille. */
    private fun tryParseMessage(bytes: ByteArray): UnknownFieldSet? {
        if (bytes.isEmpty()) return null
        return try {
            val set = UnknownFieldSet.parseFrom(bytes)
            if (set.asMap().isNotEmpty() && set.toByteArray().size == bytes.size) set else null
        } catch (e: Exception) {
            null
        }
    }

    /** Renvoie la chaîne si les octets sont du texte imprimable (UTF-8 fidèle, sans caractères de contrôle). */
    private fun asPrintableString(bytes: ByteArray): String? {
        val s = String(bytes, Charsets.UTF_8)
        if (s.any { it.code < 0x20 && it != '\t' && it != '\n' && it != '\r' }) return null
        if (!s.toByteArray(Charsets.UTF_8).contentEquals(bytes)) return null
        return s
    }

    private fun hex(bytes: ByteArray, max: Int): String {
        val shown = bytes.take(max).joinToString(" ") { "%02x".format(it) }
        return if (bytes.size > max) "$shown …(${bytes.size}b)" else shown
    }
}
