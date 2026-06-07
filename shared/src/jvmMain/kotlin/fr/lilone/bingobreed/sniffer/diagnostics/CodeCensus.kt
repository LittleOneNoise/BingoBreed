package fr.lilone.bingobreed.sniffer.diagnostics

/**
 * Recense les `type_url` rencontrés (comptage + détection de première apparition).
 *
 * Pratique pour le reverse : on lance le sniffer, on fait une action en jeu
 * (ouvrir l'enclos, nourrir une monture…) et les **nouveaux codes** qui surgissent
 * pointent vers les messages liés à cette action.
 */
class CodeCensus {
    private val counts = LinkedHashMap<String, Int>()

    /** Incrémente le compteur de [typeUrl] ; renvoie true à la première apparition. */
    fun record(typeUrl: String): Boolean {
        val previous = counts.getOrDefault(typeUrl, 0)
        counts[typeUrl] = previous + 1
        return previous == 0
    }

    val distinctCount: Int get() = counts.size

    /** Récapitulatif trié par fréquence décroissante. */
    fun summary(): String =
        if (counts.isEmpty()) "(aucun message)"
        else counts.entries.sortedByDescending { it.value }
            .joinToString("\n") { "${it.value.toString().padStart(6)}  ${it.key}" }
}
