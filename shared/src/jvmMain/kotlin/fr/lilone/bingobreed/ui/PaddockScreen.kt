package fr.lilone.bingobreed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.lilone.bingobreed.sniffer.SnifferEngine
import fr.lilone.bingobreed.sniffer.model.VersionCheck
import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.FuelGauge
import fr.lilone.bingobreed.sniffer.model.breeding.Mount
import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
import fr.lilone.bingobreed.sniffer.model.breeding.Paddock
import fr.lilone.bingobreed.sniffer.model.breeding.Sex

/** Vert/orange utilisés pour le statut de version (indépendants du thème). */
private val StatusOk = Color(0xFF2E7D32)
private val StatusWarn = Color(0xFFE65100)

private const val FUEL_MAX = 100_000f

/** Noms des items de carburant, indexés par ordinal d'élément (enum hhc, 0..5). */
private val FUEL_LABELS = listOf("Baffeur", "Caresseur", "Foudroyeur", "Abreuvoir", "Dragofesse", "Mangeoire")

private fun fuelLabel(element: Int): String = FUEL_LABELS.getOrElse(element) { "Élt $element" }

/** Ordre d'affichage des jauges de monture : endurance, maturité, amour. */
private val MOUNT_GAUGE_ORDER = listOf(MountGauge.TYPE_ENDURANCE, MountGauge.TYPE_MATURITY, MountGauge.TYPE_LOVE)

private fun gaugeRank(type: Int): Int = MOUNT_GAUGE_ORDER.indexOf(type).takeIf { it >= 0 } ?: Int.MAX_VALUE

private fun gaugeLabel(type: Int): String = when (type) {
    MountGauge.TYPE_LOVE -> "Amour"
    MountGauge.TYPE_ENDURANCE -> "Endurance"
    MountGauge.TYPE_MATURITY -> "Maturité"
    else -> "Jauge $type"
}

/**
 * Écran racine : reflète l'enclos actif (binding direct sur [SnifferEngine.activePaddock])
 * et affiche en pied de page le contrôle d'écart de version.
 */
@Composable
fun BingoBreedApp(engine: SnifferEngine) {
    val paddock by engine.activePaddock.collectAsState()
    val versionCheck by engine.versionCheck.collectAsState()
    BingoBreedScreen(paddock, versionCheck)
}

/** Contenu pur (sans dépendance à l'engine) — facilite la preview / les tests. */
@Composable
fun BingoBreedScreen(paddock: Paddock?, versionCheck: VersionCheck?) {
    MaterialTheme {
        Scaffold(bottomBar = { VersionFooter(versionCheck) }) { inner ->
            Box(Modifier.padding(inner).fillMaxSize()) {
                if (paddock == null) EmptyState() else PaddockPanel(paddock)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "En attente d'un enclos…\nOuvre un enclos dans le jeu pour voir son état ici.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaddockPanel(paddock: Paddock) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        PaddockHeader(paddock)
        Spacer(Modifier.size(8.dp))
        FuelGaugesSection(paddock.fuelGauges)
        Spacer(Modifier.size(8.dp))
        HorizontalDivider()
        Spacer(Modifier.size(8.dp))
        Text("Montures (${paddock.mounts.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(4.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(paddock.mounts.values.toList()) { mount -> MountCard(mount) }
        }
    }
}

@Composable
private fun PaddockHeader(paddock: Paddock) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Enclos actif", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        val active = paddock.activeElements.joinToString(", ", transform = ::fuelLabel).ifBlank { "aucune" }
        Text(
            "Jauges actives : $active",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FuelGaugesSection(gauges: List<FuelGauge>) {
    Column {
        Text("Carburant", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(4.dp))
        gauges.sortedBy { it.element }.forEach { gauge ->
            GaugeRow(
                label = fuelLabel(gauge.element),
                value = gauge.value,
                max = FUEL_MAX.toInt(),
            )
        }
    }
}

/** Une ligne label + barre de progression + valeur, réutilisée carburant & jauges monture. */
@Composable
private fun GaugeRow(label: String, value: Int, max: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp))
        LinearProgressIndicator(
            progress = { if (max == 0) 0f else value.coerceIn(0, max).toFloat() / max },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text("$value/$max", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp))
    }
}

@Composable
private fun MountCard(mount: Mount) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (mount.sex == Sex.MALE) "♂" else "♀", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Text(
                    mount.name ?: mount.uuid.take(8),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text("Niv. ${mount.level}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                FertilityBadge(mount.fertility)
            }
            Spacer(Modifier.size(4.dp))
            val meta = buildString {
                append("XP ${mount.experience}")
                if (!mount.sterile) append("  ·  Sérénité ${mount.serenity}")
                append("  ·  Robe ${mount.appearanceId}")
            }
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            mount.gauges.sortedBy { gaugeRank(it.type) }.forEach { g ->
                GaugeRow(gaugeLabel(g.type), g.value, Fertility.GAUGE_MAX)
            }
            if (mount.effects.isNotEmpty()) {
                val effects = mount.effects.joinToString(", ") { e -> e.value?.let { "${e.effectId}=$it" } ?: "${e.effectId}" }
                Text("Effets : $effects", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FertilityBadge(fertility: Fertility) {
    val (text, color) = when (fertility) {
        Fertility.FECONDE -> "Féconde" to StatusOk
        Fertility.FERTILE -> "Fertile" to MaterialTheme.colorScheme.primary
        Fertility.STERILE -> "Stérile" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun VersionFooter(check: VersionCheck?) {
    val (text, color) = when (check) {
        null -> "Version client : vérification…" to MaterialTheme.colorScheme.onSurfaceVariant
        is VersionCheck.UpToDate ->
            "Client ${check.local}  ·  réf BingoBreeder ${check.reference}  —  aligné" to StatusOk
        is VersionCheck.ClientAhead ->
            "Client ${check.local} > réf ${check.reference}  —  parsing peut-être obsolète" to StatusWarn
        is VersionCheck.ClientBehind ->
            "Client ${check.local} < réf ${check.reference}" to StatusWarn
        is VersionCheck.Unknown ->
            "Client ${check.local ?: "?"}  ·  réf ${check.reference}  —  version locale illisible" to StatusWarn
    }
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
