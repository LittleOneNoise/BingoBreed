package fr.lilone.bingobreed.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.lilone.bingobreed.sniffer.model.breeding.Mount

/**
 * Onglet Étable : toutes les montures de l'étable (alimentée par le message `hhv`, exposé par
 * [fr.lilone.bingobreed.sniffer.SnifferEngine.stableMounts]). Potentiellement nombreuses, d'où le
 * scroll vertical — chaque ligne reste, elle, entièrement visible (principe de densité UI).
 */
@Composable
fun EtableScreen(stable: Map<String, Mount>, now: Long, lastGameFrameAt: Long?) {
    if (stable.isEmpty()) {
        EmptyEtable()
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            EtableHeader(stable.size, now, lastGameFrameAt)
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                MountTable(stable.values)
            }
        }
    }
}

@Composable
private fun EmptyEtable() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "En attente de l'étable…\nOuvre ton étable dans le jeu pour voir son contenu ici.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EtableHeader(count: Int, now: Long, lastGameFrameAt: Long?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Étable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        NetworkChip(now, lastGameFrameAt)
        Spacer(Modifier.width(12.dp))
        Text(
            "$count montures",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
