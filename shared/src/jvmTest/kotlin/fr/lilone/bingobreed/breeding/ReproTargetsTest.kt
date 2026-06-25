package fr.lilone.bingobreed.breeding

import fr.lilone.bingobreed.sniffer.model.breeding.Achievement
import fr.lilone.bingobreed.sniffer.model.breeding.AchievementObjective
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReproTargetsTest {

    // Objectifs réels (cf. AchievementRegistry.OBJECTIVES) : 4610 = « Muldo Ebène » (gen 1),
    // 4628 = « Muldo Roux » (gen 3). 1490 est un succès de catégorie MULDO.
    private fun muldoAchievement(vararg objectives: AchievementObjective) =
        mapOf(1490 to Achievement(id = 1490, categoryId = null, objectives = objectives.toList()))

    private fun done(id: Int) = AchievementObjective(id = id, current = null, target = 1)
    private fun inProgress(id: Int, current: Long = 3) = AchievementObjective(id = id, current = current, target = 10)

    @Test
    fun hasMuldoDataOnlyWhenAMuldoAchievementIsPresent() {
        assertFalse(ReproTargets.hasMuldoData(emptyMap()))
        assertTrue(ReproTargets.hasMuldoData(muldoAchievement(done(4610))))
    }

    @Test
    fun completedObjectiveMarksRobeValidated() {
        val ach = muldoAchievement(done(4610), inProgress(4628))
        val validated = ReproTargets.validatedMuldoRobes(ach)
        assertTrue("Ébène" in validated)
        assertFalse("Roux" in validated)
    }

    @Test
    fun remainingExcludesValidatedAndIsSortedByGen() {
        val ach = muldoAchievement(done(4610), inProgress(4628))
        val remaining = ReproTargets.remainingMuldoRobes(ach)
        assertFalse("Ébène" in remaining, "robe validée ne doit pas rester")
        assertTrue("Roux" in remaining, "robe non validée doit rester")
        // Tri par génération croissante.
        val gens = remaining.map { fr.lilone.bingobreed.sniffer.model.breeding.MuldoRobes.byName(it)!!.gen }
        assertEquals(gens.sorted(), gens)
    }
}
