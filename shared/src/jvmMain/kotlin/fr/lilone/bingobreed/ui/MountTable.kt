package fr.lilone.bingobreed.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.Mount
import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
import fr.lilone.bingobreed.sniffer.model.breeding.Robes
import fr.lilone.bingobreed.sniffer.model.breeding.SerenityBand
import fr.lilone.bingobreed.sniffer.model.breeding.Sex
import org.jetbrains.compose.resources.painterResource

/** Ordre des colonnes de jauges monture : endurance, maturité, amour. */
private val MOUNT_GAUGE_ORDER = listOf(MountGauge.TYPE_ENDURANCE, MountGauge.TYPE_MATURITY, MountGauge.TYPE_LOVE)

/** Largeur fixe d'une colonne de jauge monture : barre + valeur (le détail). */
private val GAUGE_COL_WIDTH = 120.dp
private val GAUGE_BAR_WIDTH = 64.dp

/**
 * Table dense des montures (partagée enclos / étable). Tri de base : sérénité croissante.
 * Tout est visible d'un coup, sans scroll/clic/hover (cf. principe de densité UI).
 */
@Composable
internal fun MountTable(mounts: Collection<Mount>) {
    val sorted = mounts.sortedBy { it.serenity }
    Column(Modifier.fillMaxWidth()) {
        MountHeaderRow()
        Spacer(Modifier.height(2.dp))
        sorted.forEachIndexed { i, m -> MountRow(m, zebra = i % 2 == 1) }
    }
}

@Composable
private fun MountHeaderRow() {
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderText("", 26.dp)
        HeaderText("Nom", 110.dp)
        HeaderText("Robe", 140.dp)
        HeaderText("Gen", 36.dp)
        HeaderText("Niv", 36.dp)
        HeaderText("Fert", 36.dp)
        HeaderText("Sérénité", 92.dp)
        MOUNT_GAUGE_ORDER.forEach { type ->
            Row(Modifier.width(GAUGE_COL_WIDTH).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(AppIcons.gauge(type)), gaugeLabel(type), Modifier.size(14.dp), tint = gaugeColor(type))
                Spacer(Modifier.width(4.dp))
                Text(gaugeLabel(type), style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("Parents", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = c)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun RowScope.HeaderText(text: String, width: Dp) {
    Text(text, Modifier.width(width), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MountRow(mount: Mount, zebra: Boolean) {
    val band = if (mount.sterile) null else SerenityBand.of(mount.serenity)
    val bg = if (zebra) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent
    val feconde = mount.fertility == Fertility.FECONDE
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .then(if (feconde) Modifier.border(1.dp, BreedColors.feconde.copy(alpha = 0.5f), RoundedCornerShape(6.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sexe
        Box(Modifier.width(26.dp)) {
            Text(if (mount.sex == Sex.MALE) "♂" else "♀", color = sexColor(mount.sex), fontWeight = FontWeight.Bold)
        }
        // Nom (fallback « Anonyme » si null OU vide)
        val named = !mount.name.isNullOrBlank()
        Text(
            if (named) mount.name!! else "Anonyme",
            Modifier.width(110.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (named) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Robe : pastille + nom court
        Row(Modifier.width(140.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(11.dp).background(robeColor(mount.appearanceId), CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                Robes.robeName(mount.appearanceId) ?: "#${mount.appearanceId}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Génération
        Text(
            Robes.robeGeneration(mount.appearanceId)?.let { "G$it" } ?: "?",
            Modifier.width(36.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Niveau
        Text("${mount.level}", Modifier.width(36.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        // Fertilité
        Box(Modifier.width(36.dp)) {
            Box(Modifier.size(12.dp).background(fertilityColor(mount.fertility), CircleShape))
        }
        // Sérénité (smiley + valeur), masquée si stérile
        Row(Modifier.width(92.dp), verticalAlignment = Alignment.CenterVertically) {
            if (band != null) {
                SerenitySmiley(band)
                Spacer(Modifier.width(6.dp))
                Text(signed(mount.serenity), style = MaterialTheme.typography.bodySmall, color = serenityColor(band))
            } else {
                Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Jauges : illuminées si la bande de sérénité les autorise
        MOUNT_GAUGE_ORDER.forEach { type -> GaugeCell(mount, type, band) }
        // Généalogie : pastille + nom de robe (ou #id) + génération, par parent
        ParentsCell(Modifier.weight(1f), mount.parents)
    }
}

@Composable
private fun ParentsCell(modifier: Modifier, parents: List<Int>) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (parents.isEmpty()) {
            Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            parents.take(2).forEach { id ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(robeColor(id), CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${Robes.robeName(id) ?: "?"} ($id)",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Robes.robeGeneration(id)?.let {
                        Spacer(Modifier.width(5.dp))
                        Text("G$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun GaugeCell(mount: Mount, type: Int, band: SerenityBand?) {
    val enabled = band?.enables?.contains(type) == true
    Row(
        Modifier.width(GAUGE_COL_WIDTH).padding(horizontal = 6.dp).alpha(if (enabled) 1f else 0.28f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val g = mount.gauges.firstOrNull { it.type == type }
        if (mount.sterile || g == null) {
            Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            GaugeMini(g.value, Fertility.GAUGE_MAX, gaugeColor(type), Modifier.width(GAUGE_BAR_WIDTH))
            Spacer(Modifier.width(5.dp))
            Text(shortK(g.value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GaugeMini(value: Int, max: Int, color: Color, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Canvas(modifier.height(7.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, size = size, cornerRadius = r)
        val frac = if (max == 0) 0f else value.coerceIn(0, max).toFloat() / max
        if (frac > 0f) drawRoundRect(color, size = Size(size.width * frac, size.height), cornerRadius = r)
    }
}

/* -------------------------------------------------------------------- Utils */

private fun gaugeLabel(type: Int): String = when (type) {
    MountGauge.TYPE_LOVE -> "Amour"
    MountGauge.TYPE_MATURITY -> "Maturité"
    MountGauge.TYPE_ENDURANCE -> "Endurance"
    else -> "Jauge $type"
}

/** Format court « 1.2k » / « 850 » — partagé avec les jauges carburant de l'enclos. */
internal fun shortK(v: Int): String = if (v >= 1000) "%.1fk".format(v / 1000f) else v.toString()

private fun signed(v: Int): String = if (v > 0) "+$v" else v.toString()
