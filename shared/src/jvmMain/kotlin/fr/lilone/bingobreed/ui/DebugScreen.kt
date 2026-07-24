package fr.lilone.bingobreed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.lilone.bingobreed.log.LogLevel
import fr.lilone.bingobreed.log.LogMirror
import fr.lilone.bingobreed.log.LogRecord
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/* --------------------------------------------------------------------- Rendu */

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

private val Background = Color(0xFF0B0E13)
private val Dim = Color(0xFF566273)
private val Body = Color(0xFFC9D1D9)

private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.TRACE -> Color(0xFF4A5568)
    LogLevel.DEBUG -> Color(0xFF8B949E)
    LogLevel.INFO -> Color(0xFF4FC3F7)
    LogLevel.WARN -> Color(0xFFE3A008)
    LogLevel.ERROR -> Color(0xFFF85149)
}

/**
 * Abrège un nom de logger façon `%logger{24}` : dernier segment intact, les précédents réduits à
 * leur initiale (`fr.lilone.bingobreed.sniffer.SnifferEngine` -> `f.l.b.s.SnifferEngine`).
 */
private fun abbreviate(logger: String): String {
    val cut = logger.lastIndexOf('.')
    if (cut < 0) return logger
    val head = logger.substring(0, cut).split('.').joinToString(".") { it.take(1) }
    return "$head.${logger.substring(cut + 1)}"
}

/**
 * Nom de thread raccourci **par la gauche** : le début est du bruit commun
 * (`DefaultDispatcher-worker-1`), la fin porte ce qui distingue un thread d'un autre.
 */
private fun shortThread(name: String, max: Int = 18): String =
    if (name.length <= max) name else "…${name.takeLast(max - 1)}"

/* ------------------------------------------------------------------- Écran */

/**
 * Miroir de la sortie console : affiche les lignes logback captées par [LogMirror], filtrables par
 * niveau, par logger et par texte. Les stacktraces sont repliées derrière un compteur de lignes pour
 * qu'une erreur ne noie pas le flux.
 *
 * Le TRACE (frames réseau brutes) n'est visible qu'ici — les appenders console/fichier s'arrêtent à
 * DEBUG, cf. `logback.xml`.
 */
@Composable
fun DebugScreen() {
    val logs = remember { mutableStateListOf<LogRecord>() }
    var paused by remember { mutableStateOf(false) }
    var autoscroll by remember { mutableStateOf(true) }
    var levels by remember { mutableStateOf(LogLevel.entries.toSet()) }
    var loggerFilter by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }

    // Collecte par paquets : sous TRACE le débit atteint plusieurs centaines de lignes/s, et une
    // recomposition par ligne ferait ramer la liste. On draine ce qui est arrivé, on pousse d'un
    // coup, puis on souffle.
    LaunchedEffect(Unit) {
        coroutineScope {
            val incoming = Channel<LogRecord>(Channel.UNLIMITED)
            launch { LogMirror.records.collect(incoming::send) }
            while (true) {
                val batch = mutableListOf(incoming.receive())
                while (true) batch += incoming.tryReceive().getOrNull() ?: break
                if (!paused) {
                    logs.addAll(batch)
                    if (logs.size > LogMirror.CAPACITY) logs.removeRange(0, logs.size - LogMirror.CAPACITY)
                }
                delay(80)
            }
        }
    }

    val visible = logs.filter { r ->
        r.level in levels &&
            (loggerFilter.isBlank() || r.logger.contains(loggerFilter, ignoreCase = true)) &&
            (search.isBlank() || r.message.contains(search, ignoreCase = true))
    }
    val listState = rememberAutoscrolledListState(autoscroll, visible.size)

    Column(Modifier.fillMaxSize().background(Background)) {
        Toolbar(
            levels = levels, onToggleLevel = { levels = if (it in levels) levels - it else levels + it },
            loggerFilter = loggerFilter, onLoggerFilter = { loggerFilter = it },
            search = search, onSearch = { search = it },
            paused = paused, onTogglePause = { paused = !paused },
            autoscroll = autoscroll, onToggleAutoscroll = { autoscroll = !autoscroll },
            onClear = { logs.clear() },
            shown = visible.size, total = logs.size,
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp), state = listState) {
            items(visible.size) { i -> LogRow(visible[i]) }
        }
    }
}

/**
 * Liste qui suit le bas du flux tant que [autoscroll] est actif.
 *
 * [count] passe par un `rememberUpdatedState` : `snapshotFlow` n'observe que des états, un `Int`
 * capturé au lancement de l'effet resterait figé sur sa valeur initiale.
 */
@Composable
private fun rememberAutoscrolledListState(autoscroll: Boolean, count: Int): LazyListState {
    val state = rememberLazyListState()
    val latest = rememberUpdatedState(count)
    LaunchedEffect(autoscroll) {
        if (autoscroll) snapshotFlow { latest.value }.collect { n -> if (n > 0) state.scrollToItem(n - 1) }
    }
    return state
}

/* ----------------------------------------------------------------- Barre d'outils */

@Composable
private fun Toolbar(
    levels: Set<LogLevel>, onToggleLevel: (LogLevel) -> Unit,
    loggerFilter: String, onLoggerFilter: (String) -> Unit,
    search: String, onSearch: (String) -> Unit,
    paused: Boolean, onTogglePause: () -> Unit,
    autoscroll: Boolean, onToggleAutoscroll: () -> Unit,
    onClear: () -> Unit,
    shown: Int, total: Int,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LogLevel.entries.forEach { level ->
                LevelChip(level, level in levels) { onToggleLevel(level) }
            }
            Spacer(Modifier.width(4.dp))
            FilterField("logger…", loggerFilter, onLoggerFilter, 150.dp)
            FilterField("🔍 message…", search, onSearch, 190.dp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onTogglePause) { Text(if (paused) "▶ Reprendre" else "⏸ Pause") }
            TextButton(onClick = onToggleAutoscroll) { Text(if (autoscroll) "⬇ Auto" else "⬇ Manuel") }
            TextButton(onClick = onClear) { Text("🗑 Clear") }
            Text(
                "$shown/$total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Chip de niveau : teinté de sa couleur quand actif, éteint sinon. */
@Composable
private fun LevelChip(level: LogLevel, active: Boolean, onClick: () -> Unit) {
    val color = levelColor(level)
    Surface(
        color = if (active) color.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            level.name,
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * Champ de saisie compact. `OutlinedTextField` de Material 3 fait 56 dp de haut : bien trop pour une
 * barre d'outils dense, d'où le `BasicTextField` habillé à la main.
 */
@Composable
private fun FilterField(placeholder: String, value: String, onValueChange: (String) -> Unit, width: Dp) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
        Box(Modifier.width(width).height(28.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodySmall.copy(color = Body, fontFamily = FontFamily.Monospace),
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/* --------------------------------------------------------------------- Ligne */

@Composable
private fun LogRow(record: LogRecord) {
    var expanded by remember(record) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Mono(TIME_FMT.format(Instant.ofEpochMilli(record.timestampMs)), Dim, Modifier.width(92.dp))
            Mono(record.level.name, levelColor(record.level), Modifier.width(54.dp), bold = true)
            Mono(shortThread(record.thread), Dim, Modifier.width(140.dp))
            // Le nom de logger prend ce qui reste : c'est la colonne la plus utile pour se repérer,
            // et une largeur fixe la coupait en plein milieu (`…n.NetworkInterfaceD`).
            Mono(abbreviate(record.logger), Color(0xFF8B949E), Modifier.weight(1f))
        }
        Text(
            record.message,
            Modifier.padding(start = 24.dp),
            fontFamily = FontFamily.Monospace,
            color = Body,
            style = MaterialTheme.typography.bodySmall,
        )
        record.stackTrace?.let { trace ->
            val lines = trace.lineSequence().count()
            Text(
                if (expanded) "▾ replier la stacktrace" else "▸ $lines lignes de stacktrace",
                Modifier.padding(start = 24.dp).clickable { expanded = !expanded },
                fontFamily = FontFamily.Monospace,
                color = levelColor(LogLevel.ERROR).copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (expanded) {
                // Une stacktrace ne se replie pas : on la fait défiler horizontalement plutôt que
                // de la couper au milieu d'un nom de classe.
                Box(Modifier.fillMaxWidth().padding(start = 36.dp).horizontalScroll(rememberScrollState())) {
                    Text(
                        trace,
                        fontFamily = FontFamily.Monospace,
                        color = Dim,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Mono(text: String, color: Color, modifier: Modifier = Modifier, bold: Boolean = false) {
    Text(
        text,
        modifier,
        fontFamily = FontFamily.Monospace,
        color = color,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
