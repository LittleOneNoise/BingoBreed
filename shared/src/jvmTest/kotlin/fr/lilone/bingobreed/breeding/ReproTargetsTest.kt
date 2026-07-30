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
    fun objectiveIdOfRobeIsTheInverseOfTheObjectiveLabel() {
        assertEquals(4610, ReproTargets.objectiveIdOfRobe("Ébène"))
        assertEquals(4628, ReproTargets.objectiveIdOfRobe("Roux"))
        // Les objectifs Volkorne portent les mêmes noms courts : ils ne doivent pas polluer l'index.
        assertEquals(null, ReproTargets.objectiveIdOfRobe("Jade"))
    }

    /**
     * Une naissance valide l'objectif **côté client** : le serveur ne repousse pas la liste des succès,
     * donc sans ça la robe resterait dans `remaining` et le planificateur continuerait à la viser.
     */
    @Test
    fun localValidationRemovesTheRobeFromRemaining() {
        val ach = muldoAchievement(done(4610), inProgress(4628))
        assertTrue("Roux" in ReproTargets.remainingMuldoRobes(ach))

        val objective = ReproTargets.objectiveIdOfRobe("Roux")!!
        val patched = ach.mapValues { (_, a) -> a.withLocalValidations(setOf(objective)) }
        assertTrue("Roux" in ReproTargets.validatedMuldoRobes(patched))
        assertFalse("Roux" in ReproTargets.remainingMuldoRobes(patched))
        // La progression réseau est conservée : seul le flag local marque l'objectif comme terminé.
        val o = patched.getValue(1490).objectives.first { it.id == objective }
        assertEquals(3L, o.current)
        assertTrue(o.locallyValidated)
        assertTrue(o.completed)
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
