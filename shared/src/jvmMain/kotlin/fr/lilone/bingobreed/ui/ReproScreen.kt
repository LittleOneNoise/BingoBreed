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
import fr.lilone.bingobreed.breeding.CascadeStep
import fr.lilone.bingobreed.breeding.NextAction
import fr.lilone.bingobreed.breeding.OwnedMount
import fr.lilone.bingobreed.breeding.OwnedStock
import fr.lilone.bingobreed.breeding.ReproPlan
import fr.lilone.bingobreed.breeding.ReproPlanner
import fr.lilone.bingobreed.breeding.ReproTargets
import fr.lilone.bingobreed.sniffer.model.breeding.Achievement
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
    val stock = remember(stable, paddock) { OwnedStock.from(stable, paddock) }
    val plan = remember(remaining, stock, optimakina) { ReproPlanner.plan(remaining, stock, optimakina) }
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
            NextActionCard(plan)
            Spacer(Modifier.height(16.dp))
            RemainingRobes(remaining)
        }
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

/* ----------------------------------------------------------------- Prochaine action */

@Composable
private fun NextActionCard(plan: ReproPlan) {
    val target = plan.target ?: return
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, BreedColors.feconde.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("▶ Prochaine action", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = BreedColors.feconde)
            Spacer(Modifier.weight(1f))
            Text("Cible : ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RobeChip(target, plan.targetGen)
        }
        Spacer(Modifier.height(10.dp))
        ActionBody(plan.nextAction)
        if (plan.cascade.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            CascadeExpander(plan.cascade)
        }
    }
}

@Composable
private fun ActionBody(action: NextAction?) {
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
            Text("Monte les jauges de ", style = MaterialTheme.typography.bodyMedium)
            MountRef(action.mount)
            Spacer(Modifier.width(8.dp))
            Text(
                "— ${gaugeHint(action.mount)} (sérénité ${signedSerenity(action.mount.serenity)})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

        null -> Text("Rien à planifier.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Référence d'une monture du stock : sexe + nom + pastille de robe + niveau. */
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
        Box(Modifier.size(9.dp).background(robeColorByName(m.robe), CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(m.robe, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp))
        Text("niv ${m.level}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Pastille de robe + nom (+ génération optionnelle). */
@Composable
private fun RobeChip(robe: String, gen: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(11.dp).background(robeColorByName(robe), CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(robe, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (gen != null) {
            Spacer(Modifier.width(4.dp))
            Text("G$gen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CascadeExpander(cascade: List<CascadeStep>) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(
            "${if (open) "▼" else "▶"} Voir le chemin complet (${cascade.size} croisement${if (cascade.size > 1) "s" else ""})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { open = !open },
        )
        if (open) {
            Spacer(Modifier.height(8.dp))
            cascade.forEach { step ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("G${step.gen}", Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RobeRef(step.parentA)
                    Text(" × ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RobeRef(step.parentB)
                    Text("  →  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RobeRef(step.target, bold = true)
                }
            }
        }
    }
}

/** Robe inline (pastille + nom), gen 1 en accent or (à capturer). */
@Composable
private fun RobeRef(robe: String, bold: Boolean = false) {
    val gen1 = robe in MuldoRobes.GEN1
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(robeColorByName(robe), CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(
            robe,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = if (gen1) BreedColors.gold else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
                        Box(Modifier.size(9.dp).background(robeColorByName(robe), CircleShape))
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

private fun signedSerenity(s: Int): String = if (s > 0) "+$s" else s.toString()

private fun sexLabel(s: Sex): String = if (s == Sex.MALE) "mâle" else "femelle"

/** Jauges actuellement montables pour cette monture (selon sa bande de sérénité). */
private fun gaugeHint(m: OwnedMount): String =
    m.serenityBand.enables.joinToString(" + ") { gaugeName(it) }

private fun gaugeName(type: Int): String = when (type) {
    MountGauge.TYPE_LOVE -> "amour"
    MountGauge.TYPE_MATURITY -> "maturité"
    else -> "endurance"
}
