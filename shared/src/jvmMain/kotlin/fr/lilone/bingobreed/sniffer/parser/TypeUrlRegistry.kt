package fr.lilone.bingobreed.sniffer.parser

import com.google.protobuf.Message
import java.util.concurrent.ConcurrentHashMap

/**
 * Registre explicite des `type_url` du protocole de jeu (`type.ankama.com/<code>`).
 *
 * À remplir au fur et à mesure que tu ajoutes les `.proto` métier dans le module
 * `:proto`. Tant qu'un `type_url` n'a pas de `parser`, on connaît au moins son nom
 * (si enregistré) et on garde le payload brut.
 *
 * Exemple :
 * ```kotlin
 * val registry = TypeUrlRegistry()
 *     .registerCode("ial", "AveragePrices") { bytes -> AveragePrices.parseFrom(bytes) }
 *     .registerCode("jtg", "ConnectionReady") // nom seul, parser à venir
 * ```
 */
class TypeUrlRegistry {

    data class Entry(
        val name: String,
        /** Parse le `value` du Any en message concret, si le `.proto` est dispo. */
        val parser: ((ByteArray) -> Message)? = null,
    )

    private val byUrl = ConcurrentHashMap<String, Entry>()

    /** Enregistre par `type_url` complet (`type.ankama.com/xxx`). */
    fun register(typeUrl: String, name: String, parser: ((ByteArray) -> Message)? = null): TypeUrlRegistry {
        byUrl[typeUrl] = Entry(name, parser)
        return this
    }

    /** Enregistre par code court (`xxx` → `type.ankama.com/xxx`). */
    fun registerCode(code: String, name: String, parser: ((ByteArray) -> Message)? = null): TypeUrlRegistry =
        register("$ANKAMA_PREFIX$code", name, parser)

    fun lookup(typeUrl: String): Entry? = byUrl[typeUrl]

    companion object {
        const val ANKAMA_PREFIX = "type.ankama.com/"
    }
}
