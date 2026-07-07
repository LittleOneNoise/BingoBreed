package fr.lilone.bingobreed.breeding

import fr.lilone.bingobreed.sniffer.model.breeding.MuldoRobes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BreedingGeneticsTest {

    private fun pOf(out: BreedingGenetics.Outcome, robe: String): Double =
        out.chances.firstOrNull { it.robe == robe }?.p ?: 0.0

    @Test
    fun childOfInvertsRecipe() {
        assertEquals("Doré et Pourpre", MuldoRobes.childOf("Doré", "Pourpre"))
        assertEquals("Doré et Pourpre", MuldoRobes.childOf("Pourpre", "Doré")) // ordre indifférent
        assertEquals("Roux", MuldoRobes.childOf("Doré et Pourpre", "Doré et Orchidée"))
        assertEquals("Doré et Ébène", MuldoRobes.childOf("Doré", "Ébène"))
        assertNull(MuldoRobes.childOf("Doré", "Doré"))              // pas de recette parents identiques
        assertNull(MuldoRobes.childOf("Doré et Pourpre", "Ébène"))  // paire sans recette (bi gen2 × mono gen1)
    }

    @Test
    fun baseWeightsFollowMonoBiRules() {
        assertEquals(9, BreedingGenetics.baseWeight("Doré"))          // mono normale
        assertEquals(9, BreedingGenetics.baseWeight("Roux"))          // mono gen 3
        assertEquals(2, BreedingGenetics.baseWeight("Doré et Pourpre")) // bicolore
        assertEquals(2, BreedingGenetics.baseWeight("Ambre"))         // mono gen 9 (exception)
        assertEquals(2, BreedingGenetics.baseWeight("Aigue-marine"))  // mono gen 9 (exception)
    }

    @Test
    fun twoMonoGen1ParentsNoGenealogy() {
        // Doré (9×5=45) × Pourpre (45). Couple 2025 → membres Doré, Pourpre + enfant "Doré et Pourpre".
        // Poids : Doré=2025, Pourpre=2025, "Doré et Pourpre"=2025. Cible = gen 2 (Doré et Pourpre).
        // Niveaux 0 → p(cible)=0.30. Cible seule → 30% ; les deux gen 1 se partagent 70%.
        val out = BreedingGenetics.compute(
            fatherRobe = "Doré", fatherParents = emptyList(), fatherLevel = 0,
            motherRobe = "Pourpre", motherParents = emptyList(), motherLevel = 0,
            optimakina = false,
        )
        assertEquals(2, out.targetGen)
        assertEquals(0.30, pOf(out, "Doré et Pourpre"), 1e-9)
        assertEquals(0.35, pOf(out, "Doré"), 1e-9)
        assertEquals(0.35, pOf(out, "Pourpre"), 1e-9)
        assertEquals(1.0, out.chances.sumOf { it.p }, 1e-9)
    }

    @Test
    fun grandparentsRaiseTargetToRoux() {
        // Père "Doré et Pourpre" (gp Doré, Pourpre) × Mère "Doré et Orchidée" (gp Doré, Orchidée).
        // Le croisement des deux bicolores gen 2 produit "Roux" (gen 3) : c'est la génération cible,
        // atteinte uniquement par la généalogie (aucun parent n'est Roux).
        val out = BreedingGenetics.compute(
            fatherRobe = "Doré et Pourpre", fatherParents = listOf("Doré", "Pourpre"), fatherLevel = 0,
            motherRobe = "Doré et Orchidée", motherParents = listOf("Doré", "Orchidée"), motherLevel = 0,
            optimakina = false,
        )
        assertEquals(3, out.targetGen)
        val roux = out.chances.firstOrNull { it.robe == "Roux" }
        assertNotNull(roux)
        assertTrue(roux.targetGen)
        assertEquals(0.30, roux.p, 1e-9)                    // seule robe à la gén. cible → tout le bonus
        // Symétries attendues du pool.
        assertEquals(pOf(out, "Doré et Pourpre"), pOf(out, "Doré et Orchidée"), 1e-9)
        assertEquals(pOf(out, "Pourpre"), pOf(out, "Orchidée"), 1e-9)
        assertEquals(1.0, out.chances.sumOf { it.p }, 1e-9)
    }

    @Test
    fun levelsAndOptimakinaRaiseTargetShare() {
        // Mêmes parents que le cas Roux, mais niveaux 200/200 + optimakina → p(cible) plafonné à 1.0,
        // donc Roux capte 100 % (seule robe cible) et les autres tombent à 0.
        val out = BreedingGenetics.compute(
            fatherRobe = "Doré et Pourpre", fatherParents = listOf("Doré", "Pourpre"), fatherLevel = 200,
            motherRobe = "Doré et Orchidée", motherParents = listOf("Doré", "Orchidée"), motherLevel = 200,
            optimakina = true,
        )
        assertEquals(1.0, pOf(out, "Roux"), 1e-9)
        assertEquals(1.0, out.chances.sumOf { it.p }, 1e-9)
    }
}
