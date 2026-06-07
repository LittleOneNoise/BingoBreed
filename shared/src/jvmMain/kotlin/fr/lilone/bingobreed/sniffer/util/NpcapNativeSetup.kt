package fr.lilone.bingobreed.sniffer.util

import java.io.File

/**
 * Npcap (Windows) installe ses DLL natives dans `C:\Windows\System32\Npcap\`,
 * un dossier que JNA ne scanne pas par défaut. Sans l'option d'install
 * "WinPcap API-compatible Mode", `wpcap.dll` n'est donc pas trouvable et pcap4j
 * échoue avec "Unable to load library 'wpcap'".
 *
 * On ajoute ce dossier à `jna.library.path` AVANT tout chargement natif.
 * À appeler tôt (idempotent, no-op hors Windows).
 */
object NpcapNativeSetup {
    private val log = logger()
    private const val JNA_PROP = "jna.library.path"

    fun ensureOnLibraryPath() {
        val os = System.getProperty("os.name").orEmpty()
        if (!os.startsWith("Windows")) return

        val systemRoot = System.getenv("SystemRoot") ?: "C:\\Windows"
        val npcapDir = "$systemRoot\\System32\\Npcap"
        if (!File(npcapDir).isDirectory) {
            log.warn("Dossier Npcap introuvable: {} — Npcap est-il installé ?", npcapDir)
            return
        }

        val current = System.getProperty(JNA_PROP)
        if (current != null && current.split(File.pathSeparatorChar).contains(npcapDir)) {
            return // déjà présent
        }
        val updated = if (current.isNullOrBlank()) npcapDir else "$current${File.pathSeparator}$npcapDir"
        System.setProperty(JNA_PROP, updated)
        log.info("Npcap ajouté à {} = {}", JNA_PROP, updated)
    }
}
