package fr.lilone.bingobreed.sniffer.model.breeding

/**
 * Catalogue **unifié** des robes de Muldo pour le planificateur de reproduction : pour chaque
 * robe, sa génération, son id réseau (si connu) et sa **recette** (les 2 robes parentes qui la
 * produisent, modèle élevage 3.0).
 *
 * Source de vérité **dérivée** des tables existantes — pas de duplication :
 *  - nom + génération viennent de [Robes.GENERATIONS] (compendium en jeu, fait autorité) ;
 *  - id réseau vient de [Robes.IDS] (relevé par capture, partiel d'où [MuldoRobe.networkId] null) ;
 *  - [RECIPE] (robe → 2 robes parentes) est la seule donnée propre à ce fichier, portée du
 *    fansite `muldo-calculator` (tt405907, `src/data/muldos.js`) puis **normalisée** vers les
 *    noms canoniques accentués de [Robes.GENERATIONS]. Table codée en dur (cf. [Robes]),
 *    vérifiée par [MuldoRobesTest] (intégrité du DAG : tout parent existe, acyclique, couverture
 *    complète des robes gen ≥ 2).
 *
 * Modèle 3.0 : croiser les 2 robes de la recette produit la robe cible avec une probabilité
 * fonction du niveau des parents + optimakina (cf. le planner) ; la généalogie n'entre pas dans
 * cette probabilité, elle est garantie par la construction de la lignée (le DAG **est** l'exigence
 * généalogique).
 */
object MuldoRobes {

    /** robe canonique → (robe parente 1, robe parente 2). Absente pour les robes gen 1 (capturables). */
    private val RECIPE: Map<String, Pair<String, String>> = mapOf(
        // GEN 2 (deux couleurs gen 1)
        "Doré et Pourpre" to ("Doré" to "Pourpre"),
        "Indigo et Pourpre" to ("Indigo" to "Pourpre"),
        "Ébène et Pourpre" to ("Ébène" to "Pourpre"),
        "Orchidée et Pourpre" to ("Orchidée" to "Pourpre"),
        "Doré et Orchidée" to ("Doré" to "Orchidée"),
        "Indigo et Orchidée" to ("Indigo" to "Orchidée"),
        "Ébène et Orchidée" to ("Ébène" to "Orchidée"),
        "Doré et Indigo" to ("Doré" to "Indigo"),
        "Ébène et Indigo" to ("Ébène" to "Indigo"),
        "Doré et Ébène" to ("Doré" to "Ébène"),
        // GEN 3 (nouvelles couleurs de base)
        "Roux" to ("Doré et Pourpre" to "Doré et Orchidée"),
        "Amande" to ("Indigo et Pourpre" to "Ébène et Orchidée"),
        // GEN 4
        "Roux et Pourpre" to ("Pourpre" to "Roux"),
        "Roux et Orchidée" to ("Orchidée" to "Roux"),
        "Roux et Indigo" to ("Roux" to "Indigo"),
        "Roux et Ébène" to ("Roux" to "Ébène"),
        "Roux et Doré" to ("Roux" to "Doré"),
        "Roux et Amande" to ("Roux" to "Amande"),
        "Pourpre et Amande" to ("Amande" to "Pourpre"),
        "Orchidée et Amande" to ("Orchidée" to "Amande"),
        "Indigo et Amande" to ("Amande" to "Indigo"),
        "Ébène et Amande" to ("Amande" to "Ébène"),
        "Doré et Amande" to ("Amande" to "Doré"),
        // GEN 5
        "Ivoire" to ("Ébène et Amande" to "Roux et Doré"),
        "Turquoise" to ("Roux et Ébène" to "Doré et Amande"),
        // GEN 6
        "Pourpre et Ivoire" to ("Pourpre" to "Ivoire"),
        "Orchidée et Ivoire" to ("Orchidée" to "Ivoire"),
        "Indigo et Ivoire" to ("Indigo" to "Ivoire"),
        "Ébène et Ivoire" to ("Ébène" to "Ivoire"),
        "Doré et Ivoire" to ("Doré" to "Ivoire"),
        "Roux et Ivoire" to ("Roux" to "Ivoire"),
        "Amande et Ivoire" to ("Amande" to "Ivoire"),
        "Turquoise et Ivoire" to ("Turquoise" to "Ivoire"),
        "Turquoise et Pourpre" to ("Turquoise" to "Pourpre"),
        "Turquoise et Indigo" to ("Turquoise" to "Indigo"),
        "Turquoise et Ébène" to ("Turquoise" to "Ébène"),
        "Turquoise et Roux" to ("Turquoise" to "Roux"),
        "Turquoise et Amande" to ("Turquoise" to "Amande"),
        "Turquoise et Doré" to ("Turquoise" to "Doré"),
        "Turquoise et Orchidée" to ("Turquoise" to "Orchidée"),
        // GEN 7
        "Émeraude" to ("Turquoise et Ivoire" to "Turquoise et Doré"),
        "Prune" to ("Ébène et Ivoire" to "Turquoise et Pourpre"),
        // GEN 8
        "Prune et Pourpre" to ("Prune" to "Pourpre"),
        "Prune et Orchidée" to ("Prune" to "Orchidée"),
        "Prune et Indigo" to ("Prune" to "Indigo"),
        "Prune et Ébène" to ("Prune" to "Ébène"),
        "Prune et Doré" to ("Prune" to "Doré"),
        "Prune et Roux" to ("Prune" to "Roux"),
        "Prune et Amande" to ("Prune" to "Amande"),
        "Prune et Ivoire" to ("Prune" to "Ivoire"),
        "Prune et Turquoise" to ("Prune" to "Turquoise"),
        "Prune et Émeraude" to ("Prune" to "Émeraude"),
        "Pourpre et Émeraude" to ("Pourpre" to "Émeraude"),
        "Orchidée et Émeraude" to ("Orchidée" to "Émeraude"),
        "Indigo et Émeraude" to ("Indigo" to "Émeraude"),
        "Ébène et Émeraude" to ("Ébène" to "Émeraude"),
        "Doré et Émeraude" to ("Doré" to "Émeraude"),
        "Roux et Émeraude" to ("Roux" to "Émeraude"),
        "Amande et Émeraude" to ("Amande" to "Émeraude"),
        "Ivoire et Émeraude" to ("Ivoire" to "Émeraude"),
        "Turquoise et Émeraude" to ("Turquoise" to "Émeraude"),
        // GEN 9 (nouvelles couleurs de base)
        "Ambre" to ("Pourpre et Émeraude" to "Roux et Émeraude"),
        "Corail" to ("Prune et Pourpre" to "Prune et Roux"),
        "Azur" to ("Prune et Roux" to "Pourpre et Émeraude"),
        "Aigue-marine" to ("Prune et Pourpre" to "Roux et Émeraude"),
        // GEN 10
        "Ambre et Doré" to ("Ambre" to "Doré"),
        "Ambre et Ébène" to ("Ambre" to "Ébène"),
        "Ambre et Indigo" to ("Ambre" to "Indigo"),
        "Ambre et Pourpre" to ("Pourpre" to "Ambre"),
        "Ambre et Orchidée" to ("Orchidée" to "Ambre"),
        "Ambre et Amande" to ("Ambre" to "Amande"),
        "Ambre et Roux" to ("Roux" to "Ambre"),
        "Ambre et Ivoire" to ("Ivoire" to "Ambre"),
        "Ambre et Turquoise" to ("Turquoise" to "Ambre"),
        "Ambre et Émeraude" to ("Émeraude" to "Ambre"),
        "Ambre et Prune" to ("Prune" to "Ambre"),
        "Ambre et Corail" to ("Corail" to "Ambre"),
        "Ambre et Azur" to ("Azur" to "Ambre"),
        "Ambre et Aigue-marine" to ("Aigue-marine" to "Ambre"),
        "Corail et Doré" to ("Doré" to "Corail"),
        "Corail et Ébène" to ("Ébène" to "Corail"),
        "Corail et Indigo" to ("Corail" to "Indigo"),
        "Corail et Pourpre" to ("Pourpre" to "Corail"),
        "Corail et Orchidée" to ("Orchidée" to "Corail"),
        "Corail et Amande" to ("Amande" to "Corail"),
        // Recette absente de muldos.js, complétée selon le motif gen 10 (couleur × robe gen 9).
        "Corail et Roux" to ("Roux" to "Corail"),
        "Corail et Ivoire" to ("Ivoire" to "Corail"),
        "Corail et Turquoise" to ("Turquoise" to "Corail"),
        "Corail et Émeraude" to ("Émeraude" to "Corail"),
        "Corail et Prune" to ("Prune" to "Corail"),
        "Corail et Azur" to ("Azur" to "Corail"),
        "Corail et Aigue-marine" to ("Aigue-marine" to "Corail"),
        "Azur et Doré" to ("Doré" to "Azur"),
        "Azur et Pourpre" to ("Pourpre" to "Azur"),
        "Azur et Orchidée" to ("Orchidée" to "Azur"),
        "Azur et Amande" to ("Amande" to "Azur"),
        "Azur et Roux" to ("Roux" to "Azur"),
        "Azur et Ivoire" to ("Ivoire" to "Azur"),
        "Azur et Turquoise" to ("Turquoise" to "Azur"),
        "Azur et Indigo" to ("Indigo" to "Azur"),
        "Azur et Ébène" to ("Ébène" to "Azur"),
        // Recette absente de muldos.js, complétée selon le motif gen 10.
        "Azur et Émeraude" to ("Émeraude" to "Azur"),
        "Azur et Prune" to ("Azur" to "Prune"),
        "Azur et Aigue-marine" to ("Aigue-marine" to "Azur"),
        "Aigue-marine et Doré" to ("Aigue-marine" to "Doré"),
        "Aigue-marine et Ébène" to ("Ébène" to "Aigue-marine"),
        "Aigue-marine et Indigo" to ("Aigue-marine" to "Indigo"),
        "Aigue-marine et Pourpre" to ("Pourpre" to "Aigue-marine"),
        "Aigue-marine et Orchidée" to ("Orchidée" to "Aigue-marine"),
        "Aigue-marine et Amande" to ("Aigue-marine" to "Amande"),
        "Aigue-marine et Roux" to ("Roux" to "Aigue-marine"),
        "Aigue-marine et Ivoire" to ("Aigue-marine" to "Ivoire"),
        "Aigue-marine et Turquoise" to ("Turquoise" to "Aigue-marine"),
        "Aigue-marine et Émeraude" to ("Émeraude" to "Aigue-marine"),
        "Aigue-marine et Prune" to ("Aigue-marine" to "Prune"),
    )

    /** Inverse robe → id réseau (dérivé de [Robes.IDS], partiel). */
    private val ID_OF: Map<String, Int> = Robes.IDS.entries.associate { (id, name) -> name to id }

    /** Toutes les robes muldo connues, dérivées de [Robes.GENERATIONS]. */
    val ALL: List<MuldoRobe> = Robes.GENERATIONS.flatMap { (gen, names) ->
        names.map { name -> MuldoRobe(name, gen, ID_OF[name], RECIPE[name]) }
    }

    val BY_NAME: Map<String, MuldoRobe> = ALL.associateBy { it.name }

    /** id réseau → robe (espace commun robe propre / robes parentales, cf. [Robes.IDS]). */
    val BY_ID: Map<Int, MuldoRobe> = ALL.mapNotNull { r -> r.networkId?.let { it to r } }.toMap()

    /** Robes gen 1 (les seules capturables directement). */
    val GEN1: Set<String> = ALL.filter { it.gen == 1 }.map { it.name }.toSet()

    fun byName(name: String): MuldoRobe? = BY_NAME[name]

    /** Recette d'une robe (2 robes parentes), ou null si gen 1 / robe inconnue. */
    fun recipeOf(name: String): Pair<String, String>? = BY_NAME[name]?.recipe

    /**
     * Résout un nom de robe **lâche** (casse, accents et ordre des couleurs indifférents) vers la
     * robe canonique. Indispensable pour réconcilier les libellés de succès (« Muldo Ebène »,
     * « Emeraude »…) avec les noms accentués de [Robes.GENERATIONS]. null si non trouvé.
     */
    fun resolve(looseName: String): MuldoRobe? = BY_NAME[looseName] ?: BY_KEY[normKey(looseName)]

    /**
     * Clé de **fichier image** stable d'une robe : dérivée de [normKey] (sans accents, minuscule,
     * couleurs triées → ordre indifférent), jointe par « _ » pour rester un nom de fichier valide.
     * Sert de pont avec les images DofusDB téléchargées (cf. `tools/MuldoImageDownloader`) et au
     * chargement runtime (`ui/RobeImages`). Ex. « Prune et Ivoire » → « ivoire_prune ».
     */
    fun imageKey(name: String): String = normKey(name).replace("|", "_")

    /** Clé normalisée : sans accents, minuscule, couleurs triées (ordre indifférent). */
    private fun normKey(name: String): String =
        java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase()
            .split(" et ")
            .map { it.trim() }
            .sorted()
            .joinToString("|")

    private val BY_KEY: Map<String, MuldoRobe> = ALL.associateBy { normKey(it.name) }
}

/** Une robe de muldo et tout ce qu'il faut pour la planifier : génération, id réseau, recette. */
data class MuldoRobe(
    /** Nom canonique accentué, aligné sur [Robes.GENERATIONS] (ex. « Roux et Doré »). */
    val name: String,
    /** Génération 1..10. */
    val gen: Int,
    /** Id réseau (champ feam/feap) si relevé, sinon null. */
    val networkId: Int?,
    /** Les 2 robes parentes qui la produisent ; null pour une robe gen 1 (capturable). */
    val recipe: Pair<String, String>?,
)
