package fr.lilone.bingobreed.sniffer.model

/**
 * Un message de jeu extrait de l'enveloppe `GameMessage` : le contenu est un
 * `google.protobuf.Any` (`type_url` = `type.ankama.com/<code>`, `value` = payload).
 *
 * On garde le `value` brut : il sera parsé en message concret via le
 * [fr.lilone.bingobreed.sniffer.parser.TypeUrlRegistry] au fur et à mesure que
 * tu ajoutes les `.proto` métier.
 */
class DecodedGameAny(
    val direction: Direction,
    /** N° de champ top-level de l'enveloppe (1/2/3 selon Request/Response/Event). */
    val envelopeField: Int,
    /** Identifiant de requête si présent (côté client → serveur). */
    val requestId: Long?,
    /** Ex. `type.ankama.com/jtg`. */
    val typeUrl: String,
    /** Payload brut du `Any`. */
    val value: ByteArray,
    /** Nom lisible si le `type_url` est enregistré dans le registre, sinon null. */
    val knownName: String?,
) {
    /** Code court, ex. `jtg`. */
    val code: String get() = typeUrl.substringAfterLast('/')

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedGameAny) return false
        return direction == other.direction &&
            envelopeField == other.envelopeField &&
            requestId == other.requestId &&
            typeUrl == other.typeUrl &&
            value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = direction.hashCode()
        result = 31 * result + envelopeField
        result = 31 * result + (requestId?.hashCode() ?: 0)
        result = 31 * result + typeUrl.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }

    override fun toString(): String =
        "DecodedGameAny($direction, field=$envelopeField, id=$requestId, $typeUrl, ${value.size}b${knownName?.let { ", $it" } ?: ""})"
}
