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
    /** Id du succès (champ `mgc.gfsm`). */
    val id: Int,
    /** Id de la catégorie d'où il a été vu (déduit de la dernière requête `mgi`), si connu. */
    val categoryId: Int?,
    val objectives: List<AchievementObjective>,
) {
    /** Obtenu = au moins un objectif et tous terminés. */
    val obtained: Boolean = objectives.isNotEmpty() && objectives.all { it.completed }

    /** Nombre d'objectifs terminés. */
    val completedCount: Int = objectives.count { it.completed }

    /** Marque comme validés côté client les objectifs dont l'id est dans [objectiveIds] (cf. [AchievementObjective.locallyValidated]). */
    fun withLocalValidations(objectiveIds: Set<Int>): Achievement {
        if (objectiveIds.isEmpty()) return this
        val patched = objectives.map { o ->
            if (o.id in objectiveIds && !o.completed) o.copy(locallyValidated = true) else o
        }
        return if (patched == objectives) this else copy(objectives = patched)
    }
}

/**
 * Objectif d'un succès. Le champ réseau `mga.gfsg` (valeur courante) est **optional** :
 *  - présent ([current] non-null) → objectif **en cours** (progression [current] / [target]) ;
 *  - absent ([current] null) → objectif **terminé**.
 */
data class AchievementObjective(
    /** Id de l'objectif (champ `mga.gfsi`). */
    val id: Int,
    /** Valeur courante (champ `mga.gfsg`), null si l'objectif est terminé. */
    val current: Long?,
    /** Valeur cible (champ `mga.gfsf`). */
    val target: Long,
    /**
     * Validé **côté BingoBreed**, pas (encore) par le jeu : le serveur ne repousse pas la liste des
     * succès après une naissance, donc un objectif de robe fraîchement obtenu resterait « à faire »
     * jusqu'à la prochaine ouverture manuelle de la catégorie en jeu — et le planificateur de repro
     * continuerait à viser une robe déjà acquise. Mis à `true` à la naissance (cf.
     * `SnifferEngine.registerBirths`), remis à plat dès que le jeu confirme ([current] null).
     */
    val locallyValidated: Boolean = false,
) {
    val completed: Boolean get() = current == null || locallyValidated
}
