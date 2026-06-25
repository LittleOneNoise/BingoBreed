package fr.lilone.bingobreed.breeding

import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.Mount
import fr.lilone.bingobreed.sniffer.model.breeding.MuldoRobes
import fr.lilone.bingobreed.sniffer.model.breeding.Paddock
import fr.lilone.bingobreed.sniffer.model.breeding.SerenityBand
import fr.lilone.bingobreed.sniffer.model.breeding.Sex

/**
 * Modèle du planificateur de reproduction (élevage 3.0). Types de domaine purs (sans Compose),
 * alimentés par l'état live du sniffer et le catalogue [MuldoRobes].
 */

/** Probabilité de réussite d'un croisement = probabilité que le poulain soit de la robe cible. */
object BreedingProbability {
    const val BASE = 30.0
    const val PER_LEVEL = 0.15   // par niveau et par parent
    const val OPTIMAKINA = 10.0

    /** p = min(1, (30 + (niveauA + niveauB)·0,15 + optimakina·10) / 100). Plafond 100 %. */
    fun calcP(levelA: Int, levelB: Int, optimakina: Boolean): Double =
        minOf(1.0, (BASE + (levelA + levelB) * PER_LEVEL + if (optimakina) OPTIMAKINA else 0.0) / 100.0)
}

/** Une monture possédée, réduite à ce dont le planificateur a besoin. */
data class OwnedMount(
    val uuid: String,
    val name: String?,
    val robe: String,          // nom canonique de robe ([MuldoRobes])
    val sex: Sex,
    val level: Int,
    val fertility: Fertility,
    val serenity: Int,
    /** Somme des jauges (amour+maturité+endurance), pour départager les fertiles « presque prêtes ». */
    val gaugeTotal: Int,
) {
    /** Bande de sérénité (jauges actuellement montables), pour conseiller la montée. */
    val serenityBand: SerenityBand get() = SerenityBand.of(serenity)
}

/** Stock du joueur regroupé par robe, avec accès rapide par état de fertilité. */
class OwnedStock(val byRobe: Map<String, List<OwnedMount>>) {
    fun all(robe: String): List<OwnedMount> = byRobe[robe].orEmpty()
    fun fecondes(robe: String): List<OwnedMount> = all(robe).filter { it.fertility == Fertility.FECONDE }
    fun fertiles(robe: String): List<OwnedMount> = all(robe).filter { it.fertility == Fertility.FERTILE }
    fun steriles(robe: String): List<OwnedMount> = all(robe).filter { it.fertility == Fertility.STERILE }

    /** Une robe est « obtenable » sans repro : féconde dispo, fertile à monter, ou ≥2 stériles à cloner. */
    fun obtainable(robe: String): Boolean =
        fecondes(robe).isNotEmpty() || fertiles(robe).isNotEmpty() || steriles(robe).size >= 2

    companion object {
        /** Construit le stock depuis l'étable + l'enclos actif, dédupliqué par uuid, robes muldo connues. */
        fun from(stable: Map<String, Mount>, paddock: Paddock?): OwnedStock {
            val merged = LinkedHashMap<String, Mount>()
            stable.forEach { (uuid, m) -> merged[uuid] = m }
            paddock?.mounts?.forEach { (uuid, m) -> merged[uuid] = m } // l'enclos (plus live) prime
            val byRobe = merged.values
                .mapNotNull { m -> MuldoRobes.BY_ID[m.appearanceId]?.let { robe -> toOwned(m, robe.name) } }
                .groupBy { it.robe }
            return OwnedStock(byRobe)
        }

        private fun toOwned(m: Mount, robe: String) = OwnedMount(
            uuid = m.uuid,
            name = m.name,
            robe = robe,
            sex = m.sex,
            level = m.level,
            fertility = m.fertility,
            serenity = m.serenity,
            gaugeTotal = m.gauges.sumOf { it.value },
        )
    }
}

/** Action concrète immédiatement exécutable, mise en avant comme « prochaine action ». */
sealed interface NextAction {
    /** Croiser deux montures prêtes (fécondes, sexes opposés) pour viser [target]. */
    data class Cross(
        val target: String,
        val targetGen: Int,
        val mother: OwnedMount,   // femelle
        val father: OwnedMount,   // mâle
        val pSuccess: Double,
    ) : NextAction

    /** Monter les jauges d'une monture fertile pour la rendre féconde. */
    data class RaiseGauges(val mount: OwnedMount) : NextAction

    /** Cloner ≥2 stériles identiques pour récupérer 1 féconde. */
    data class Clone(val robe: String, val first: OwnedMount, val second: OwnedMount) : NextAction

    /** Capturer une robe gen 1 (seules capturables). */
    data class Capture(val robe: String) : NextAction

    /** Les deux parents requis sont fécondes mais du même sexe : il faut un mâle et une femelle. */
    data class NeedOppositeSex(
        val target: String,
        val parentA: String,
        val parentB: String,
        val have: Sex,
    ) : NextAction
}

/** Une étape de la cascade théorique : croiser [parentA] × [parentB] → [target] (gen [gen]). */
data class CascadeStep(
    val target: String,
    val gen: Int,
    val parentA: String,
    val parentB: String,
)

/** Résultat du planificateur pour une espèce. */
data class ReproPlan(
    /** Robe cible mise en avant (plus petite génération restante), null si full succès. */
    val target: String?,
    val targetGen: Int?,
    /** Prochaine action concrète à faire maintenant, null si rien d'exploitable (cf. [target] null). */
    val nextAction: NextAction?,
    /** Cascade restante vers [target], en ordre topologique (générations croissantes). */
    val cascade: List<CascadeStep>,
    /** Toutes les robes restant à valider, triées par génération. */
    val remainingTargets: List<String>,
    val fullSuccess: Boolean,
)
