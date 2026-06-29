package fr.lilone.bingobreed.breeding

import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.MuldoRobes
import fr.lilone.bingobreed.sniffer.model.breeding.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReproPlannerTest {

    private var seq = 0
    private fun mount(
        robe: String,
        fert: Fertility,
        sex: Sex = Sex.FEMALE,
        level: Int = 100,
        gaugeTotal: Int = 0,
        serenity: Int = 0,
        location: MountLocation = MountLocation.Stable,
        consumed: Boolean = false,
    ) = OwnedMount(
        uuid = "uuid-${seq++}",
        name = "M$seq",
        robe = robe,
        sex = sex,
        level = level,
        fertility = fert,
        serenity = serenity,
        gaugeTotal = gaugeTotal,
        location = location,
        consumed = consumed,
    )

    private fun stockOf(vararg mounts: OwnedMount) = OwnedStock(mounts.toList().groupBy { it.robe })

    private val empty = OwnedStock(emptyMap())

    /** Première action de la checklist (toutes confondues), pour les scénarios à 1 robe restante. */
    private fun firstAction(plan: ReproPlan) = plan.steps.first().action
    private fun readyTargets(plan: ReproPlan) =
        plan.stepsOf(StepStatus.READY).map { (it.action as NextAction.Cross).target }

    @Test
    fun calcPMatchesModel() {
        assertEquals(0.30, BreedingProbability.calcP(0, 0, false), 1e-9)
        assertEquals(0.60, BreedingProbability.calcP(100, 100, false), 1e-9)
        assertEquals(0.90, BreedingProbability.calcP(200, 200, false), 1e-9)
        assertEquals(1.0, BreedingProbability.calcP(200, 200, true), 1e-9)   // plafonné à 100 %
    }

    @Test
    fun cascadeFromEmptyStockExpandsDownToGen1() {
        val cascade = ReproPlanner.buildCascade("Roux", empty)
        // Roux = "Doré et Pourpre" × "Doré et Orchidée" ; les gen 1 sont des feuilles (capture, pas une étape).
        assertEquals(listOf("Doré et Pourpre", "Doré et Orchidée", "Roux"), cascade.map { it.target })
        assertEquals("Roux", cascade.last().target)
    }

    @Test
    fun emptyStockYieldsACaptureToPrepare() {
        val plan = ReproPlanner.plan(listOf("Roux"), empty, optimakina = false)
        val next = firstAction(plan)
        assertIs<NextAction.Capture>(next)
        assertTrue(next.robe in MuldoRobes.GEN1)
        assertEquals(StepStatus.TO_PREPARE, plan.steps.first().status)
    }

    @Test
    fun bothParentsFecondeGivesAReadyCrossWithProbability() {
        val stock = stockOf(
            mount("Doré et Pourpre", Fertility.FECONDE, Sex.FEMALE, level = 150),
            mount("Doré et Orchidée", Fertility.FECONDE, Sex.MALE, level = 150),
        )
        val plan = ReproPlanner.plan(listOf("Roux"), stock, optimakina = false)
        val ready = plan.stepsOf(StepStatus.READY)
        assertEquals(1, ready.size)
        val cross = ready.first().action as NextAction.Cross
        assertEquals("Roux", cross.target)
        assertEquals(Sex.FEMALE, cross.mother.sex)
        assertEquals(Sex.MALE, cross.father.sex)
        assertEquals(BreedingProbability.calcP(150, 150, false), cross.pSuccess, 1e-9)
    }

    @Test
    fun fertileParentIsRaisedBeforeCrossing() {
        // Priorité : monter les jauges d'une fertile plutôt que la recréer.
        val stock = stockOf(
            mount("Doré et Pourpre", Fertility.FERTILE, Sex.FEMALE, gaugeTotal = 30000),
            mount("Doré et Orchidée", Fertility.FECONDE, Sex.MALE),
        )
        val plan = ReproPlanner.plan(listOf("Roux"), stock, optimakina = false)
        val next = firstAction(plan)
        assertIs<NextAction.RaiseGauges>(next)
        assertEquals("Doré et Pourpre", next.mount.robe)
        // Hors enclos → à préparer, pas en cours.
        assertEquals(StepStatus.TO_PREPARE, plan.steps.first { it.action is NextAction.RaiseGauges }.status)
    }

    @Test
    fun raisingInActiveEnclosIsInProgress() {
        // Même scénario, mais la fertile est dans l'enclos avec la jauge amour active → "en cours".
        val stock = stockOf(
            mount(
                "Doré et Pourpre", Fertility.FERTILE, Sex.FEMALE,
                serenity = 3000, // bande GREEN → amour montable
                location = MountLocation.Paddock(1),
            ),
            mount("Doré et Orchidée", Fertility.FECONDE, Sex.MALE),
        )
        // Élément actif 4 = dragofesse (amour) dans l'enclos 1.
        val plan = ReproPlanner.plan(listOf("Roux"), stock, optimakina = false, activeElementsByPaddock = mapOf<Int?, Set<Int>>(1 to setOf(4)))
        val raise = plan.steps.first { it.action is NextAction.RaiseGauges }
        assertEquals(StepStatus.IN_PROGRESS, raise.status)
    }

    @Test
    fun raisingIsJudgedPerEnclosNotByOpenTab() {
        // Régression : une fertile monte dans l'enclos 2 (dragofesse active). Quand on ouvre l'onglet
        // de l'enclos 1 (autres éléments actifs), sa montée doit rester « en cours » — on juge enclos
        // par enclos, pas selon l'onglet ouvert.
        val stock = stockOf(
            mount(
                "Doré et Pourpre", Fertility.FERTILE, Sex.FEMALE,
                serenity = 3000, // bande GREEN → amour montable
                location = MountLocation.Paddock(2),
            ),
            mount("Doré et Orchidée", Fertility.FECONDE, Sex.MALE),
        )
        // Enclos 1 ouvert (élément sérénité actif), enclos 2 a la dragofesse (amour) active.
        val active = mapOf<Int?, Set<Int>>(1 to setOf(1), 2 to setOf(4))
        val plan = ReproPlanner.plan(listOf("Roux"), stock, optimakina = false, activeElementsByPaddock = active)
        val raise = plan.steps.first { it.action is NextAction.RaiseGauges }
        assertEquals(StepStatus.IN_PROGRESS, raise.status)
    }

    @Test
    fun twoSterilesAreClonedBeforeRecreating() {
        val stock = stockOf(
            mount("Doré et Pourpre", Fertility.STERILE, Sex.FEMALE),
            mount("Doré et Pourpre", Fertility.STERILE, Sex.MALE),
            mount("Doré et Orchidée", Fertility.FECONDE, Sex.MALE),
        )
        val plan = ReproPlanner.plan(listOf("Roux"), stock, optimakina = false)
        assertIs<NextAction.Clone>(firstAction(plan))
    }

    @Test
    fun loneSterileRecreatesInsteadOfCloning() {
        val stock = stockOf(
            mount("Doré et Pourpre", Fertility.STERILE, Sex.FEMALE),
            mount("Doré et Orchidée", Fertility.FECONDE, Sex.MALE),
        )
        val plan = ReproPlanner.plan(listOf("Roux"), stock, optimakina = false)
        assertIs<NextAction.Capture>(firstAction(plan))
    }

    @Test
    fun readyCrossSurfacesEvenWithLowerGenRobeRemaining() {
        // Le scénario du joueur : un Prune + un Ivoire fécondes de sexes opposés, et « Prune et Ivoire »
        // (gen 8) reste à faire. Même si une robe gen 1 reste à capturer, le croisement réalisable
        // tout de suite est dans « Prêt maintenant ».
        val stock = stockOf(
            mount("Prune", Fertility.FECONDE, Sex.FEMALE, level = 120),
            mount("Ivoire", Fertility.FECONDE, Sex.MALE, level = 130),
        )
        val plan = ReproPlanner.plan(listOf("Ébène", "Prune et Ivoire"), stock, optimakina = false)
        assertEquals(listOf("Prune et Ivoire"), readyTargets(plan))
        val cross = plan.stepsOf(StepStatus.READY).first().action as NextAction.Cross
        assertEquals("Prune", cross.mother.robe)   // la femelle
        assertEquals("Ivoire", cross.father.robe)  // le mâle
        // La gen 1 restante reste à capturer.
        assertTrue(plan.stepsOf(StepStatus.TO_PREPARE).any { (it.action as? NextAction.Capture)?.robe == "Ébène" })
    }

    @Test
    fun twoFecondesSameSexIsNotReady() {
        val stock = stockOf(
            mount("Prune", Fertility.FECONDE, Sex.MALE),
            mount("Ivoire", Fertility.FECONDE, Sex.MALE),
        )
        val plan = ReproPlanner.plan(listOf("Prune et Ivoire"), stock, optimakina = false)
        assertTrue(plan.stepsOf(StepStatus.READY).isEmpty())
        assertIs<NextAction.NeedOppositeSex>(firstAction(plan))
    }

    @Test
    fun readyCrossesAreListedHighestGenFirst() {
        val stock = stockOf(
            mount("Prune", Fertility.FECONDE, Sex.FEMALE),
            mount("Ivoire", Fertility.FECONDE, Sex.MALE),
            mount("Doré", Fertility.FECONDE, Sex.FEMALE),
            mount("Pourpre", Fertility.FECONDE, Sex.MALE),
        )
        val plan = ReproPlanner.plan(listOf("Doré et Pourpre", "Prune et Ivoire"), stock, optimakina = false)
        // Génération décroissante : « Prune et Ivoire » (gen 8) avant « Doré et Pourpre » (gen 2).
        assertEquals(listOf("Prune et Ivoire", "Doré et Pourpre"), readyTargets(plan))
    }

    @Test
    fun consumedMountIsExcludedAndShownJustBred() {
        // Prune fécond mais consommé (vient d'être accouplé) → pas de croisement prêt, listé "juste accouplé".
        val stock = stockOf(
            mount("Prune", Fertility.FECONDE, Sex.FEMALE, consumed = true),
            mount("Ivoire", Fertility.FECONDE, Sex.MALE),
        )
        val plan = ReproPlanner.plan(listOf("Prune et Ivoire"), stock, optimakina = false)
        assertTrue(plan.stepsOf(StepStatus.READY).isEmpty())
        assertEquals(listOf("Prune"), plan.justBred.map { it.robe })
    }

    @Test
    fun fullSuccessWhenNothingRemains() {
        val plan = ReproPlanner.plan(emptyList(), empty, optimakina = false)
        assertTrue(plan.fullSuccess)
    }
}
