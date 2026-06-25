package fr.lilone.bingobreed.breeding

import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReproPlannerTest {

    private var seq = 0
    private fun mount(robe: String, fert: Fertility, sex: Sex = Sex.FEMALE, level: Int = 100, gaugeTotal: Int = 0) =
        OwnedMount("uuid-${seq++}", "M$seq", robe, sex, level, fert, serenity = 0, gaugeTotal = gaugeTotal)

    private fun stockOf(vararg mounts: OwnedMount) = OwnedStock(mounts.toList().groupBy { it.robe })

    private val empty = OwnedStock(emptyMap())

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
    fun nextActionFromEmptyStockIsACapture() {
        val plan = ReproPlanner.plan(listOf("Roux"), empty, optimakina = false)
        val next = plan.nextAction
        assertIs<NextAction.Capture>(next)
        assertTrue(next.robe in fr.lilone.bingobreed.sniffer.model.breeding.MuldoRobes.GEN1)
    }

    @Test
    fun cascadeIsPrunedAtRobesAlreadyInStock() {
        val stock = stockOf(
            mount("Doré et Pourpre", Fertility.FECONDE, Sex.FEMALE),
            mount("Doré et Orchidée", Fertility.FECONDE, Sex.MALE),
        )
        val cascade = ReproPlanner.buildCascade("Roux", stock)
        assertEquals(listOf("Roux"), cascade.map { it.target }) // les 2 parents fécondes sont élagués
    }

    @Test
    fun bothParentsFecondeGivesACrossWithProbability() {
        val stock = stockOf(
            mount("Doré et Pourpre", Fertility.FECONDE, Sex.FEMALE, level = 150),
            mount("Doré et Orchidée", Fertility.FECONDE, Sex.MALE, level = 150),
        )
        val plan = ReproPlanner.plan(listOf("Roux"), stock, optimakina = false)
        val next = plan.nextAction
        assertIs<NextAction.Cross>(next)
        assertEquals("Roux", next.target)
        assertEquals(Sex.FEMALE, next.mother.sex)
        assertEquals(Sex.MALE, next.father.sex)
        assertEquals(BreedingProbability.calcP(150, 150, false), next.pSuccess, 1e-9)
    }

    @Test
    fun fertileParentIsRaisedBeforeCrossing() {
        // Priorité : monter les jauges d'une fertile plutôt que la recréer.
        val stock = stockOf(
            mount("Doré et Pourpre", Fertility.FERTILE, Sex.FEMALE, gaugeTotal = 30000),
            mount("Doré et Orchidée", Fertility.FECONDE, Sex.MALE),
        )
        val plan = ReproPlanner.plan(listOf("Roux"), stock, optimakina = false)
        val next = plan.nextAction
        assertIs<NextAction.RaiseGauges>(next)
        assertEquals("Doré et Pourpre", next.mount.robe)
    }

    @Test
    fun twoSterilesAreClonedBeforeRecreating() {
        // Priorité : cloner ≥2 stériles identiques plutôt que refabriquer la robe.
        val stock = stockOf(
            mount("Doré et Pourpre", Fertility.STERILE, Sex.FEMALE),
            mount("Doré et Pourpre", Fertility.STERILE, Sex.MALE),
            mount("Doré et Orchidée", Fertility.FECONDE, Sex.MALE),
        )
        val plan = ReproPlanner.plan(listOf("Roux"), stock, optimakina = false)
        val next = plan.nextAction
        assertIs<NextAction.Clone>(next)
        assertEquals("Doré et Pourpre", next.robe)
    }

    @Test
    fun loneSterileRecreatesInsteadOfCloning() {
        // 1 seule stérile (clone impossible) → on recrée la robe (ici via capture des gen 1 amont).
        val stock = stockOf(
            mount("Doré et Pourpre", Fertility.STERILE, Sex.FEMALE),
            mount("Doré et Orchidée", Fertility.FECONDE, Sex.MALE),
        )
        val plan = ReproPlanner.plan(listOf("Roux"), stock, optimakina = false)
        // La prochaine action descend recréer « Doré et Pourpre » → capture d'une gen 1.
        assertIs<NextAction.Capture>(plan.nextAction)
    }

    @Test
    fun fullSuccessWhenNothingRemains() {
        val plan = ReproPlanner.plan(emptyList(), empty, optimakina = false)
        assertTrue(plan.fullSuccess)
    }
}
