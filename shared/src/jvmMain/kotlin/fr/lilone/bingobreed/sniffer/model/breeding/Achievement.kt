package fr.lilone.bingobreed.sniffer.model.breeding

/**
 * Modèle de domaine **stable** des succès — découplé des numéros de champ protobuf
 * (cf. [Paddock]). Le mapping wire → domaine vit dans
 * [fr.lilone.bingobreed.sniffer.parser.breeding.AchievementMapper].
 */

/** Familles de succès d'élevage, pour le regroupement par sous-onglets dans l'UI. */
enum class AchievementCategory(val label: String) {
    GENERAL("Élevage général"),
    DRAGODINDE("Dragodinde"),
    MULDO("Muldo"),
    VOLKORNE("Volkorne"),
}

/** Un succès et ses objectifs. */
data class Achievement(
    /** Id du succès (champ `ftht`). */
    val id: Int,
    /** Id de la catégorie d'où il a été vu (déduit de la dernière requête `lfc`), si connu. */
    val categoryId: Int?,
    val objectives: List<AchievementObjective>,
) {
    /** Obtenu = au moins un objectif et tous terminés. */
    val obtained: Boolean = objectives.isNotEmpty() && objectives.all { it.completed }

    /** Nombre d'objectifs terminés. */
    val completedCount: Int = objectives.count { it.completed }
}

/**
 * Objectif d'un succès. Le champ réseau `fthm` (valeur courante) est **optional** :
 *  - présent ([current] non-null) → objectif **en cours** (progression [current] / [target]) ;
 *  - absent ([current] null) → objectif **terminé**.
 */
data class AchievementObjective(
    /** Id de l'objectif (champ `fthp`). */
    val id: Int,
    /** Valeur courante (champ `fthm`), null si l'objectif est terminé. */
    val current: Long?,
    /** Valeur cible (champ `ftho`). */
    val target: Long,
) {
    val completed: Boolean get() = current == null
}
