package fr.lilone.bingobreed.breeding

import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
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

    /**
     * Horizon (en nombre de croisements restants) au-delà duquel on **n'expose plus les captures** d'une
     * cible : tant qu'une robe est loin (gros sous-arbre vide), proposer de capturer ses gen 1 serait
     * « halluciner » des scénarios trop en avance. On capture donc seulement pour les cibles **proches**.
     * Les **montées de jauges** de montures déjà possédées, elles, sont toujours proposées (jamais
     * hallucinées) — c'est le gros du travail parallélisable.
     */
    const val DEFAULT_CAPTURE_HORIZON = 4

    /**
     * Construit la **checklist** vers le full succès par **balayage de frontière** : on calcule l'ensemble
     * des robes nécessaires (cascades des cibles restantes) puis, pour chacune, on émet **toutes** les
     * actions actionnables maintenant — monter **chaque** fertile possédée, cloner, croiser quand 2 parents
     * sont prêts. Les **captures** ne sont émises que pour les cibles **proches** ([captureHorizon]) pour
     * ne pas plonger trop loin dans l'arbre. Tout est dédupliqué et classé par [StepStatus].
     *
     * [activeElementsByPaddock] = éléments de jauge actifs (ordinaux hhc) **par index d'enclos** (clé =
     * `Paddock.id`, nullable). Une montée est jugée « en cours » contre les éléments de **l'enclos où se
     * trouve la monture**, pas contre l'onglet d'enclos actuellement ouvert — ainsi changer d'onglet
     * in-game ne déplace pas les étapes.
     */
    fun plan(
        remaining: List<String>,
        stock: OwnedStock,
        optimakina: Boolean,
        activeElementsByPaddock: Map<Int?, Set<Int>> = emptyMap(),
        captureHorizon: Int = DEFAULT_CAPTURE_HORIZON,
    ): ReproPlan {
        if (remaining.isEmpty()) {
            return ReproPlan(emptyList(), emptyList(), emptyList(), emptyMap(), fullSuccess = true)
        }
        val remainingSet = remaining.toHashSet()

        // Robes **à produire** (croisement/clone/capture) : cascade élaguée aux robes déjà obtenables ;
        // sous-ensemble « proche » (cascade ≤ horizon) pour les captures uniquement.
        val needed = HashSet<String>()
        val captureNeeded = HashSet<String>()
        for (target in remaining) {
            val cascade = buildCascade(target, stock)
            val near = cascade.size <= captureHorizon
            fun mark(robe: String) { needed += robe; if (near) captureNeeded += robe }
            mark(target)
            cascade.forEach { mark(it.target); mark(it.parentA); mark(it.parentB) }
        }

        // Robes dont on **monte les fertiles** : ascendance **complète** des cibles, **sans** élagage
        // « obtenable ». Sinon une fertile de gen paire (ex. « Turquoise et Pourpre », 50 % de Prune)
        // restait invisible dès que son enfant gen impaire (Prune) était déjà obtenable et donc élagué :
        // l'enfant était monté, jamais ses parents. Monter une fertile déjà possédée est toujours du
        // travail utile et borné par le stock ; les robes hors ascendance (gen terminales déjà validées)
        // restent exclues. [needed] ⊆ [raiseAncestry].
        val raiseAncestry = usefulRobes(remaining)
        // Montures **extractibles** : robe hors ascendance des cibles → ne peut plus contribuer à aucun
        // succès restant (cf. [usefulRobes]). Groupées par robe pour l'UI.
        val extractableByRobe = stock.byRobe.filterKeys { it !in raiseAncestry }

        val steps = LinkedHashMap<String, ReproStep>() // clé de dédup → étape (1re occurrence conservée)
        fun put(key: String, action: NextAction, status: StepStatus) = steps.putIfAbsent(key, ReproStep(action, status))

        raiseAncestry.sortedWith(compareBy({ MuldoRobes.byName(it)?.gen ?: Int.MAX_VALUE }, { it })).forEach { robe ->
            // Monter chaque fertile possédée de cette robe (en cours si déjà dans l'enclos sur la bonne jauge).
            stock.fertiles(robe).forEach { f ->
                put("raise:${f.uuid}", NextAction.RaiseGauges(f),
                    if (isRaising(f, activeElementsByPaddock)) StepStatus.IN_PROGRESS else StepStatus.TO_PREPARE)
            }
            // Production (clone/croisement/capture) : réservée aux robes **à produire** ([needed] élagué),
            // pour ne pas sur-produire une robe déjà obtenable autrement.
            if (robe !in needed) return@forEach
            // Cloner ≥2 stériles identiques.
            val ster = stock.steriles(robe).sortedByDescending { it.level }
            if (ster.size >= 2) put("clone:$robe", NextAction.Clone(robe, ster[0], ster[1]), StepStatus.TO_PREPARE)
            // Croiser dès que les 2 parents ont une féconde (READY si la robe est un succès, sinon intermédiaire).
            val mr = MuldoRobes.byName(robe)
            val recipe = mr?.recipe
            if (recipe != null && stock.fecondes(recipe.first).isNotEmpty() && stock.fecondes(recipe.second).isNotEmpty()) {
                when (val a = pairCross(robe, mr.gen, recipe.first, recipe.second, stock, optimakina)) {
                    is NextAction.Cross ->
                        put("cross:$robe", a, if (robe in remainingSet) StepStatus.READY else StepStatus.TO_PREPARE)
                    is NextAction.NeedOppositeSex -> put("oppsex:$robe", a, StepStatus.TO_PREPARE)
                    else -> Unit
                }
            }
            // Capturer une gen 1 manquante — seulement pour une cible proche (anti-hallucination).
            if (robe in captureNeeded && robe in MuldoRobes.GEN1 && stock.all(robe).isEmpty()) {
                put("capture:$robe", NextAction.Capture(robe), StepStatus.TO_PREPARE)
            }
        }

        // Tri : statut d'abord, puis **génération décroissante** au sein du statut — on met en haut les
        // étapes les plus hautes (proches de la vraie cible) pour converger dessus plutôt que de
        // s'éparpiller sur les gen 1 ; captures (gen 1) en bas, naturellement.
        val sorted = steps.values.sortedWith(
            compareBy<ReproStep> { it.status.ordinal }
                .thenByDescending { stepGen(it.action) }
                .thenBy { kindRank(it.action) }
                .thenBy { stepLabel(it.action) }
        )
        return ReproPlan(
            steps = sorted,
            justBred = stock.justBred().distinctBy { it.uuid },
            remainingTargets = remaining,
            extractableByRobe = extractableByRobe,
            fullSuccess = false,
        )
    }

    /**
     * Robes **utiles** : ascendance-recette **complète** des cibles restantes (sans élagage). Une robe
     * hors de cet ensemble ne peut plus être parente (directe ou transitive) d'aucun succès restant : ses
     * montures sont donc extractibles. Démontrable car une robe ne se produit **que** via sa recette
     * ([MuldoRobes.recipe]), dont les 2 parents sont par construction dans cette ascendance.
     */
    fun usefulRobes(remaining: List<String>): Set<String> {
        val acc = HashSet<String>()
        fun add(robe: String) {
            if (!acc.add(robe)) return
            MuldoRobes.byName(robe)?.recipe?.let { (a, b) -> add(a); add(b) }
        }
        remaining.forEach(::add)
        return acc
    }

    /** Priorité d'affichage intra-statut : croisements d'abord, captures en dernier (recours). */
    private fun kindRank(action: NextAction): Int = when (action) {
        is NextAction.Cross -> 0
        is NextAction.RaiseGauges -> 1
        is NextAction.Clone -> 2
        is NextAction.NeedOppositeSex -> 3
        is NextAction.Capture -> 4
    }

    /** Éléments d'enclos (ordinaux hhc) qui agissent sur la **sérénité** (pas une jauge de monture). */
    private val SERENITY_ELEMENTS = setOf(0, 1) // 0 = baffeur (baisse), 1 = caresseur (monte)

    /** Élément de jauge d'enclos (ordinal hhc) → type de jauge monture monté, ou null (sérénité/inconnu). */
    private fun fuelElementToGauge(el: Int): Int? = when (el) {
        2 -> MountGauge.TYPE_ENDURANCE // foudroyeur
        3 -> MountGauge.TYPE_MATURITY  // abreuvoir
        4 -> MountGauge.TYPE_LOVE      // dragofesse
        else -> null
    }

    /**
     * Une montée est **en cours** si la monture est **dans un enclos** ET qu'un élément **utile** est
     * actif **dans ce même enclos** : soit un élément monte une jauge actuellement débloquée par sa
     * bande de sérénité, soit un élément de sérénité est actif (réglage en cours, ex. « monter la
     * sérénité avant l'amour »). On regarde les éléments de l'enclos de la monture (via [MountLocation]),
     * pas ceux de l'onglet ouvert, pour rester stable au changement d'onglet.
     */
    private fun isRaising(mount: OwnedMount, activeElementsByPaddock: Map<Int?, Set<Int>>): Boolean {
        val location = mount.location
        if (location !is MountLocation.Paddock) return false
        val activeElements = activeElementsByPaddock[location.id] ?: return false
        val risingTypes = activeElements.mapNotNull(::fuelElementToGauge).toSet()
        val gaugeWork = mount.serenityBand.enables.any { it in risingTypes }
        val serenityWork = activeElements.any { it in SERENITY_ELEMENTS }
        return gaugeWork || serenityWork
    }

    /** Génération de la robe concernée par l'action (tri intra-statut). */
    private fun stepGen(action: NextAction): Int = when (action) {
        is NextAction.Cross -> action.targetGen
        is NextAction.RaiseGauges -> MuldoRobes.byName(action.mount.robe)?.gen ?: Int.MAX_VALUE
        is NextAction.Clone -> MuldoRobes.byName(action.robe)?.gen ?: Int.MAX_VALUE
        is NextAction.Capture -> 1
        is NextAction.NeedOppositeSex -> MuldoRobes.byName(action.target)?.gen ?: Int.MAX_VALUE
    }

    /** Libellé stable pour départager le tri intra-statut/génération. */
    private fun stepLabel(action: NextAction): String = when (action) {
        is NextAction.Cross -> action.target
        is NextAction.RaiseGauges -> action.mount.robe + action.mount.uuid
        is NextAction.Clone -> action.robe
        is NextAction.Capture -> action.robe
        is NextAction.NeedOppositeSex -> action.target
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

    /**
     * Choisit la meilleure paire féconde de sexes opposés (somme de niveaux max → meilleur p) pour
     * croiser [aRobe] × [bRobe] → [target]. Si les 2 robes ont des fécondes mais toutes du même sexe,
     * renvoie [NextAction.NeedOppositeSex]. Pré-requis appelant : chaque robe a ≥1 féconde.
     */
    private fun pairCross(
        target: String, gen: Int, aRobe: String, bRobe: String, stock: OwnedStock, optimakina: Boolean,
    ): NextAction {
        val fa = stock.fecondes(aRobe)
        val fb = stock.fecondes(bRobe)
        var best: Pair<OwnedMount, OwnedMount>? = null
        var bestLevel = -1
        for (x in fa) for (y in fb) {
            if (x.uuid != y.uuid && x.sex != y.sex && x.level + y.level > bestLevel) {
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
}
