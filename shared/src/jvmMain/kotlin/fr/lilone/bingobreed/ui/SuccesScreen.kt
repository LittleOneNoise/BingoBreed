package fr.lilone.bingobreed.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.lilone.bingobreed.sniffer.model.breeding.Achievement
import fr.lilone.bingobreed.sniffer.model.breeding.AchievementCategory
import fr.lilone.bingobreed.sniffer.model.breeding.AchievementObjective
import fr.lilone.bingobreed.sniffer.model.breeding.AchievementRegistry

/** Largeur d'une carte de succès ; les cartes s'écoulent en grille (plusieurs par ligne). */
private val CARD_WIDTH = 380.dp

/**
 * Ordre d'affichage calé sur le client Dofus : points croissants, puis succès **terminaux** avant
 * les succès **méta** (qui regroupent d'autres succès), puis id croissant. Ex. Muldo : les gén. 9-10
 * (terminales, 50 pts) précèdent « Générations aquatiques » (méta, 50 pts) malgré un id plus grand.
 */
private val AchievementOrder: Comparator<Achievement> = compareBy(
    { AchievementRegistry.points(it.id) ?: Int.MAX_VALUE },
    { AchievementRegistry.children(it.id).isNotEmpty() },
    { it.id },
)

/**
 * Onglet Succès : succès d'élevage du joueur, alimentés par les listes détaillées `lfd`
 * (cf. [fr.lilone.bingobreed.sniffer.parser.breeding.AchievementMapper]) et nommés via
 * [AchievementRegistry]. Sous-onglets par famille ([AchievementCategory]), masquage des succès
 * validés (activé par défaut). Tout est trié dans l'ordre du client Dofus ([AchievementOrder]).
 */
@Composable
fun SuccesScreen(achievements: Map<Int, Achievement>, now: Long, lastGameFrameAt: Long?) {
    if (achievements.isEmpty()) {
        EmptySucces()
        return
    }
    var category by remember { mutableStateOf(AchievementCategory.GENERAL) }
    var hideObtained by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SuccesHeader(achievements.values, now, lastGameFrameAt)
        Spacer(Modifier.height(12.dp))
        CategoryTabs(category, achievements.values, onSelect = { category = it })
        Spacer(Modifier.height(10.dp))
        HideObtainedToggle(hideObtained, onChange = { hideObtained = it })
        Spacer(Modifier.height(10.dp))

        // Succès de la famille sélectionnée, dans l'ordre du client (le thème suit le tri, sans épinglage).
        val items = achievements.values
            .filter { AchievementRegistry.category(it.id) == category }
            .filterNot { hideObtained && it.obtained }
            .sortedWith(AchievementOrder)

        if (items.isEmpty()) {
            EmptyCategory(hideObtained)
        } else {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                CardGrid(items)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardGrid(items: List<Achievement>) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { AchievementCard(it, Modifier.width(CARD_WIDTH)) }
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
private fun EmptyCategory(hideObtained: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            if (hideObtained) "Rien à afficher : tous les succès vus de cette famille sont validés.\nDécoche « Masquer les succès validés » pour les revoir."
            else "Aucun succès de cette famille capturé pour l'instant.\nOuvre la catégorie correspondante dans le jeu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuccesHeader(all: Collection<Achievement>, now: Long, lastGameFrameAt: Long?) {
    val obtained = all.count { it.obtained }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Succès d'élevage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        NetworkChip(now, lastGameFrameAt)
        Spacer(Modifier.width(12.dp))
        Text(
            "$obtained/${all.size} validés",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryTabs(selected: AchievementCategory, all: Collection<Achievement>, onSelect: (AchievementCategory) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AchievementCategory.entries.forEach { cat ->
            val inCat = all.filter { AchievementRegistry.category(it.id) == cat }
            CategoryChip(cat.label, inCat.count { it.obtained }, inCat.size, cat == selected) { onSelect(cat) }
        }
    }
}

@Composable
private fun CategoryChip(label: String, obtained: Int, total: Int, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val fg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = fg)
        // Compteur validés/total — masqué tant que rien n'est chargé pour cette famille.
        if (total > 0) {
            Spacer(Modifier.width(6.dp))
            Text("$obtained/$total", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun HideObtainedToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                // Pouce bien visible même éteint (sinon il se fond dans le gris).
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Masquer les succès validés",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { onChange(!checked) },
        )
    }
}

/**
 * Carte d'un succès, alignée à gauche et empilée. L'état validé est porté par la **bordure verte**
 * (pas de texte « Validé » ni de points). Pour un succès-**méta** (qui regroupe d'autres succès),
 * on n'affiche pas la description générique (« Obtenir les succès suivants ») : la liste des
 * objectifs nomme déjà les sous-succès. Tous les objectifs sont montrés, validés inclus.
 */
@Composable
private fun AchievementCard(a: Achievement, modifier: Modifier) {
    val total = a.objectives.size
    val accent = if (a.obtained) BreedColors.feconde else BreedColors.fertile
    val title = AchievementRegistry.name(a.id) ?: "Succès #${a.id}"
    val isMeta = AchievementRegistry.children(a.id).isNotEmpty()
    // Description seulement pour un succès terminal multi-objectifs (sinon redondante / vide de sens).
    val description = if (!isMeta && total > 1) AchievementRegistry.description(a.id) else null

    val border = if (a.obtained) BreedColors.feconde else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(if (a.obtained) 1.5.dp else 1.dp, border, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Progression : compteur + courte barre, regroupés à gauche.
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${a.completedCount}/$total",
                style = MaterialTheme.typography.labelMedium,
                color = if (a.obtained) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            ProgressBar(a.completedCount, total, accent, Modifier.width(100.dp))
        }
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Objectifs (validés + restants) en puces compactes. Pour un succès mono-objectif, la
        // description sert de repli quand l'objectif n'a pas de libellé propre (ex. #79).
        if (a.objectives.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val fallback = if (total == 1) AchievementRegistry.description(a.id) else null
            ObjectiveChips(a.objectives, fallback)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ObjectiveChips(objectives: List<AchievementObjective>, fallback: String?) {
    // Validés d'abord (ce qui est acquis se lit en un coup d'œil), puis restants.
    val sorted = objectives.sortedByDescending { it.completed }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        sorted.forEach { ObjectiveChip(it, fallback) }
    }
}

@Composable
private fun ObjectiveChip(o: AchievementObjective, fallback: String?) {
    val done = o.completed
    val label = AchievementRegistry.objectiveText(o.id) ?: fallback ?: "#${o.id}"
    // Objectif numérique en cours (cible > 1) : on montre la progression chiffrée.
    val text = if (!done && o.target > 1) "$label ${o.current}/${o.target}" else label
    val bg = if (done) BreedColors.feconde.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val fg = if (done) BreedColors.feconde else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.clip(RoundedCornerShape(5.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done) {
            Text("✓", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = fg)
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
