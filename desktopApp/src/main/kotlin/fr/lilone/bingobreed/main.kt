package fr.lilone.bingobreed

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import fr.lilone.bingobreed.sniffer.SnifferEngine
import fr.lilone.bingobreed.sniffer.diagnostics.ProtobufDiagnostics
import fr.lilone.bingobreed.sniffer.model.SnifferEvent
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
    log.info("BingoBreed démarré — initialisation du sniffer… (diagnostic={})", diagnostic)

    val sniffer = SnifferEngine()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    if (diagnostic) {
        log.info("Mode DIAGNOSTIC actif — chaque frame sera dumpée (brut + typé)")
        appScope.launch {
            sniffer.events.collect { event ->
                when (event) {
                    is SnifferEvent.ConnectionFrame ->
                        diagLog.info("\n{}", ProtobufDiagnostics.reportConnection(event.frame))
                    is SnifferEvent.GameFrame ->
                        diagLog.info("\n{}", ProtobufDiagnostics.reportRaw(event.frame))
                    else -> Unit
                }
            }
        }
    }

    // Démarrage automatique : récupération des IPs serveurs + écoute connexion.
    // Lance l'appli AVANT le jeu : tout s'enchaîne ensuite tout seul.
    sniffer.start()

    application {
        Window(
            onCloseRequest = {
                appScope.cancel()
                sniffer.stop()
                exitApplication()
            },
            title = "BingoBreed",
        ) {
            App()
        }
    }
}
