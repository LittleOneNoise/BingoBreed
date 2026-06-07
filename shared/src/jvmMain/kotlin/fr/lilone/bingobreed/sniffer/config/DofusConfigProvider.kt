package fr.lilone.bingobreed.sniffer.config

import fr.lilone.bingobreed.sniffer.model.ConnectionHost
import fr.lilone.bingobreed.sniffer.util.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path

/**
 * Étape 2 — récupération de la configuration publique de Dofus.
 *
 * On tape sur [configUrl] (par défaut la config dofus3 d'Ankama), on lit le champ
 * `connectionHosts` (tableau de lignes `"name:host:ports"`) et on en déduit les
 * serveurs de connexion à surveiller.
 *
 * NB : l'URL est également référencée dans le fichier local [VERSION_FILE]. On la
 * garde codée en dur par défaut, mais [readVersion] permet de la corréler / logger.
 */
class DofusConfigProvider(
    private val configUrl: String = DEFAULT_CONFIG_URL,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val log = logger()

    /** Lit la config distante et renvoie les serveurs de connexion parsés. */
    suspend fun fetchConnectionHosts(): List<ConnectionHost> = withContext(Dispatchers.IO) {
        log.info("Récupération config Ankama: {} (version locale: {})", configUrl, readVersion() ?: "?")
        val request = HttpRequest.newBuilder(URI.create(configUrl)).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Config Ankama inaccessible ($configUrl) : HTTP ${response.statusCode()}"
        }
        json.decodeFromString<RemoteConfig>(response.body())
            .connectionHosts
            .map(ConnectionHost::parse)
            .also { hosts -> log.info("{} serveur(s) de connexion: {}", hosts.size, hosts.map { "${it.host}:${it.preferredPort}" }) }
    }

    /** Contenu brut du fichier de version local, si présent (best-effort, pour log/corrélation). */
    fun readVersion(path: Path = Path.of(VERSION_FILE)): String? =
        runCatching { Files.readString(path).trim() }
            .onFailure { log.warn("Fichier de version introuvable: {}", path) }
            .getOrNull()

    @Serializable
    private data class RemoteConfig(
        @SerialName("connectionHosts")
        val connectionHosts: List<String> = emptyList(),
    )

    companion object {
        const val DEFAULT_CONFIG_URL = "https://dofus2.cdn.ankama.com/config/dofus3.json"
        const val VERSION_FILE =
            "C:\\Users\\natha\\AppData\\Local\\Ankama\\Dofus-dofus3\\Dofus_Data\\StreamingAssets\\version"
    }
}
