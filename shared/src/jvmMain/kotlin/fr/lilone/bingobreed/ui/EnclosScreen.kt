package fr.lilone.bingobreed.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.lilone.bingobreed.sniffer.model.breeding.FuelGauge
import fr.lilone.bingobreed.sniffer.model.breeding.FuelTier
import fr.lilone.bingobreed.sniffer.model.breeding.Paddock
import org.jetbrains.compose.resources.painterResource

private val FUEL_LABELS = listOf("Baffeur", "Caresseur", "Foudroyeur", "Abreuvoir", "Dragofesse", "Mangeoire")
private fun fuelLabel(element: Int): String = FUEL_LABELS.getOrElse(element) { "Élt $element" }

/** Onglet Enclos : état complet de l'enclos actif (jauges carburant + montures présentes). */
@Composable
fun EnclosScreen(paddock: Paddock?, now: Long, lastGameFrameAt: Long?) {
    if (paddock == null) {
        EmptyState()
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PaddockHeader(paddock, now, lastGameFrameAt)
            Spacer(Modifier.height(14.dp))
            FuelZone(paddock)
            Spacer(Modifier.height(18.dp))
            MountTable(paddock.mounts.values)
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
private fun PaddockHeader(paddock: Paddock, now: Long, lastGameFrameAt: Long?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            paddock.id?.let { "Enclos #$it" } ?: "Enclos actif",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        NetworkChip(now, lastGameFrameAt)
        Spacer(Modifier.width(12.dp))
        Text(
            "${paddock.mounts.size}/10 montures",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ---------------------------------------------------------------- Carburant */

@Composable
private fun FuelZone(paddock: Paddock) {
    val active = paddock.activeElements.toSet()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        paddock.fuelGauges.sortedBy { it.element }.forEach { g ->
            FuelTile(g, g.element in active, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FuelTile(gauge: FuelGauge, active: Boolean, modifier: Modifier) {
    val tier = FuelTier.of(gauge.value)
    val border = if (active) tierColor(tier) else MaterialTheme.colorScheme.outline
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(if (active) 1.5.dp else 1.dp, border.copy(alpha = if (active) 0.9f else 0.4f), RoundedCornerShape(10.dp))
            .alpha(if (active) 1f else 0.45f)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FuelGlyph(gauge.element, size = 18)
            Spacer(Modifier.width(6.dp))
            TierBadge(tier)
            Spacer(Modifier.weight(1f))
            if (active) Box(Modifier.size(6.dp).background(tierColor(tier), CircleShape))
        }
        Spacer(Modifier.height(6.dp))
        FuelBar(gauge.value, tier)
        Spacer(Modifier.height(4.dp))
        Text(
            shortK(gauge.value),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (active) {
            Text(
                "−${tier.drainPer10s}/10s · ↓${fmtDur(FuelTier.secondsToTierDrop(gauge.value))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TierBadge(tier: FuelTier) {
    Surface(color = tierColor(tier).copy(alpha = 0.18f), shape = RoundedCornerShape(5.dp)) {
        Text(
            "×${tier.multiplier}",
            color = tierColor(tier),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

/** Barre carburant segmentée : remplissage teinté par palier + séparateurs aux frontières. */
@Composable
private fun FuelBar(value: Int, tier: FuelTier) {
    val color = tierColor(tier)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Canvas(Modifier.fillMaxWidth().height(9.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, size = size, cornerRadius = r)
        val frac = (value.coerceIn(0, FuelTier.FUEL_MAX).toFloat() / FuelTier.FUEL_MAX)
        if (frac > 0f) drawRoundRect(color, size = Size(size.width * frac, size.height), cornerRadius = r)
        // séparateurs de paliers à 40 % / 70 % / 90 %
        listOf(0.40f, 0.70f, 0.90f).forEach { x ->
            drawLine(Color.Black.copy(alpha = 0.55f), Offset(size.width * x, 0f), Offset(size.width * x, size.height), strokeWidth = 1.4f)
        }
    }
}

@Composable
private fun FuelGlyph(element: Int, size: Int) {
    val res = AppIcons.fuel(element)
    if (res != null) {
        Icon(painterResource(res), fuelLabel(element), Modifier.size(size.dp), tint = fuelColor(element))
    } else {
        Text("XP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = fuelColor(element))
    }
}

private fun fmtDur(s: Int): String = when {
    s >= 3600 -> "${s / 3600}h${(s % 3600) / 60}m"
    s >= 60 -> "${s / 60}m"
    else -> "${s}s"
}
