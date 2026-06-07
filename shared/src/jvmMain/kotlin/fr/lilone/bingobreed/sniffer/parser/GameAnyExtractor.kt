package fr.lilone.bingobreed.sniffer.parser

import com.google.protobuf.UnknownFieldSet
import fr.lilone.bingobreed.sniffer.model.DecodedGameAny
import fr.lilone.bingobreed.sniffer.model.DofusFrame

/**
 * Extrait le `google.protobuf.Any` (`type_url` + `value`) de l'enveloppe d'un
 * message de jeu, **sans dépendre du `.proto`** : on navigue le wire format.
 *
 * Formes observées :
 *  - serveur → client : `#<k> { #<j> { #1 type_url, #2 value } }`
 *  - client → serveur : `#<k> { #1 id, #<j> { #1 type_url, #2 value } }`
 *
 * On repère le `Any` par sa signature : un sous-message dont le champ #1 est une
 * string commençant par `type.ankama.com/`.
 */
class GameAnyExtractor {

    fun extract(frame: DofusFrame, registry: TypeUrlRegistry): DecodedGameAny? {
        val top = parse(frame.protobuf) ?: return null

        // Top-level : un unique champ message = l'enveloppe (Request/Response/Event).
        val (envelopeField, envelope) = top.asMap().entries.firstNotNullOfOrNull { (number, field) ->
            field.lengthDelimitedList.firstOrNull()
                ?.let { parse(it.toByteArray()) }
                ?.let { number to it }
        } ?: return null

        var requestId: Long? = null
        var any: AnyParts? = null
        for ((_, field) in envelope.asMap()) {
            if (requestId == null) field.varintList.firstOrNull()?.let { requestId = it }
            for (bs in field.lengthDelimitedList) {
                parseAny(bs.toByteArray())?.let { any = it }
            }
        }

        val parts = any ?: return null
        val known = registry.lookup(parts.typeUrl)?.name
        return DecodedGameAny(
            direction = frame.direction,
            envelopeField = envelopeField,
            requestId = requestId,
            typeUrl = parts.typeUrl,
            value = parts.value,
            knownName = known,
        )
    }

    private data class AnyParts(val typeUrl: String, val value: ByteArray)

    /** Reconnaît un `Any` : sous-message avec #1 = string `type.ankama.com/...`. */
    private fun parseAny(bytes: ByteArray): AnyParts? {
        val set = parse(bytes) ?: return null
        val url = set.asMap()[ANY_TYPE_URL_FIELD]?.lengthDelimitedList?.firstOrNull()?.toStringUtf8()
            ?: return null
        if (!url.startsWith(TypeUrlRegistry.ANKAMA_PREFIX)) return null
        val value = set.asMap()[ANY_VALUE_FIELD]?.lengthDelimitedList?.firstOrNull()?.toByteArray()
            ?: ByteArray(0)
        return AnyParts(url, value)
    }

    private fun parse(bytes: ByteArray): UnknownFieldSet? =
        if (bytes.isEmpty()) null else runCatching { UnknownFieldSet.parseFrom(bytes) }.getOrNull()

    private companion object {
        const val ANY_TYPE_URL_FIELD = 1
        const val ANY_VALUE_FIELD = 2
    }
}
