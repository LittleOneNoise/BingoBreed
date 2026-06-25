package fr.lilone.bingobreed.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lilone.bingobreed.shared.generated.resources.Res
import fr.lilone.bingobreed.shared.generated.resources.enclos
import fr.lilone.bingobreed.shared.generated.resources.succes
import fr.lilone.bingobreed.sniffer.SnifferEngine
import fr.lilone.bingobreed.sniffer.model.VersionCheck
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val StatusOk = Color(0xFF2EE6A6)
private val StatusWarn = Color(0xFFE3A008)

/**
 * Onglets du rail. [icon] (drawable monochrome teinté) si fourni, sinon [glyph] (emoji).
 * [tint] force une couleur d'icône fixe (indépendante de la sélection) ; sinon l'icône suit
 * l'état actif/inactif.
 */
private enum class Tab(val label: String, val icon: DrawableResource?, val glyph: String, val tint: Color? = null) {
    ENCLOS("Enclos", Res.drawable.enclos, "🚧", tint = BreedColors.fence),
    ETABLE("Étable", null, "🐴"),
    SUCCES("Succès", Res.drawable.succes, "🏆", tint = BreedColors.gold),
    REPRO("Repro", null, "🧬"),
    DEBUG("Debug", null, "🖥"),
}

/** Écran racine : coque (rail + onglets + footer version) reliée à l'engine. */
@Composable
fun BingoBreedApp(engine: SnifferEngine) {
    val paddock by engine.activePaddock.collectAsState()
    val stable by engine.stableMounts.collectAsState()
    val achievements by engine.achievements.collectAsState()
    val versionCheck by engine.versionCheck.collectAsState()
    val lastGameFrameAt by engine.lastGameFrameAt.collectAsState()
    BingoBreedTheme {
        var tab by remember { mutableStateOf(Tab.ENCLOS) }
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
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            Tab.ENCLOS -> EnclosScreen(paddock, now, lastGameFrameAt)
                            Tab.ETABLE -> EtableScreen(stable, now, lastGameFrameAt)
                            Tab.SUCCES -> SuccesScreen(achievements, now, lastGameFrameAt)
                            Tab.REPRO -> ReproScreen(stable, paddock, achievements, now, lastGameFrameAt)
                            Tab.DEBUG -> DebugScreen(engine)
                        }
                        if (showSignalOverlay) SignalOverlay(signalAge!!)
                    }
                    VersionFooter(versionCheck)
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
                RailButton(t, t == selected) { onSelect(t) }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(10.dp).background(versionColor(version), CircleShape))
        }
    }
}

@Composable
private fun RailButton(tab: Tab, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
    val fg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick)
            .padding(vertical = 8.dp).width(52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (tab.icon != null) {
            Icon(painterResource(tab.icon), tab.label, Modifier.size(22.dp), tint = tab.tint ?: fg)
        } else {
            Text(tab.glyph, fontSize = 20.sp)
        }
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

/* ------------------------------------------------------------- Santé réseau */

// Santé réseau globale. En dessous de NET_WARN_S tout va bien ; au-delà, overlay d'avertissement,
// puis "signal perdu" à partir de NET_LOST_S.
private const val NET_WARN_S = 60   // 1 min
private const val NET_LOST_S = 120  // 2 min

/** Santé réseau/sniffer, basée sur le dernier frame de jeu quelconque. Partagé par les onglets. */
@Composable
internal fun NetworkChip(now: Long, lastGameFrameAt: Long?) {
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
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw * 0.25f, bw * 0.25f),
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
