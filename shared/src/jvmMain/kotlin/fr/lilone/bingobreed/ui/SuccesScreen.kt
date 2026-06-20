package fr.lilone.bingobreed.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.lilone.bingobreed.sniffer.model.breeding.Achievement
import fr.lilone.bingobreed.sniffer.model.breeding.AchievementRegistry

/**
 * Onglet Succès : succès d'élevage du joueur, alimentés par les listes détaillées `lfd`
 * (cf. [fr.lilone.bingobreed.sniffer.parser.breeding.AchievementMapper]). Comme on accumule ce
 * qui défile à l'ouverture des catégories d'élevage in-game, le contenu se remplit au fur et à
 * mesure de la navigation. Faute de base id→nom, les succès sont affichés par id pour l'instant.
 */
@Composable
fun SuccesScreen(achievements: Map<Int, Achievement>, now: Long, lastGameFrameAt: Long?) {
    if (achievements.isEmpty()) {
        EmptySucces()
    } else {
        val all = achievements.values
        val obtained = all.count { it.obtained }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            SuccesHeader(all.size, obtained, now, lastGameFrameAt)
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                // Groupé par catégorie (id), succès non obtenus d'abord pour voir ce qu'il reste.
                all.groupBy { it.categoryId }
                    .toSortedMap(compareBy { it ?: Int.MAX_VALUE })
                    .forEach { (categoryId, list) ->
                        CategoryHeader(categoryId, list.count { it.obtained }, list.size)
                        Spacer(Modifier.height(2.dp))
                        list.sortedWith(compareBy({ it.obtained }, { it.id }))
                            .forEachIndexed { i, a -> AchievementRow(a, zebra = i % 2 == 1) }
                        Spacer(Modifier.height(12.dp))
                    }
            }
        }
    }
}

@Composable
private fun EmptySucces() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "En attente des succès…\nOuvre tes succès d'élevage dans le jeu (catégorie puis sous-catégories) pour les voir ici.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuccesHeader(total: Int, obtained: Int, now: Long, lastGameFrameAt: Long?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Succès d'élevage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        NetworkChip(now, lastGameFrameAt)
        Spacer(Modifier.width(12.dp))
        Text(
            "$obtained/$total obtenus",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryHeader(categoryId: Int?, obtained: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            categoryId?.let { "Catégorie #$it" } ?: "Sans catégorie",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "$obtained/$total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun AchievementRow(a: Achievement, zebra: Boolean) {
    val bg = if (zebra) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent
    val total = a.objectives.size
    val accent = if (a.obtained) BreedColors.feconde else MaterialTheme.colorScheme.onSurfaceVariant
    val title = AchievementRegistry.name(a.id) ?: "Succès #${a.id}"
    val points = AchievementRegistry.points(a.id)
    // Objectifs encore en cours (valeur courante présente) : le détail actionnable de ce qu'il reste.
    val pending = a.objectives.filter { !it.completed }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Pastille d'état (obtenu = vert plein, sinon contour)
            Box(Modifier.width(26.dp)) {
                Box(Modifier.size(12.dp).background(if (a.obtained) accent else accent.copy(alpha = 0.35f), CircleShape))
            }
            Text(
                title,
                Modifier.width(240.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (a.obtained) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.width(52.dp)) {
                points?.let { Text("$it pts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(
                "${a.completedCount}/$total",
                Modifier.width(52.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProgressBar(a.completedCount, total, accent, Modifier.weight(1f))
        }
        // Détail des objectifs en cours (libellé issu de la table + progression courante/cible).
        if (pending.isNotEmpty() && !a.obtained) {
            Spacer(Modifier.height(3.dp))
            pending.forEach { o ->
                Row(Modifier.fillMaxWidth().padding(start = 26.dp, top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        AchievementRegistry.objectiveText(o.id) ?: "Objectif #${o.id}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${o.current}/${o.target}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(value: Int, max: Int, color: Color, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Canvas(modifier.height(8.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, size = size, cornerRadius = r)
        val frac = if (max == 0) 0f else value.coerceIn(0, max).toFloat() / max
        if (frac > 0f) drawRoundRect(color, size = Size(size.width * frac, size.height), cornerRadius = r)
    }
}
