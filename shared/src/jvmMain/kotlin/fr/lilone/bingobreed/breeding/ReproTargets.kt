package fr.lilone.bingobreed.breeding

import fr.lilone.bingobreed.sniffer.model.breeding.Achievement
import fr.lilone.bingobreed.sniffer.model.breeding.AchievementCategory
import fr.lilone.bingobreed.sniffer.model.breeding.AchievementRegistry
import fr.lilone.bingobreed.sniffer.model.breeding.MuldoRobes

/**
 * Déduit, depuis l'avancement des succès du joueur, les robes de muldo **restant à faire naître**
 * pour le full succès. Chaque robe correspond à un objectif de succès (`mga.gfsi`) nommé via
 * [AchievementRegistry.objectiveText] (ex. « Muldo Ebène »), validé quand l'objectif est
 * `completed`.
 *
 * Robe **non vue / non chargée** ⇒ considérée comme **à faire** (cf. plan) : on part de toutes les
 * robes muldo connues ([MuldoRobes]) et on retire celles validées. Inclut les robes gen 1 (validées
 * par capture / « Prélèvement marin »).
 */
object ReproTargets {

    private const val PREFIX = "Muldo "

    /**
     * True seulement quand le **détail** des succès Muldo est chargé, c.-à-d. qu'au moins un objectif
     * **de robe** est connu. La vue d'ensemble générale (`mfo`) ne porte que le méta Muldo (1497) dont
     * les objectifs sont des sous-succès de génération (pas des robes) : elle ne suffit donc pas, sinon
     * l'onglet s'activerait en affichant « 0 robe validée » tant que le joueur n'a pas ouvert la
     * catégorie Muldo en jeu (qui déclenche le `mfn` détaillé).
     */
    fun hasMuldoData(achievements: Map<Int, Achievement>): Boolean =
        achievements.values
            .filter { AchievementRegistry.category(it.id) == AchievementCategory.MULDO }
            .any { ach -> ach.objectives.any { robeOf(it.id) != null } }

    /** Robes muldo dont un objectif de naissance/capture est validé. */
    fun validatedMuldoRobes(achievements: Map<Int, Achievement>): Set<String> = buildSet {
        achievements.values
            .filter { AchievementRegistry.category(it.id) == AchievementCategory.MULDO }
            .forEach { ach ->
                ach.objectives.filter { it.completed }.forEach { obj ->
                    robeOf(obj.id)?.let { add(it) }
                }
            }
    }

    /** Robes muldo restant à valider = toutes les robes connues − celles validées. Triées par génération. */
    fun remainingMuldoRobes(achievements: Map<Int, Achievement>): List<String> {
        val validated = validatedMuldoRobes(achievements)
        return MuldoRobes.ALL
            .filter { it.name !in validated }
            .sortedWith(compareBy({ it.gen }, { it.name }))
            .map { it.name }
    }

    /**
     * Id de l'objectif de succès validé par la **naissance** d'un muldo de robe [robe], ou null si la
     * robe n'a pas d'objectif connu. Inverse de [robeOf] ; sert à valider l'objectif côté client dès la
     * naissance, le jeu ne repoussant pas la liste des succès (cf.
     * [fr.lilone.bingobreed.sniffer.model.breeding.AchievementObjective.locallyValidated]).
     */
    fun objectiveIdOfRobe(robe: String): Int? = OBJECTIVE_OF_ROBE[robe]

    /** Nom de robe canonique d'un objectif, ou null si l'objectif n'est pas une robe (méta génération…). */
    private fun robeOf(objectiveId: Int): String? {
        val text = AchievementRegistry.objectiveText(objectiveId) ?: return null
        if (!text.startsWith(PREFIX)) return null
        return MuldoRobes.resolve(text.removePrefix(PREFIX).trim())?.name
    }

    /**
     * Index inverse robe canonique → id d'objectif, restreint aux objectifs **Muldo** (le préfixe est
     * obligatoire dans [robeOf] : « Volkorne Ebène » et « Muldo Ebène » résoudraient tous deux vers la
     * robe muldo « Ébène » sinon).
     */
    private val OBJECTIVE_OF_ROBE: Map<String, Int> =
        AchievementRegistry.ALL_OBJECTIVES.keys.mapNotNull { id -> robeOf(id)?.let { it to id } }.toMap()
}
