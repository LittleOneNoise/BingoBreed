package fr.lilone.bingobreed.breeding

import fr.lilone.bingobreed.sniffer.model.breeding.MuldoRobes
import fr.lilone.bingobreed.sniffer.model.breeding.Sex

/**
 * Planificateur « coach » : à partir des robes restantes, du stock du joueur et du toggle
 * optimakina, désigne la **robe cible** prioritaire, la **prochaine action concrète** à faire
 * maintenant, et la **cascade** théorique restante.
 *
 * Modèle 3.0 : DAG de recettes ([MuldoRobes]) remonté récursivement (arbre binaire) jusqu'aux
 * gen 1. À chaque robe-parent requise, un résolveur applique la priorité validée
 * (féconde > monter les jauges > cloner si ≥2 stériles > recréer/capturer), regardant fécondes,
 * fertiles **et** stériles. La généalogie n'entre pas dans la probabilité (cf. [BreedingProbability]) :
 * elle est garantie par la construction de la lignée.
 */
object ReproPlanner {

    fun plan(remaining: List<String>, stock: OwnedStock, optimakina: Boolean): ReproPlan {
        if (remaining.isEmpty()) {
            return ReproPlan(null, null, null, emptyList(), emptyList(), fullSuccess = true)
        }
        // Cible mise en avant : plus petite génération restante, puis cascade restante la plus courte.
        val target = remaining.minWithOrNull(
            compareBy(
                { MuldoRobes.byName(it)?.gen ?: Int.MAX_VALUE },
                { buildCascade(it, stock).size },
                { it },
            )
        )!!
        return ReproPlan(
            target = target,
            targetGen = MuldoRobes.byName(target)?.gen,
            nextAction = nextActionFor(target, stock, optimakina),
            cascade = buildCascade(target, stock),
            remainingTargets = remaining,
            fullSuccess = false,
        )
    }

    /** Cascade restante vers [target] : croisements à faire, élagués aux robes déjà obtenables, en ordre topologique. */
    fun buildCascade(target: String, stock: OwnedStock): List<CascadeStep> {
        val steps = LinkedHashMap<String, CascadeStep>()
        val visiting = HashSet<String>()
        fun visit(robe: String, isTarget: Boolean) {
            if (robe in steps) return
            if (!isTarget && stock.obtainable(robe)) return  // déjà dispo en stock → on s'arrête (jamais sur la cible)
            val mr = MuldoRobes.byName(robe) ?: return
            val recipe = mr.recipe ?: return                 // gen 1 → feuille (capture), pas un croisement
            if (!visiting.add(robe)) return                  // garde-fou anti-cycle
            visit(recipe.first, isTarget = false)
            visit(recipe.second, isTarget = false)
            visiting.remove(robe)
            steps[robe] = CascadeStep(robe, mr.gen, recipe.first, recipe.second)
        }
        visit(target, isTarget = true)
        return steps.values.toList()
    }

    /* ------------------------------------------------------------------ interne */

    /** La cible est toujours **produite** (naissance fraîche) : capture si gen 1, sinon croisement. */
    private fun nextActionFor(target: String, stock: OwnedStock, optimakina: Boolean): NextAction {
        val robe = MuldoRobes.byName(target)
        val recipe = robe?.recipe ?: return NextAction.Capture(target)
        return crossStep(target, robe.gen, recipe.first, recipe.second, stock, optimakina)
    }

    /**
     * Étape de croisement [aRobe] × [bRobe] → [target]. Si les 2 parents sont prêts (fécondes),
     * renvoie le croisement ; sinon descend et renvoie la première sous-action exécutable.
     */
    private fun crossStep(
        target: String, gen: Int, aRobe: String, bRobe: String, stock: OwnedStock, optimakina: Boolean,
    ): NextAction {
        val ra = resolveParent(aRobe, stock, optimakina)
        val rb = resolveParent(bRobe, stock, optimakina)
        (ra as? ParentState.Step)?.let { return it.action }
        (rb as? ParentState.Step)?.let { return it.action }
        // Les deux parents sont prêts : croiser une paire de sexes opposés.
        return pairCross(target, gen, aRobe, bRobe, stock, optimakina)
    }

    /** Choisit la meilleure paire féconde de sexes opposés (somme de niveaux max → meilleur p). */
    private fun pairCross(
        target: String, gen: Int, aRobe: String, bRobe: String, stock: OwnedStock, optimakina: Boolean,
    ): NextAction {
        val fa = stock.fecondes(aRobe)
        val fb = stock.fecondes(bRobe)
        var best: Pair<OwnedMount, OwnedMount>? = null
        var bestLevel = -1
        for (x in fa) for (y in fb) {
            if (x.sex != y.sex && x.level + y.level > bestLevel) {
                bestLevel = x.level + y.level
                best = x to y
            }
        }
        if (best == null) {
            val have = (fa + fb).firstOrNull()?.sex ?: Sex.MALE
            return NextAction.NeedOppositeSex(target, aRobe, bRobe, have)
        }
        val (x, y) = best
        val mother = if (x.sex == Sex.FEMALE) x else y
        val father = if (x.sex == Sex.MALE) x else y
        return NextAction.Cross(target, gen, mother, father, BreedingProbability.calcP(mother.level, father.level, optimakina))
    }

    /** État d'un parent requis : prêt (féconde) ou action à faire pour le rendre prêt. */
    private sealed interface ParentState {
        data class Ready(val mount: OwnedMount) : ParentState
        data class Step(val action: NextAction) : ParentState
    }

    /**
     * Résout l'obtention d'**un parent féconde** de [robe], par priorité validée :
     * féconde dispo > monter les jauges d'une fertile > cloner ≥2 stériles > recréer (recette)/capturer.
     * Le cas « 1 stérile esseulée » tombe sur recréer (auto le moins cher actionnable).
     */
    private fun resolveParent(robe: String, stock: OwnedStock, optimakina: Boolean): ParentState {
        stock.fecondes(robe).maxByOrNull { it.level }?.let { return ParentState.Ready(it) }
        stock.fertiles(robe).maxByOrNull { it.gaugeTotal }?.let { return ParentState.Step(NextAction.RaiseGauges(it)) }
        val ster = stock.steriles(robe).sortedByDescending { it.level }
        if (ster.size >= 2) return ParentState.Step(NextAction.Clone(robe, ster[0], ster[1]))
        // 0 ou 1 stérile (clone impossible) → recréer la robe (récursion vers sa recette / capture gen 1).
        val mr = MuldoRobes.byName(robe)
        val recipe = mr?.recipe ?: return ParentState.Step(NextAction.Capture(robe))
        return ParentState.Step(crossStep(robe, mr.gen, recipe.first, recipe.second, stock, optimakina))
    }
}
