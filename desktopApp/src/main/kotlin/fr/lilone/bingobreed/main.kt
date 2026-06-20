package fr.lilone.bingobreed

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import fr.lilone.bingobreed.sniffer.SnifferEngine
import fr.lilone.bingobreed.sniffer.diagnostics.CodeCensus
import fr.lilone.bingobreed.sniffer.diagnostics.ProtobufDiagnostics
import fr.lilone.bingobreed.sniffer.model.SnifferEvent
import fr.lilone.bingobreed.ui.BingoBreedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("fr.lilone.bingobreed.Main")
private val diagLog = LoggerFactory.getLogger("fr.lilone.bingobreed.diagnostic")

fun main() {
    // Mode diagnostic : dump des frames protobuf (brut + typé) pour vérifier la
    // cohérence des .proto avec les octets reçus. Activé via -Pdiagnostic (Gradle)
    // ou -Dbingobreed.diagnostic=true.
    val diagnostic = System.getProperty("bingobreed.diagnostic") == "true"
    // protobuf-java (TextFormat) loggue en java.util.logging des "Invalid key for map field"
    // au tri des clés de map d'un DynamicMessage : bruit inoffensif, on le coupe.
    java.util.logging.Logger.getLogger("com.google.protobuf").level = java.util.logging.Level.WARNING
    log.info("BingoBreed démarré — initialisation du sniffer… (diagnostic={})", diagnostic)

    val sniffer = SnifferEngine()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val census = CodeCensus()

    if (diagnostic) {
        log.info("Mode DIAGNOSTIC actif — structure décodée + détection des nouveaux codes")
        appScope.launch {
            sniffer.events.collect { event ->
                when (event) {
                    is SnifferEvent.ConnectionFrame ->
                        diagLog.info("\n{}", ProtobufDiagnostics.reportConnection(event.frame))
                    is SnifferEvent.GameMessage -> {
                        val isNew = census.record(event.message.typeUrl)
                        val report = ProtobufDiagnostics.reportGameMessage(event)
                        // Nouveaux codes en évidence (INFO), répétitions en DEBUG.
                        if (isNew) diagLog.info("\n🆕 NOUVEAU CODE\n{}", report)
                        else diagLog.debug("\n{}", report)
                    }
                    else -> Unit
                }
            }
        }
    }

    // Démarrage automatique : récupération des IPs serveurs + écoute connexion.
    // Lance l'appli AVANT le jeu : tout s'enchaîne ensuite tout seul.
    sniffer.start()

    application {
        val windowState = rememberWindowState(
            width = 1400.dp,
            height = 900.dp,
            position = WindowPosition(Alignment.Center),
        )
        Window(
            onCloseRequest = {
                if (diagnostic) {
                    diagLog.info("Récap des codes vus ({}):\n{}", census.distinctCount, census.summary())
                }
                appScope.cancel()
                sniffer.stop()
                exitApplication()
            },
            state = windowState,
            title = "BingoBreed",
        ) {
            BingoBreedApp(sniffer)
        }
    }
}
