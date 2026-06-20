package fr.lilone.bingobreed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.lilone.bingobreed.sniffer.SnifferEngine
import fr.lilone.bingobreed.sniffer.model.SnifferEvent
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class LogCat(val label: String, val color: Color) {
    INFO("Info", Color(0xFF8B949E)),
    FRAME("Frame", Color(0xFFB1BAC4)),
    GAMEMSG("GameMsg", Color(0xFF4FC3F7)),
    PADDOCK("Paddock", Color(0xFF2EE6A6)),
    FAILURE("Failure", Color(0xFFF85149)),
}

private data class LogLine(val time: String, val cat: LogCat, val tag: String, val msg: String)

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

private fun lineOf(e: SnifferEvent): LogLine {
    val now = LocalTime.now().format(TIME_FMT)
    return when (e) {
        is SnifferEvent.InterfaceSelected -> LogLine(now, LogCat.INFO, "INTERFACE", "${e.name} · ${e.localIp}")
        is SnifferEvent.VersionChecked -> LogLine(now, LogCat.INFO, "VERSION", e.check.toString())
        is SnifferEvent.ConnectionEndpointsResolved -> LogLine(now, LogCat.INFO, "ENDPOINTS", "${e.endpoints.size} endpoint(s) connexion")
        is SnifferEvent.GameServerDetected -> LogLine(now, LogCat.INFO, "GAMESERVER", "${e.host} (${e.endpoints.size} ep)")
        is SnifferEvent.ConnectionFrame -> LogLine(now, LogCat.FRAME, "CONN", "frame ${e.frame.size}b")
        is SnifferEvent.GameFrame -> LogLine(now, LogCat.FRAME, "GAME", "${e.host} · frame ${e.frame.size}b")
        is SnifferEvent.GameMessage -> {
            val m = e.message
            val resolved = if (e.dynamic != null) "✓" else "·"
            LogLine(now, LogCat.GAMEMSG, "MSG", "${m.code} ${m.knownName ?: ""} (${m.value.size}b) $resolved")
        }
        is SnifferEvent.PaddockUpdated -> LogLine(now, LogCat.PADDOCK, "PADDOCK", "${e.host} · ${e.paddock.mounts.size} montures")
        is SnifferEvent.Failure -> LogLine(now, LogCat.FAILURE, "FAIL", "${e.context}: ${e.cause.message ?: e.cause::class.simpleName}")
    }
}

@Composable
fun DebugScreen(engine: SnifferEngine) {
    val logs = remember { mutableStateListOf<LogLine>() }
    var paused by remember { mutableStateOf(false) }
    var autoscroll by remember { mutableStateOf(true) }
    var enabled by remember { mutableStateOf(LogCat.entries.toSet()) }

    LaunchedEffect(Unit) {
        engine.events.collect { e ->
            if (!paused) {
                logs.add(lineOf(e))
                if (logs.size > 1000) logs.removeRange(0, logs.size - 1000)
            }
        }
    }

    val visible = logs.filter { it.cat in enabled }
    val listState = rememberLazyListState()
    LaunchedEffect(autoscroll) {
        if (autoscroll) {
            snapshotFlow { visible.size }.collect { n -> if (n > 0) listState.scrollToItem(n - 1) }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0E13))) {
        // Barre d'outils
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogCat.entries.forEach { cat ->
                    FilterChip(
                        selected = cat in enabled,
                        onClick = { enabled = if (cat in enabled) enabled - cat else enabled + cat },
                        label = { Text(cat.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedLabelColor = cat.color),
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { paused = !paused }) { Text(if (paused) "▶ Reprendre" else "⏸ Pause") }
                TextButton(onClick = { autoscroll = !autoscroll }) { Text(if (autoscroll) "⬇ Auto" else "⬇ Manuel") }
                TextButton(onClick = { logs.clear() }) { Text("🗑 Clear") }
                Spacer(Modifier.weight(1f))
                Text("${visible.size}/${logs.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Flux
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp), state = listState) {
            items(visible.size) { i -> LogRow(visible[i]) }
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Mono(line.time, Color(0xFF566273), 92.dp)
        Mono(line.tag, line.cat.color, 90.dp, bold = true)
        Text(line.msg, fontFamily = FontFamily.Monospace, color = Color(0xFFC9D1D9), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Mono(text: String, color: Color, width: androidx.compose.ui.unit.Dp, bold: Boolean = false) {
    Text(
        text,
        Modifier.width(width),
        fontFamily = FontFamily.Monospace,
        color = color,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        style = MaterialTheme.typography.bodySmall,
    )
}
