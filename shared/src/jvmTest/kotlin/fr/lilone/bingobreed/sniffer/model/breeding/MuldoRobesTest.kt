package fr.lilone.bingobreed.sniffer.model.breeding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MuldoRobesTest {

    @Test
    fun gen1HasFiveCapturableRobesWithoutRecipe() {
        assertEquals(5, MuldoRobes.GEN1.size)
        MuldoRobes.GEN1.forEach { name ->
            val r = MuldoRobes.byName(name)
            assertNotNull(r, "gen1 robe $name absente du catalogue")
            assertEquals(1, r.gen)
            assertNull(r.recipe, "une robe gen 1 ne doit pas avoir de recette : $name")
        }
    }

    @Test
    fun catalogMatchesTheKnownMuldoCensus() {
        // Recensement officiel (spec) : 120 robes, réparties par génération 5/10/2/11/2/15/2/19/4/50.
        assertEquals(120, MuldoRobes.ALL.size, "le catalogue muldo doit compter 120 robes")
        val expectedByGen = mapOf(1 to 5, 2 to 10, 3 to 2, 4 to 11, 5 to 2, 6 to 15, 7 to 2, 8 to 19, 9 to 4, 10 to 50)
        val actualByGen = MuldoRobes.ALL.groupingBy { it.gen }.eachCount()
        assertEquals(expectedByGen, actualByGen, "répartition par génération inattendue")
    }

    @Test
    fun everyNonGen1RobeHasAValidRecipe() {
        MuldoRobes.ALL.filter { it.gen >= 2 }.forEach { robe ->
            val recipe = robe.recipe
            assertNotNull(recipe, "recette manquante pour ${robe.name} (gen ${robe.gen})")
            val (a, b) = recipe
            assertNotNull(MuldoRobes.byName(a), "parent inconnu '$a' dans la recette de ${robe.name}")
            assertNotNull(MuldoRobes.byName(b), "parent inconnu '$b' dans la recette de ${robe.name}")
        }
    }

    @Test
    fun dagIsAcyclicByStrictlyDecreasingGenerations() {
        // Tout parent doit être d'une génération strictement inférieure à la cible → DAG acyclique.
        MuldoRobes.ALL.mapNotNull { robe -> robe.recipe?.let { robe to it } }.forEach { (robe, recipe) ->
            val (a, b) = recipe
            assertTrue(MuldoRobes.byName(a)!!.gen < robe.gen, "${a} (parent) doit être < gen ${robe.gen} (${robe.name})")
            assertTrue(MuldoRobes.byName(b)!!.gen < robe.gen, "${b} (parent) doit être < gen ${robe.gen} (${robe.name})")
        }
    }

    @Test
    fun resolveIsAccentCaseAndOrderInsensitive() {
        assertEquals("Ébène", MuldoRobes.resolve("Ebène")?.name)
        assertEquals("Émeraude", MuldoRobes.resolve("emeraude")?.name)
        assertEquals("Doré et Pourpre", MuldoRobes.resolve("doré et pourpre")?.name)
        assertEquals("Doré et Pourpre", MuldoRobes.resolve("Pourpre et Doré")?.name)
        assertNull(MuldoRobes.resolve("Deuxième génération"))
    }

    @Test
    fun parityWithLegacyRobesTableForKnownIds() {
        Robes.IDS.forEach { (id, name) ->
            val r = MuldoRobes.BY_ID[id]
            assertNotNull(r, "id $id ($name) absent de MuldoRobes.BY_ID")
            assertEquals(name, r.name)
            assertEquals(Robes.robeGeneration(id), r.gen)
        }
    }
}
