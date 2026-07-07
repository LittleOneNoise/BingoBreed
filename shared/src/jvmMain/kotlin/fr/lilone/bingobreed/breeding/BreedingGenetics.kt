package fr.lilone.bingobreed.breeding

import fr.lilone.bingobreed.sniffer.model.breeding.MuldoRobes

/**
 * Calcul des probabilités de **robe du bébé** (élevage Dofus 2026), d'après la formule communautaire
 * rétro-ingénierée (vérifiée en jeu jusqu'à gen 5), cohérente avec le devblog Ankama.
 *
 * Modèle : **un seul croisement** entre une race de l'arbre du père et une race de l'arbre de la mère.
 * L'arbre d'un parent = sa robe propre (**parent direct**, ×5) + les robes de ses 2 parents
 * (**grands-parents** du bébé, ×3). La consanguinité n'existe plus ; on ne regarde pas plus loin que
 * les grands-parents.
 *
 * Chaque race porte un poids = `poids_base × position`, avec `poids_base` = 9 (monocolore) ou 2
 * (bicolore, et monocolores « exception »). Chaque **couple** (raceP, raceM) a un poids
 * `poids(raceP) × poids(raceM)` et distribue ce poids à ses deux membres **et** à l'enfant
 * `MuldoRobes.childOf(raceP, raceM)` (s'il existe). La **génération cible** = la plus haute génération
 * parmi les robes-résultats ; elle reçoit `calcP(...)` réparti au prorata des poids, le reste
 * (1 − calcP) allant aux autres générations au prorata.
 */
object BreedingGenetics {

    /** Multiplicateur de position : parent direct. */
    const val PARENT_MULT = 5
    /** Multiplicateur de position : grand-parent (= 0,6 × parent). */
    const val GRANDPARENT_MULT = 3

    /** Poids de base d'une monocolore « normale ». */
    const val MONO_WEIGHT = 9
    /** Poids de base d'une bicolore et des monocolores « exception ». */
    const val BI_WEIGHT = 2

    /**
     * Monocolores à poids réduit (2 au lieu de 9). **Confirmé** pour les monos muldo gen 9. Le muldo
     * « Doré » (gen 1) a un statut incertain (la Dorée dragodinde est une exception héritée de l'ancien
     * système) : à confirmer en jeu — l'ajouter ici si les probas affichées le montrent.
     */
    val LOW_WEIGHT_MONOS: Set<String> = setOf("Ambre", "Corail", "Azur", "Aigue-marine")

    private fun isBicolor(robe: String): Boolean = robe.contains(" et ")

    /** Poids de base d'une robe (avant multiplicateur de position). */
    fun baseWeight(robe: String): Int =
        if (isBicolor(robe) || robe in LOW_WEIGHT_MONOS) BI_WEIGHT else MONO_WEIGHT

    /**
     * Arbre d'un parent réduit aux robes pondérées : robe propre (×5) + robes des 2 parents (×3),
     * poids **fusionnés par robe** (multiplicité gérée par sommation). Robes vides ignorées.
     */
    fun treeWeights(ownRobe: String, parentRobes: List<String>): Map<String, Int> {
        val acc = LinkedHashMap<String, Int>()
        acc.merge(ownRobe, baseWeight(ownRobe) * PARENT_MULT, Int::plus)
        parentRobes.forEach { gp -> acc.merge(gp, baseWeight(gp) * GRANDPARENT_MULT, Int::plus) }
        return acc
    }

    /** Une robe possible du bébé : son [gen], le [weight] cumulé, la proba [p], et si c'est la gén. cible. */
    data class RobeChance(val robe: String, val gen: Int, val weight: Int, val p: Double, val targetGen: Boolean)

    /** Résultat complet d'un croisement, pour l'affichage/vérification. */
    data class Outcome(
        val targetGen: Int,
        val pTargetGen: Double,
        /** Robes possibles, triées par proba décroissante. */
        val chances: List<RobeChance>,
        val fatherTree: Map<String, Int>,
        val motherTree: Map<String, Int>,
    )

    private fun genOf(robe: String): Int = MuldoRobes.byName(robe)?.gen ?: 0

    /**
     * Distribution des robes du bébé pour un croisement père × mère (l'assignation père/mère n'affecte
     * pas les probabilités : le modèle est symétrique). [fatherParents]/[motherParents] = robes des
     * grands-parents (vide si inconnues).
     */
    fun compute(
        fatherRobe: String, fatherParents: List<String>, fatherLevel: Int,
        motherRobe: String, motherParents: List<String>, motherLevel: Int,
        optimakina: Boolean,
    ): Outcome {
        val fTree = treeWeights(fatherRobe, fatherParents)
        val mTree = treeWeights(motherRobe, motherParents)

        // Chaque couple distribue son poids à {membre père, membre mère, enfant}. Si les deux membres
        // sont la même robe, elle reçoit le poids deux fois (multiplicité). L'enfant n'est ajouté que
        // s'il diffère des deux membres (sinon déjà couvert par la multiplicité des membres).
        val raceWeight = LinkedHashMap<String, Int>()
        for ((rf, wf) in fTree) for ((rm, wm) in mTree) {
            val w = wf * wm
            raceWeight.merge(rf, w, Int::plus)
            raceWeight.merge(rm, w, Int::plus)
            val child = MuldoRobes.childOf(rf, rm)
            if (child != null && child != rf && child != rm) raceWeight.merge(child, w, Int::plus)
        }

        val targetGen = raceWeight.keys.maxOfOrNull(::genOf) ?: 0
        val pTarget = BreedingProbability.calcP(fatherLevel, motherLevel, optimakina)
        val targetSum = raceWeight.entries.filter { genOf(it.key) == targetGen }.sumOf { it.value }
        val otherSum = raceWeight.entries.filter { genOf(it.key) != targetGen }.sumOf { it.value }

        val chances = raceWeight.map { (robe, w) ->
            val g = genOf(robe)
            val p = if (g == targetGen) {
                // Si aucune robe hors-cible (tout le pool est à la gén. max), la cible prend toute la proba.
                val share = if (otherSum == 0) 1.0 else pTarget
                if (targetSum > 0) share * w / targetSum else 0.0
            } else {
                if (otherSum > 0) (1.0 - pTarget) * w / otherSum else 0.0
            }
            RobeChance(robe, g, w, p, g == targetGen)
        }.sortedByDescending { it.p }

        return Outcome(targetGen, pTarget, chances, fTree, mTree)
    }
}
