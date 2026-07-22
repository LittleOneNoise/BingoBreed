package fr.lilone.bingobreed.sniffer.model.breeding

/**
 * Référence des robes de **Muldo** : ids réseau → nom de robe, et nom → génération.
 *
 * ⚠️ **Un seul espace d'ids** pour la robe propre ([Mount.appearanceId], champ `feam`) ET
 * les robes des parents ([Mount.parents], champ `feap`) : un id donné désigne la même robe
 * dans les deux cas (confirmé par capture systématique, ex. `115`=Roux et Doré, `120`=Ébène
 * et Amande, `98`=Turquoise dans les deux champs). La table [IDS] est donc commune.
 *
 * [GENERATIONS] (robe → génération) en découle. L'espace d'ids est **global aux familles**
 * (dragodindes 1..66, muldos 90..171, volkornes 172..296, muldos gen 9+ après 296 — cf. Azur=299,
 * confirmé par `api.dofusdb.fr/mounts`) mais les noms courts se répètent entre familles :
 * [IDS] reste donc réservé au Muldo, les Volkornes vivent dans [VOLKORNE_IDS].
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
        103 to "Ébène et Pourpre",
        104 to "Orchidée et Pourpre",
        105 to "Doré et Orchidée",
        106 to "Indigo et Orchidée",
        107 to "Ébène et Orchidée",
        108 to "Doré et Indigo",
        109 to "Ébène et Indigo",
        110 to "Doré et Ébène",
        111 to "Roux et Pourpre",
        112 to "Roux et Orchidée",
        113 to "Roux et Indigo",
        114 to "Roux et Ébène",
        115 to "Roux et Doré",
        116 to "Roux et Amande",
        117 to "Pourpre et Amande",
        118 to "Orchidée et Amande",
        119 to "Indigo et Amande",
        120 to "Ébène et Amande",
        121 to "Doré et Amande",
        122 to "Pourpre et Ivoire",
        123 to "Orchidée et Ivoire",
        124 to "Indigo et Ivoire",
        125 to "Ébène et Ivoire",
        126 to "Doré et Ivoire",
        127 to "Roux et Ivoire",
        138 to "Amande et Ivoire",
        139 to "Turquoise et Ivoire",
        140 to "Turquoise et Pourpre",
        141 to "Turquoise et Indigo",
        142 to "Turquoise et Ébène",
        143 to "Turquoise et Roux",
        144 to "Turquoise et Amande",
        145 to "Turquoise et Doré",
        146 to "Prune et Pourpre",
        147 to "Prune et Orchidée",
        148 to "Prune et Indigo",
        149 to "Prune et Ébène",
        150 to "Prune et Doré",
        151 to "Prune et Roux",
        152 to "Prune et Amande",
        153 to "Prune et Ivoire",
        154 to "Prune et Turquoise",
        155 to "Prune et Émeraude",
        156 to "Pourpre et Émeraude",
        157 to "Orchidée et Émeraude",
        158 to "Indigo et Émeraude",
        159 to "Ébène et Émeraude",
        160 to "Doré et Émeraude",
        161 to "Roux et Émeraude",
        162 to "Amande et Émeraude",
        163 to "Ivoire et Émeraude",
        164 to "Turquoise et Émeraude",
        165 to "Turquoise et Orchidée",
        299 to "Azur",
    )

    /**
     * Variantes **sauvages** du Muldo (avant apprivoisement) : hors élevage, donc hors [IDS] —
     * elles n'ont ni génération ni recette, et pollueraient l'index inverse robe→id de [MuldoRobes].
     * Une monture capturée repasse sur l'id de sa robe de base (Ébène sauvage 170 → 91, vérifié en
     * jeu). Conservées pour que [robeName] identifie l'id au lieu d'afficher « #170 ».
     */
    val WILD_IDS: Map<Int, String> = mapOf(
        167 to "Doré Sauvage",
        168 to "Pourpre Sauvage",
        169 to "Indigo Sauvage",
        170 to "Ébène Sauvage",
        171 to "Orchidée Sauvage",
    )

    /**
     * id → robe **Volkorne** (même espace d'ids global, plage 172..296 ; 232 absent aussi côté
     * DofusDB). Séparé de [IDS] : les noms courts se répètent entre familles (« Indigo » muldo=92,
     * volkorne=176) et [MuldoRobes] inverse [IDS] par nom. Source : `api.dofusdb.fr/mounts`.
     */
    val VOLKORNE_IDS: Map<Int, String> = mapOf(
        172 to "Pourpre Sauvage",
        173 to "Orchidée Sauvage",
        174 to "Indigo Sauvage",
        175 to "Ébène Sauvage",
        176 to "Indigo",
        177 to "Ébène",
        178 to "Pourpre",
        179 to "Orchidée",
        180 to "Roux",
        181 to "Amande",
        182 to "Ivoire",
        183 to "Turquoise",
        184 to "Prune",
        185 to "Émeraude",
        186 to "Doré",
        187 to "Jade",
        188 to "Rubis",
        189 to "Saphir",
        190 to "Améthyste",
        191 to "Pourpre et Orchidée",
        192 to "Pourpre et Indigo",
        193 to "Pourpre et Ébène",
        194 to "Orchidée et Indigo",
        195 to "Orchidée et Ébène",
        196 to "Indigo et Ébène",
        197 to "Amande et Pourpre",
        198 to "Amande et Orchidée",
        199 to "Amande et Indigo",
        200 to "Amande et Ébène",
        201 to "Amande et Roux",
        202 to "Amande et Ivoire",
        203 to "Amande et Turquoise",
        204 to "Roux et Pourpre",
        205 to "Roux et Orchidée",
        206 to "Roux et Indigo",
        207 to "Roux et Ébène",
        208 to "Roux et Ivoire",
        209 to "Roux et Turquoise",
        210 to "Ivoire et Pourpre",
        211 to "Ivoire et Orchidée",
        212 to "Ivoire et Indigo",
        213 to "Ivoire et Ébène",
        214 to "Ivoire et Turquoise",
        215 to "Turquoise et Pourpre",
        216 to "Turquoise et Orchidée",
        217 to "Turquoise et Indigo",
        218 to "Turquoise et Ébène",
        219 to "Prune et Pourpre",
        220 to "Prune et Orchidée",
        221 to "Prune et Indigo",
        222 to "Prune et Ébène",
        223 to "Prune et Amande",
        224 to "Prune et Turquoise",
        225 to "Prune et Émeraude",
        226 to "Émeraude et Pourpre",
        227 to "Émeraude et Orchidée",
        228 to "Émeraude et Indigo",
        229 to "Émeraude et Ébène",
        230 to "Émeraude et Amande",
        231 to "Émeraude et Roux",
        233 to "Émeraude et Ivoire",
        234 to "Émeraude et Turquoise",
        235 to "Doré et Pourpre",
        236 to "Doré et Orchidée",
        237 to "Doré et Indigo",
        238 to "Doré et Ébène",
        239 to "Jade et Pourpre",
        240 to "Jade et Orchidée",
        241 to "Jade et Indigo",
        242 to "Jade et Ébène",
        243 to "Jade et Amande",
        244 to "Jade et Roux",
        245 to "Jade et Ivoire",
        246 to "Jade et Turquoise",
        247 to "Jade et Prune",
        248 to "Jade et Émeraude",
        249 to "Jade et Doré",
        250 to "Jade et Rubis",
        251 to "Jade et Saphir",
        252 to "Jade et Améthyste",
        253 to "Rubis et Pourpre",
        254 to "Rubis et Orchidée",
        255 to "Rubis et Indigo",
        256 to "Rubis et Ébène",
        257 to "Rubis et Amande",
        258 to "Rubis et Roux",
        259 to "Rubis et Ivoire",
        260 to "Rubis et Turquoise",
        261 to "Rubis et Prune",
        262 to "Rubis et Émeraude",
        263 to "Rubis et Doré",
        264 to "Saphir et Pourpre",
        265 to "Saphir et Orchidée",
        266 to "Saphir et Indigo",
        267 to "Saphir et Ébène",
        268 to "Saphir et Amande",
        269 to "Saphir et Roux",
        270 to "Saphir et Ivoire",
        271 to "Saphir et Turquoise",
        272 to "Saphir et Prune",
        273 to "Saphir et Émeraude",
        274 to "Saphir et Doré",
        275 to "Saphir et Améthyste",
        276 to "Améthyste et Pourpre",
        277 to "Améthyste et Orchidée",
        278 to "Améthyste et Indigo",
        279 to "Améthyste et Ébène",
        280 to "Améthyste et Amande",
        281 to "Améthyste et Roux",
        282 to "Améthyste et Ivoire",
        283 to "Améthyste et Turquoise",
        284 to "Améthyste et Prune",
        285 to "Améthyste et Émeraude",
        286 to "Améthyste et Doré",
        287 to "Prune et Roux",
        288 to "Prune et Ivoire",
        289 to "Rubis et Saphir",
        290 to "Rubis et Améthyste",
        291 to "Doré et Roux",
        292 to "Doré et Amande",
        293 to "Doré et Ivoire",
        294 to "Doré et Turquoise",
        295 to "Doré et Prune",
        296 to "Doré et Émeraude",
    )

    /** Nom court de la robe (sans génération), ou null si l'id est inconnu. */
    fun robeName(id: Int): String? = IDS[id] ?: WILD_IDS[id]

    /** Génération de la robe, ou null si robe/id inconnu (les sauvages n'en ont pas). */
    fun robeGeneration(id: Int): Int? = IDS[id]?.let { GENERATION_OF[it] }

    /** Libellé : « Roux et Doré (Gen 4) » si connu, sinon « #id ». */
    fun robeLabel(id: Int): String {
        val name = robeName(id) ?: return "#$id"
        return GENERATION_OF[name]?.let { "$name (Gen $it)" } ?: name
    }
}
