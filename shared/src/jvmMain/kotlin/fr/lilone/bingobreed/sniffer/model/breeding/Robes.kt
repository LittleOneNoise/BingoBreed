package fr.lilone.bingobreed.sniffer.model.breeding

/**
 * Référence des robes de **Muldo** : ids réseau → nom de robe, et nom → génération.
 *
 * ⚠️ **Un seul espace d'ids** pour la robe propre ([Mount.appearanceId], champ `feam`) ET
 * les robes des parents ([Mount.parents], champ `feap`) : un id donné désigne la même robe
 * dans les deux cas (confirmé par capture systématique, ex. `115`=Roux et Doré, `120`=Ébène
 * et Amande, `98`=Turquoise dans les deux champs). La table [IDS] est donc commune.
 *
 * [GENERATIONS] (robe → génération) en découle. Table **partielle**, à compléter au fil des
 * captures (relève l'id « (xx) » affiché dans l'UI + le nom de robe en jeu). Espace
 * **spécifique au Muldo**.
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
     * id → robe (espace **commun** robe propre `feam` et robes parentales `feap`). Relevé sur
     * montures nommées d'après leur robe et sur les généalogies affichées. À compléter.
     */
    val IDS: Map<Int, String> = mapOf(
        90 to "Orchidée",
        91 to "Ébène",
        92 to "Indigo",
        93 to "Pourpre",
        94 to "Doré",
        95 to "Roux",
        96 to "Amande",
        97 to "Ivoire",
        98 to "Turquoise",
        99 to "Prune",
        100 to "Émeraude",
        101 to "Doré et Pourpre",
        102 to "Indigo et Pourpre",
        104 to "Orchidée et Pourpre",
        105 to "Doré et Orchidée",
        107 to "Ébène et Orchidée",
        108 to "Doré et Indigo",
        109 to "Ébène et Indigo",
        110 to "Doré et Ébène",
        114 to "Roux et Ébène",
        115 to "Roux et Doré",
        116 to "Roux et Amande",
        118 to "Orchidée et Amande",
        120 to "Ébène et Amande",
        121 to "Doré et Amande",
        122 to "Pourpre et Ivoire",
        125 to "Ébène et Ivoire",
        126 to "Doré et Ivoire",
        138 to "Amande et Ivoire",
        139 to "Turquoise et Ivoire",
        140 to "Turquoise et Pourpre",
        142 to "Turquoise et Ébène",
        143 to "Turquoise et Roux",
        144 to "Turquoise et Amande",
        145 to "Turquoise et Doré",
        150 to "Prune et Doré",
        156 to "Pourpre et Émeraude",
        157 to "Orchidée et Émeraude",
        158 to "Indigo et Émeraude",
        159 to "Ébène et Émeraude",
        160 to "Doré et Émeraude",
        165 to "Turquoise et Orchidée",
    )

    /** Nom court de la robe (sans génération), ou null si l'id est inconnu. */
    fun robeName(id: Int): String? = IDS[id]

    /** Génération de la robe, ou null si robe/id inconnu. */
    fun robeGeneration(id: Int): Int? = IDS[id]?.let { GENERATION_OF[it] }

    /** Libellé : « Roux et Doré (Gen 4) » si connu, sinon « #id ». */
    fun robeLabel(id: Int): String {
        val name = IDS[id] ?: return "#$id"
        return GENERATION_OF[name]?.let { "$name (Gen $it)" } ?: name
    }
}
