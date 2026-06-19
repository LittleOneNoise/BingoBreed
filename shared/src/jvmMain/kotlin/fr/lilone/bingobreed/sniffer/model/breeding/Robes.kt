package fr.lilone.bingobreed.sniffer.model.breeding

/**
 * Référence des robes de **Muldo** : ids réseau → nom de robe, et nom → génération.
 *
 * ⚠️ **Deux espaces d'ids distincts et indépendants**, à ne pas confondre :
 *  - [OWN_IDS] : robe **propre** de la monture ([Mount.appearanceId], champ `feam`).
 *  - [PARENT_IDS] : robes des **parents** ([Mount.parents], champ `feap`).
 *
 * Un même id désigne des robes différentes selon le champ (ex. Turquoise = `98` en robe
 * propre mais `93` en tant que parent ; « Roux et Doré » = `115` en propre, `120` en parent).
 * Confirmé par deux captures (montures nommées d'après leur robe propre, puis d'après les
 * parents).
 *
 * [GENERATIONS] (robe → génération) est commune : une fois le nom de robe connu, la
 * génération en découle. Tables **partielles**, à compléter au fil des captures (relève
 * l'id « #xx » affiché dans l'UI + le nom de robe en jeu). Espace **spécifique au Muldo**.
 */
object Robes {

    /** Robes par génération (noms canoniques, accents inclus). Source : compendium en jeu. */
    val GENERATIONS: Map<Int, List<String>> = mapOf(
        1 to listOf("Ébène", "Indigo", "Pourpre", "Orchidée", "Doré"),
        2 to listOf(
            "Doré et Pourpre", "Indigo et Pourpre", "Ébène et Pourpre", "Orchidée et Pourpre",
            "Doré et Orchidée", "Indigo et Orchidée", "Ébène et Orchidée", "Doré et Ébène",
            "Doré et Indigo", "Ébène et Indigo",
        ),
        3 to listOf("Roux", "Amande"),
        4 to listOf(
            "Doré et Amande", "Ébène et Amande", "Indigo et Amande", "Orchidée et Amande",
            "Pourpre et Amande", "Roux et Amande", "Roux et Doré", "Roux et Ébène",
            "Roux et Indigo", "Roux et Orchidée", "Roux et Pourpre",
        ),
        5 to listOf("Ivoire", "Turquoise"),
        6 to listOf(
            "Pourpre et Ivoire", "Orchidée et Ivoire", "Indigo et Ivoire", "Ébène et Ivoire",
            "Doré et Ivoire", "Roux et Ivoire", "Amande et Ivoire", "Turquoise et Ivoire",
            "Turquoise et Pourpre", "Turquoise et Orchidée", "Turquoise et Indigo",
            "Turquoise et Ébène", "Turquoise et Roux", "Turquoise et Amande", "Turquoise et Doré",
        ),
        7 to listOf("Prune", "Émeraude"),
        8 to listOf(
            "Prune et Pourpre", "Prune et Orchidée", "Prune et Indigo", "Prune et Ébène",
            "Prune et Doré", "Prune et Roux", "Prune et Amande", "Prune et Ivoire",
            "Prune et Turquoise", "Prune et Émeraude", "Pourpre et Émeraude", "Orchidée et Émeraude",
            "Indigo et Émeraude", "Ébène et Émeraude", "Doré et Émeraude", "Roux et Émeraude",
            "Amande et Émeraude", "Ivoire et Émeraude", "Turquoise et Émeraude",
        ),
        9 to listOf("Ambre", "Corail", "Azur", "Aigue-marine"),
        10 to listOf(
            "Ambre et Doré", "Ambre et Ébène", "Ambre et Indigo", "Ambre et Pourpre",
            "Ambre et Orchidée", "Ambre et Amande", "Ambre et Roux", "Ambre et Ivoire",
            "Ambre et Turquoise", "Ambre et Émeraude", "Ambre et Prune", "Ambre et Corail",
            "Ambre et Azur", "Ambre et Aigue-marine", "Corail et Doré", "Corail et Ébène",
            "Corail et Indigo", "Corail et Pourpre", "Corail et Orchidée", "Corail et Amande",
            "Corail et Roux", "Corail et Ivoire", "Corail et Turquoise", "Corail et Émeraude",
            "Corail et Prune", "Corail et Azur", "Corail et Aigue-marine", "Azur et Doré",
            "Azur et Ébène", "Azur et Indigo", "Azur et Pourpre", "Azur et Orchidée",
            "Azur et Amande", "Azur et Roux", "Azur et Ivoire", "Azur et Turquoise",
            "Azur et Émeraude", "Azur et Prune", "Azur et Aigue-marine", "Aigue-marine et Doré",
            "Aigue-marine et Ébène", "Aigue-marine et Indigo", "Aigue-marine et Pourpre",
            "Aigue-marine et Orchidée", "Aigue-marine et Amande", "Aigue-marine et Roux",
            "Aigue-marine et Ivoire", "Aigue-marine et Turquoise", "Aigue-marine et Émeraude",
            "Aigue-marine et Prune",
        ),
    )

    /** Index inverse robe → génération, dérivé de [GENERATIONS]. */
    val GENERATION_OF: Map<String, Int> =
        GENERATIONS.flatMap { (gen, robes) -> robes.map { it to gen } }.toMap()

    /**
     * id → robe **propre** (`feam`). Relevé sur montures nommées d'après leur robe (capture
     * 2026-06). À compléter.
     */
    val OWN_IDS: Map<Int, String> = mapOf(
        94 to "Doré",
        95 to "Roux",
        96 to "Amande",
        98 to "Turquoise",
        114 to "Roux et Ébène",
        115 to "Roux et Doré",
        120 to "Ébène et Amande",
        140 to "Turquoise et Pourpre",
    )

    /**
     * id → robe **parentale** (`feap`). Relevé sur montures nommées d'après leurs parents.
     * **Espace différent de [OWN_IDS].** À compléter.
     */
    val PARENT_IDS: Map<Int, String> = mapOf(
        91 to "Amande",
        93 to "Turquoise",
        94 to "Roux",
        95 to "Doré",
        96 to "Ébène",
        98 to "Pourpre",
        115 to "Ébène et Amande",
        120 to "Roux et Doré",
    )

    /** Libellé de la robe propre : « Roux et Doré (Gen 4) » si connu, sinon « #id ». */
    fun ownLabel(id: Int): String = labelFrom(OWN_IDS, id)

    /** Libellé d'une robe parentale : « Amande (Gen 3) » si connu, sinon « #id ». */
    fun parentLabel(id: Int): String = labelFrom(PARENT_IDS, id)

    private fun labelFrom(table: Map<Int, String>, id: Int): String {
        val name = table[id] ?: return "#$id"
        return GENERATION_OF[name]?.let { "$name (Gen $it)" } ?: name
    }
}
