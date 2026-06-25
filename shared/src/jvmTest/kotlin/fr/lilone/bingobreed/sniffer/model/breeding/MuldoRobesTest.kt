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
