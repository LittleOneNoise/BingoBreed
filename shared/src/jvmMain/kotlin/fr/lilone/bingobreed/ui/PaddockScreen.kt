package fr.lilone.bingobreed.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lilone.bingobreed.sniffer.SnifferEngine
import fr.lilone.bingobreed.sniffer.model.VersionCheck
import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.FuelGauge
import fr.lilone.bingobreed.sniffer.model.breeding.FuelTier
import fr.lilone.bingobreed.sniffer.model.breeding.Mount
import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
import fr.lilone.bingobreed.sniffer.model.breeding.Paddock
import fr.lilone.bingobreed.sniffer.model.breeding.Robes
import fr.lilone.bingobreed.sniffer.model.breeding.SerenityBand
import fr.lilone.bingobreed.sniffer.model.breeding.Sex
import org.jetbrains.compose.resources.painterResource

private val StatusOk = Color(0xFF2EE6A6)
private val StatusWarn = Color(0xFFE3A008)

private val FUEL_LABELS = listOf("Baffeur", "Caresseur", "Foudroyeur", "Abreuvoir", "Dragofesse", "Mangeoire")
private fun fuelLabel(element: Int): String = FUEL_LABELS.getOrElse(element) { "Élt $element" }

/** Ordre des colonnes de jauges monture : endurance, maturité, amour. */
private val MOUNT_GAUGE_ORDER = listOf(MountGauge.TYPE_ENDURANCE, MountGauge.TYPE_MATURITY, MountGauge.TYPE_LOVE)

/** Largeur fixe d'une colonne de jauge monture : barre + valeur (le détail). */
private val GAUGE_COL_WIDTH = 120.dp
private val GAUGE_BAR_WIDTH = 64.dp

private enum class Tab(val glyph: String, val label: String) { LIVE("🐴", "Live"), DEBUG("🖥", "Debug") }

/** Écran racine : coque (rail + onglets) reliée à l'engine. */
@Composable
fun BingoBreedApp(engine: SnifferEngine) {
    val paddock by engine.activePaddock.collectAsState()
    val versionCheck by engine.versionCheck.collectAsState()
    val lastGameFrameAt by engine.lastGameFrameAt.collectAsState()
    BingoBreedTheme {
        var tab by remember { mutableStateOf(Tab.LIVE) }
        // Horloge unique (tick 1 s) pour l'âge du signal (chip + overlay).
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
        val signalAge = lastGameFrameAt?.let { ((now - it) / 1000).toInt() }
        // Overlay seulement quand le signal est franchement perdu (le warning reste en chip).
        val showSignalOverlay = signalAge != null && signalAge >= NET_LOST_S
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(Modifier.fillMaxSize()) {
                AppRail(tab, versionCheck, onSelect = { tab = it })
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (tab) {
                        Tab.LIVE -> LiveScreen(paddock, versionCheck, now, lastGameFrameAt)
                        Tab.DEBUG -> DebugScreen(engine)
                    }
                    if (showSignalOverlay) SignalOverlay(signalAge!!)
                }
            }
        }
    }
}

@Composable
private fun AppRail(selected: Tab, version: VersionCheck?, onSelect: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(
            Modifier.fillMaxHeight().width(64.dp).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Tab.entries.forEach { t ->
                RailButton(t.glyph, t.label, t == selected) { onSelect(t) }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(10.dp).background(versionColor(version), CircleShape))
        }
    }
}

@Composable
private fun RailButton(glyph: String, label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick)
            .padding(vertical = 8.dp).width(52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, fontSize = 20.sp)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveScreen(paddock: Paddock?, versionCheck: VersionCheck?, now: Long, lastGameFrameAt: Long?) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (paddock == null) {
                EmptyState()
            } else {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    PaddockHeader(paddock, now, lastGameFrameAt)
                    Spacer(Modifier.height(14.dp))
                    FuelZone(paddock)
                    Spacer(Modifier.height(18.dp))
                    MountTable(paddock)
                }
            }
        }
        VersionFooter(versionCheck)
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

// Santé réseau globale. En dessous de NET_WARN_S tout va bien ; au-delà, overlay d'avertissement,
// puis "signal perdu" à partir de NET_LOST_S.
private const val NET_WARN_S = 60   // 1 min
private const val NET_LOST_S = 120  // 2 min

/** Santé réseau/sniffer, basée sur le dernier frame de jeu quelconque. */
@Composable
private fun NetworkChip(now: Long, lastGameFrameAt: Long?) {
    val age = lastGameFrameAt?.let { ((now - it) / 1000).toInt() }
    val (color, label) = when {
        age == null -> MaterialTheme.colorScheme.onSurfaceVariant to "Réseau …"
        age < NET_WARN_S -> BreedColors.feconde to "Réseau"
        age < NET_LOST_S -> StatusWarn to "Pas de signal Dofus · ${fmtSignalAge(age)} (zone calme ?)"
        else -> BreedColors.serenityRed to "Réseau perdu"
    }
    StatusChip(color, label)
}

/** Overlay plein écran (zone contenu) quand le flux réseau est franchement perdu (≥ NET_LOST_S). */
@Composable
private fun SignalOverlay(ageSec: Int) {
    val color = BreedColors.serenityRed
    Box(
        Modifier.fillMaxSize().background(Color(0xE60B0E13)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 460.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BarredSignalIcon(slash = color)
            Spacer(Modifier.height(20.dp))
            Text("Signal perdu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.height(8.dp))
            Text(
                "Toujours aucun paquet de jeu depuis ${fmtSignalAge(ageSec)}.\n" +
                    "Ça peut être normal dans une zone déserte avec tous les canaux de discussion coupés — " +
                    "sinon vérifie que le jeu tourne et que la capture réseau est active.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Âge formaté « 1 min 30s » / « 45s ». */
private fun fmtSignalAge(s: Int): String = if (s >= 60) "${s / 60} min ${s % 60}s" else "${s}s"

@Composable
private fun BarredSignalIcon(slash: Color, diameter: Dp = 76.dp) {
    val bars = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(diameter)) {
        val w = size.width
        val h = size.height
        val n = 4
        val gap = w * 0.06f
        val bw = (w - gap * (n - 1)) / n
        for (i in 0 until n) {
            val bh = h * (0.28f + 0.72f * i / (n - 1))
            drawRoundRect(
                color = bars,
                topLeft = Offset(i * (bw + gap), h - bh),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(bw * 0.25f, bw * 0.25f),
            )
        }
        drawLine(slash, Offset(0f, h), Offset(w, 0f), strokeWidth = h * 0.12f, cap = StrokeCap.Round)
    }
}

@Composable
private fun StatusChip(color: Color, label: String) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
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

/* ----------------------------------------------------------------- Montures */

@Composable
private fun MountTable(paddock: Paddock) {
    // Tri de base : sérénité croissante.
    val mounts = paddock.mounts.values.sortedBy { it.serenity }
    Column(Modifier.fillMaxWidth()) {
        MountHeaderRow()
        Spacer(Modifier.height(2.dp))
        mounts.forEachIndexed { i, m -> MountRow(m, zebra = i % 2 == 1) }
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
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun RowScope.HeaderText(text: String, width: androidx.compose.ui.unit.Dp) {
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

/* ------------------------------------------------------------------- Footer */

@Composable
private fun VersionFooter(check: VersionCheck?) {
    val (text, color) = versionText(check) to versionColor(check)
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

private fun versionColor(check: VersionCheck?): Color = when (check) {
    is VersionCheck.UpToDate -> StatusOk
    null -> Color(0xFF6E7681)
    else -> StatusWarn
}

private fun versionText(check: VersionCheck?): String = when (check) {
    null -> "Version client : vérification…"
    is VersionCheck.UpToDate -> "Client ${check.local}  ·  réf ${check.reference}  —  aligné"
    is VersionCheck.ClientAhead -> "Client ${check.local} > réf ${check.reference}  —  parsing peut-être obsolète"
    is VersionCheck.ClientBehind -> "Client ${check.local} < réf ${check.reference}"
    is VersionCheck.Unknown -> "Client ${check.local ?: "?"}  ·  réf ${check.reference}  —  version locale illisible"
}

/* -------------------------------------------------------------------- Utils */

private fun gaugeLabel(type: Int): String = when (type) {
    MountGauge.TYPE_LOVE -> "Amour"
    MountGauge.TYPE_MATURITY -> "Maturité"
    MountGauge.TYPE_ENDURANCE -> "Endurance"
    else -> "Jauge $type"
}

private fun shortK(v: Int): String = if (v >= 1000) "%.1fk".format(v / 1000f) else v.toString()

private fun signed(v: Int): String = if (v > 0) "+$v" else v.toString()

private fun fmtDur(s: Int): String = when {
    s >= 3600 -> "${s / 3600}h${(s % 3600) / 60}m"
    s >= 60 -> "${s / 60}m"
    else -> "${s}s"
}
