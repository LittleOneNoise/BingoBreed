package fr.lilone.bingobreed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.lilone.bingobreed.breeding.MountLocation
import fr.lilone.bingobreed.breeding.NextAction
import fr.lilone.bingobreed.breeding.OwnedMount
import fr.lilone.bingobreed.breeding.OwnedStock
import fr.lilone.bingobreed.breeding.ReproPlan
import fr.lilone.bingobreed.breeding.ReproPlanner
import fr.lilone.bingobreed.breeding.ReproTargets
import fr.lilone.bingobreed.breeding.StepStatus
import fr.lilone.bingobreed.sniffer.model.breeding.Achievement
import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.Mount
import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
import fr.lilone.bingobreed.sniffer.model.breeding.MuldoRobes
import fr.lilone.bingobreed.sniffer.model.breeding.Paddock
import fr.lilone.bingobreed.sniffer.model.breeding.Sex

/**
 * Onglet Repro : planificateur « coach » vers le full succès Muldo. Calcule, à partir de
 * l'avancement des succès ([ReproTargets]) et du stock de montures (étable + enclos,
 * [OwnedStock]), la **prochaine action** concrète et la **cascade** restante ([ReproPlanner]).
 * Tout est recalculé à chaque recomposition (état live du sniffer). Muldo seul en v1.
 */
@Composable
fun ReproScreen(
    stable: Map<String, Mount>,
    paddock: Paddock?,
    consumed: Set<String>,
    achievements: Map<Int, Achievement>,
    now: Long,
    lastGameFrameAt: Long?,
) {
    if (!ReproTargets.hasMuldoData(achievements)) {
        EmptyRepro()
        return
    }
    var optimakina by remember { mutableStateOf(false) }

    // Plan recalculé **uniquement** quand les données réelles changent (succès, stock, toggle) — pas
    // à chaque tick d'horloge du chip réseau. Les StateFlows du sniffer poussant de nouvelles
    // références à chaque état d'enclos, ceci équivaut à un recalcul événementiel (≈ chaque push).
    val remaining = remember(achievements) { ReproTargets.remainingMuldoRobes(achievements) }
    val stock = remember(stable, paddock, consumed) { OwnedStock.from(stable, paddock, consumed) }
    val activeElements = paddock?.activeElements?.toSet() ?: emptySet()
    val plan = remember(remaining, stock, optimakina, activeElements) {
        ReproPlanner.plan(remaining, stock, optimakina, activeElements)
    }
    val total = MuldoRobes.ALL.size

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ReproHeader(remaining.size, total, now, lastGameFrameAt)
        Spacer(Modifier.height(12.dp))
        SpeciesTabs()
        Spacer(Modifier.height(10.dp))
        OptimakinaToggle(optimakina, onChange = { optimakina = it })
        Spacer(Modifier.height(12.dp))

        if (plan.fullSuccess) {
            FullSuccess()
            return@Column
        }

        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            Checklist(plan)
            Spacer(Modifier.height(16.dp))
            RemainingRobes(remaining)
        }
    }
}

/* ------------------------------------------------------------------ Checklist (3 statuts) */

/**
 * Checklist du coach : 3 groupes par statut — **Prêt maintenant** (croisements complétant une robe),
 * **En cours** (montées dans l'enclos + montures juste accouplées), **À préparer** (à lancer,
 * parallélisable). L'état live fait avancer chaque étape d'un groupe à l'autre, sans action manuelle.
 */
@Composable
private fun Checklist(plan: ReproPlan) {
    val ready = plan.stepsOf(StepStatus.READY)
    val inProgress = plan.stepsOf(StepStatus.IN_PROGRESS)
    val toPrepare = plan.stepsOf(StepStatus.TO_PREPARE)

    if (ready.isNotEmpty()) {
        StepSection("✅ Prêt maintenant", ready.size, BreedColors.feconde) {
            ready.forEach { StepRow(it.action) }
        }
    }
    if (inProgress.isNotEmpty() || plan.justBred.isNotEmpty()) {
        if (ready.isNotEmpty()) Spacer(Modifier.height(16.dp))
        StepSection("⏳ En cours", inProgress.size + plan.justBred.size, BreedColors.gold) {
            inProgress.forEach { StepRow(it.action) }
            plan.justBred.forEach { JustBredRow(it) }
        }
    }
    if (toPrepare.isNotEmpty()) {
        if (ready.isNotEmpty() || inProgress.isNotEmpty() || plan.justBred.isNotEmpty()) Spacer(Modifier.height(16.dp))
        StepSection("🛠 À préparer", toPrepare.size, MaterialTheme.colorScheme.primary) {
            toPrepare.forEach { StepRow(it.action) }
        }
    }
}

@Composable
private fun StepSection(title: String, count: Int, accent: androidx.compose.ui.graphics.Color, body: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(
            "$title ($count)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Spacer(Modifier.height(8.dp))
        body()
    }
}

/** Une ligne de checklist : rend l'action selon son type. */
@Composable
private fun StepRow(action: NextAction) {
    Box(Modifier.padding(vertical = 4.dp)) { ActionBody(action) }
}

/** Monture juste accouplée : feedback « en cours, jauges en reset ». */
@Composable
private fun JustBredRow(m: OwnedMount) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        MountRef(m)
        Spacer(Modifier.width(8.dp))
        Text(
            "vient d'être accouplée — jauges en reset",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReproHeader(remaining: Int, total: Int, now: Long, lastGameFrameAt: Long?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Reproduction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        NetworkChip(now, lastGameFrameAt)
        Spacer(Modifier.width(12.dp))
        Text(
            "$remaining robes restantes / $total",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Sélecteur d'espèce : Muldo actif, les autres « bientôt » (grisées). */
@Composable
private fun SpeciesTabs() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpeciesChip("Muldo", active = true, enabled = true)
        SpeciesChip("Dragodinde", active = false, enabled = false)
        SpeciesChip("Volkorne", active = false, enabled = false)
    }
}

@Composable
private fun SpeciesChip(label: String, active: Boolean, enabled: Boolean) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val fg = when {
        active -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = fg)
        if (!enabled) {
            Spacer(Modifier.width(6.dp))
            Text("bientôt", style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

@Composable
private fun OptimakinaToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Utiliser une Optimakina (+10 % de réussite par croisement)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { onChange(!checked) },
        )
    }
}

/* ----------------------------------------------------------------- Rendu d'une action */

@Composable
private fun ActionBody(action: NextAction) {
    when (action) {
        is NextAction.Cross -> Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Croise ", style = MaterialTheme.typography.bodyMedium)
                MountRef(action.mother)
                Text("  avec  ", style = MaterialTheme.typography.bodyMedium)
                MountRef(action.father)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("→ vise ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RobeChip(action.target, action.targetGen)
                Spacer(Modifier.width(10.dp))
                Text(
                    "réussite ≈ ${pct(action.pSuccess)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = successColor(action.pSuccess),
                )
            }
        }

        is NextAction.RaiseGauges -> Row(verticalAlignment = Alignment.CenterVertically) {
            val m = action.mount
            val raisable = m.gaugesRaisableNow()
            if (raisable.isNotEmpty()) {
                Text("Monte ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    raisable.joinToString(" + ") { "${gaugeName(it.type)} ${shortK(it.value)}/${shortK(Fertility.GAUGE_MAX)}" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(" de ", style = MaterialTheme.typography.bodyMedium)
                MountRef(m)
            } else {
                // Les jauges manquantes sont bloquées par la sérénité actuelle → l'ajuster d'abord.
                Text("Ajuste la sérénité de ", style = MaterialTheme.typography.bodyMedium)
                MountRef(m)
                Spacer(Modifier.width(6.dp))
                Text(
                    "pour monter ${m.gaugesMissing().joinToString(" + ") { gaugeName(it.type) }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is NextAction.Clone -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Clone tes 2 ", style = MaterialTheme.typography.bodyMedium)
            RobeChip(action.robe, MuldoRobes.byName(action.robe)?.gen)
            Text(" stériles → 1 féconde", style = MaterialTheme.typography.bodyMedium)
        }

        is NextAction.Capture -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Capture une Muldo ", style = MaterialTheme.typography.bodyMedium)
            RobeChip(action.robe, 1)
            Text("  (gen 1, seules capturables)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        is NextAction.NeedOppositeSex -> Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RobeChip(action.parentA, MuldoRobes.byName(action.parentA)?.gen)
                Text("  et  ", style = MaterialTheme.typography.bodyMedium)
                RobeChip(action.parentB, MuldoRobes.byName(action.parentB)?.gen)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Tes fécondes sont toutes du même sexe (${sexLabel(action.have)}) : il te faut un mâle ET une femelle pour viser ${action.target}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Référence d'une monture du stock : sexe + nom + vignette de robe + niveau + sérénité + localisation. */
@Composable
private fun MountRef(m: OwnedMount) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (m.sex == Sex.MALE) "♂" else "♀", color = sexColor(m.sex), fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(5.dp))
        Text(
            if (!m.name.isNullOrBlank()) m.name else "Anonyme",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        RobeMark(m.robe, 18.dp)
        Spacer(Modifier.width(4.dp))
        Text(m.robe, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp))
        Text("niv ${m.level}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        // Sérénité (signée + smiley de bande) : aide à retrouver la monture (l'étable se trie par sérénité).
        SerenitySmiley(m.serenityBand, diameter = 13.dp)
        Spacer(Modifier.width(3.dp))
        Text(
            signedSerenity(m.serenity),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = serenityColor(m.serenityBand),
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "· ${locationLabel(m.location)}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = locationColor(m.location),
            maxLines = 1,
        )
    }
}

/** Libellé court de localisation d'une monture (où aller la chercher). */
private fun locationLabel(loc: MountLocation): String = when (loc) {
    is MountLocation.Stable -> "Étable"
    is MountLocation.Paddock -> loc.id?.let { "Enclos $it" } ?: "Enclos actif"
}

@Composable
private fun locationColor(loc: MountLocation): androidx.compose.ui.graphics.Color = when (loc) {
    is MountLocation.Stable -> MaterialTheme.colorScheme.onSurfaceVariant
    is MountLocation.Paddock -> BreedColors.fence
}

/** Pastille de robe + nom (+ génération optionnelle). */
@Composable
private fun RobeChip(robe: String, gen: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RobeMark(robe, 20.dp)
        Spacer(Modifier.width(5.dp))
        Text(robe, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (gen != null) {
            Spacer(Modifier.width(4.dp))
            Text("G$gen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ------------------------------------------------------------------ Robes restantes */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemainingRobes(remaining: List<String>) {
    if (remaining.isEmpty()) return
    Text("Robes restantes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    val byGen = remaining.groupBy { MuldoRobes.byName(it)?.gen ?: 0 }.toSortedMap()
    byGen.forEach { (gen, robes) ->
        Row(Modifier.padding(vertical = 4.dp)) {
            Text("Gen $gen", Modifier.width(54.dp).padding(top = 3.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                robes.forEach { robe ->
                    Row(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RobeMark(robe, 18.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(robe, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/* ----------------------------------------------------------------------- États / utils */

@Composable
private fun EmptyRepro() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "En attente des succès Muldo…\nOuvre tes succès d'élevage Muldo dans le jeu pour que le planificateur sache quelles robes te manquent.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FullSuccess() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "🏆 Full succès Muldo atteint !\nToutes les robes connues ont été validées.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BreedColors.feconde,
        )
    }
}

private fun pct(p: Double): String = "${(p * 100).toInt()} %"

@Composable
private fun successColor(p: Double) = when {
    p >= 0.80 -> BreedColors.feconde
    p >= 0.50 -> BreedColors.gold
    else -> BreedColors.serenityRed
}

private fun sexLabel(s: Sex): String = if (s == Sex.MALE) "mâle" else "femelle"

private fun signedSerenity(s: Int): String = if (s > 0) "+$s" else s.toString()

private fun gaugeName(type: Int): String = when (type) {
    MountGauge.TYPE_LOVE -> "amour"
    MountGauge.TYPE_MATURITY -> "maturité"
    else -> "endurance"
}
