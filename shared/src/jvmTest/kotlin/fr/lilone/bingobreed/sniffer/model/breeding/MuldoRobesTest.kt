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
    fun everyCataloguedRobeHasANetworkId() {
        // Régression : un id manquant fait disparaître la monture du planificateur **et** de la
        // validation des succès (cas vécu avec les gen 9/10, invisibles malgré une étable pleine).
        val orphans = MuldoRobes.ALL.filter { it.networkId == null }.map { it.name }
        assertEquals(emptyList(), orphans, "robes sans id d'apparence : elles seraient ignorées partout")
    }

    @Test
    fun deducesTheChildRobeWhenItIsTheOnlyOutcomeWithoutAKnownId() {
        // Cas vécu au moment où les gen 10 n'avaient pas encore d'id : croiser Corail × Amande ne peut
        // donner que des robes déjà identifiées (arbres des parents) ou « Corail et Amande » — donc un
        // id d'apparence non résolu ne peut désigner que celle-là.
        val corailTree = setOf("Corail", "Prune et Pourpre", "Prune et Roux")
        val amandeTree = setOf("Amande", "Indigo et Pourpre", "Ébène et Orchidée")
        assertTrue("Corail et Amande" in MuldoRobes.possibleChildren(corailTree, amandeTree))
        assertEquals(
            "Corail et Amande",
            MuldoRobes.deduceUnknownChild(corailTree, amandeTree) { it != "Corail et Amande" },
        )
    }

    @Test
    fun refusesToDeduceWhenSeveralOutcomesLackAKnownId() {
        // Deux issues sans id connu : deviner validerait un succès à tort → on renonce.
        val a = setOf("Ambre et Doré", "Ambre", "Doré")
        val b = setOf("Corail et Doré", "Corail", "Doré")
        assertNull(MuldoRobes.deduceUnknownChild(a, b) { it !in setOf("Ambre et Doré", "Corail et Doré") })
    }

    @Test
    fun deducesNothingWhenTheCatalogueAlreadyIdentifiesEveryOutcome() {
        // Table complète (cf. everyCataloguedRobeHasANetworkId) ⇒ le chemin de déduction reste inerte.
        val corailTree = setOf("Corail", "Prune et Pourpre", "Prune et Roux")
        val amandeTree = setOf("Amande", "Indigo et Pourpre", "Ébène et Orchidée")
        assertNull(MuldoRobes.deduceUnknownChild(corailTree, amandeTree))
    }

    @Test
    fun deducedIdsBecomeResolvableEverywhere() {
        // L'apprentissage doit profiter à tous les lecteurs (planificateur, succès, UI), pas juste à
        // l'appelant : `byId` interroge la table statique **et** les ids appris.
        // Robe propre à ce test : l'apprentissage est un état **global de session**, deux tests qui
        // apprendraient la même robe se pollueraient (`hasKnownId` deviendrait vrai pour l'autre).
        val unusedId = 30_001
        assertNull(MuldoRobes.byId(unusedId))
        assertTrue(Robes.learnRobeId(unusedId, "Azur et Prune"))
        assertEquals("Azur et Prune", MuldoRobes.byId(unusedId)?.name)
        assertEquals("Azur et Prune", Robes.robeName(unusedId))
        assertTrue(MuldoRobes.hasKnownId("Azur et Prune"))
        assertEquals(false, Robes.learnRobeId(unusedId, "Azur et Prune"), "2e apprentissage = pas une découverte")
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
