package fr.lilone.bingobreed.breeding

import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.Mount
import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
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

/** Où se trouve physiquement une monture, pour que le coach dise où aller la chercher. */
sealed interface MountLocation {
    /** Dans l'étable (réserve). */
    data object Stable : MountLocation
    /** Dans l'enclos actif (id 1..6 si connu, sinon null). */
    data class Paddock(val id: Int?) : MountLocation
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
    /** Jauges individuelles (amour/maturité/endurance), pour dire **quelle** jauge monter. */
    val gauges: List<MountGauge> = emptyList(),
    /** Localisation (étable / enclos actif), pour guider le joueur jusqu'à la monture. */
    val location: MountLocation = MountLocation.Stable,
    /** Vient d'être accouplée (`htq`) : jauges en reset côté jeu → ni féconde ni utilisable maintenant. */
    val consumed: Boolean = false,
) {
    /** Bande de sérénité (jauges actuellement montables), pour conseiller la montée. */
    val serenityBand: SerenityBand get() = SerenityBand.of(serenity)

    /** Jauges non encore au max (à monter au total, indépendamment de la sérénité). */
    fun gaugesMissing(): List<MountGauge> = gauges.filter { it.value < Fertility.GAUGE_MAX }

    /** Parmi les jauges manquantes, celles **montables tout de suite** (débloquées par la sérénité actuelle). */
    fun gaugesRaisableNow(): List<MountGauge> = gaugesMissing().filter { it.type in serenityBand.enables }
}

/** Stock du joueur regroupé par robe, avec accès rapide par état de fertilité (montures consommées exclues). */
class OwnedStock(val byRobe: Map<String, List<OwnedMount>>) {
    fun all(robe: String): List<OwnedMount> = byRobe[robe].orEmpty()
    fun fecondes(robe: String): List<OwnedMount> = all(robe).filter { it.fertility == Fertility.FECONDE && !it.consumed }
    fun fertiles(robe: String): List<OwnedMount> = all(robe).filter { it.fertility == Fertility.FERTILE && !it.consumed }
    fun steriles(robe: String): List<OwnedMount> = all(robe).filter { it.fertility == Fertility.STERILE && !it.consumed }

    /** Montures fraîchement accouplées (toutes robes), pour les afficher « en cours » dans la checklist. */
    fun justBred(): List<OwnedMount> = byRobe.values.flatten().filter { it.consumed }

    /** Une robe est « obtenable » sans repro : féconde dispo, fertile à monter, ou ≥2 stériles à cloner. */
    fun obtainable(robe: String): Boolean =
        fecondes(robe).isNotEmpty() || fertiles(robe).isNotEmpty() || steriles(robe).size >= 2

    companion object {
        /**
         * Construit le stock depuis l'étable + **tous les enclos connus**, dédupliqué par uuid, robes
         * muldo connues. Prendre tous les enclos (pas seulement l'actif) garde le planificateur stable
         * quand le joueur change d'onglet d'enclos in-game. [consumed] = montures fraîchement accouplées
         * (`htq`), exclues du calcul tant qu'un état frais ne les a pas rétablies (cf.
         * SnifferEngine.consumedMounts).
         */
        fun from(stable: Map<String, Mount>, paddocks: Iterable<Paddock>, consumed: Set<String> = emptySet()): OwnedStock {
            val merged = LinkedHashMap<String, Pair<Mount, MountLocation>>()
            stable.forEach { (uuid, m) -> merged[uuid] = m to MountLocation.Stable }
            // Les enclos (plus live) priment, et fixent la localisation « enclos N ».
            paddocks.forEach { p -> p.mounts.forEach { (uuid, m) -> merged[uuid] = m to MountLocation.Paddock(p.id) } }
            val byRobe = merged.values
                .mapNotNull { (m, loc) -> MuldoRobes.BY_ID[m.appearanceId]?.let { robe -> toOwned(m, robe.name, loc, m.uuid in consumed) } }
                .groupBy { it.robe }
            return OwnedStock(byRobe)
        }

        private fun toOwned(m: Mount, robe: String, location: MountLocation, consumed: Boolean) = OwnedMount(
            uuid = m.uuid,
            name = m.name,
            robe = robe,
            sex = m.sex,
            level = m.level,
            fertility = m.fertility,
            serenity = m.serenity,
            gaugeTotal = m.gauges.sumOf { it.value },
            gauges = m.gauges,
            location = location,
            consumed = consumed,
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

/**
 * Statut d'une étape de la checklist, par priorité d'affichage :
 *  - [READY] : un croisement complète une robe-succès **maintenant** (les 2 parents fécondes, sexes opposés) ;
 *  - [IN_PROGRESS] : action **déjà en cours** — monture dans l'enclos avec la jauge utile active, ou
 *    juste accouplée. Le worker ne la remet pas en avant, il la montre pour info ;
 *  - [TO_PREPARE] : tout ce qu'il reste à **lancer** (monter des jauges, capturer, cloner, croisement
 *    intermédiaire), parallélisable.
 */
enum class StepStatus { READY, IN_PROGRESS, TO_PREPARE }

/** Une étape de la checklist : une [action] concrète + son [status] déduit de l'état live. */
data class ReproStep(
    val action: NextAction,
    val status: StepStatus,
)

/**
 * Résultat du planificateur : une **checklist** unifiée d'étapes vers le full succès, classées par
 * statut. Remplace l'ancienne « action unique » : l'état du jeu (enclos/étable/accouplements) pilote
 * l'avancement, le joueur parallélise.
 */
data class ReproPlan(
    /** Étapes actionnables, ordonnées par statut ([StepStatus.ordinal]) puis génération. */
    val steps: List<ReproStep>,
    /** Montures fraîchement accouplées (jauges en reset) — affichées « en cours » pour info. */
    val justBred: List<OwnedMount>,
    /** Toutes les robes restant à valider, triées par génération. */
    val remainingTargets: List<String>,
    val fullSuccess: Boolean,
) {
    /** Raccourcis de regroupement pour l'UI. */
    fun stepsOf(status: StepStatus): List<ReproStep> = steps.filter { it.status == status }
}
