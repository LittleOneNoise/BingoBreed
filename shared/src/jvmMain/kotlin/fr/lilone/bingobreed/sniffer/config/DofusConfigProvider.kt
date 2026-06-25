package fr.lilone.bingobreed.sniffer.config

import fr.lilone.bingobreed.sniffer.model.ConnectionHost
import fr.lilone.bingobreed.sniffer.model.DofusClientVersion
import fr.lilone.bingobreed.sniffer.model.LocalClientInfo
import fr.lilone.bingobreed.sniffer.model.VersionCheck
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
 * L'URL de config est lue dans le fichier local [VERSION_FILE] (champ `ConfigUrl`), qui
 * est la source de vérité du client. On y récupère le champ `connectionHosts` (tableau de
 * lignes `"name:host:ports"`) pour en déduire les serveurs de connexion à surveiller.
 *
 * [DEFAULT_CONFIG_URL] ne sert que de filet de secours si le fichier est illisible, et
 * [configUrlOverride] permet de forcer une URL (tests). [checkVersion] compare en plus la
 * version du client local à [DOFUS_CLIENT_VERSION_REFERENCE].
 */
class DofusConfigProvider(
    private val configUrlOverride: String? = null,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val log = logger()

    /** Lit la config distante et renvoie les serveurs de connexion parsés. */
    suspend fun fetchConnectionHosts(): List<ConnectionHost> = withContext(Dispatchers.IO) {
        val info = readClientInfo()
        val configUrl = configUrlOverride ?: info?.configUrl ?: DEFAULT_CONFIG_URL
        log.info("Récupération config Ankama: {} (version locale: {})", configUrl, info?.version ?: "?")
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

    /**
     * Lit et parse le fichier `version` local (format `Clé=Valeur` ligne par ligne) en
     * [LocalClientInfo]. Best-effort : renvoie `null` si le fichier est illisible.
     */
    fun readClientInfo(path: Path = VERSION_FILE): LocalClientInfo? =
        runCatching {
            val fields = Files.readAllLines(path).mapNotNull { line ->
                val sep = line.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
                line.substring(0, sep).trim() to line.substring(sep + 1).trim()
            }.toMap()
            LocalClientInfo(
                version = fields["Version"],
                buildDate = fields["BuildDate"],
                configUrl = fields["ConfigUrl"],
            )
        }.onFailure { log.warn("Fichier de version illisible: {}", path) }.getOrNull()

    /**
     * Compare la version du client Dofus installé localement à la
     * [DOFUS_CLIENT_VERSION_REFERENCE] figée au build de BingoBreed, et classe l'écart
     * éventuel.
     *
     * Un écart signale que les descripteurs / le parsing embarqués peuvent être
     * désynchronisés du client réel.
     */
    fun checkVersion(path: Path = VERSION_FILE): VersionCheck {
        val local = readClientInfo(path)?.version
        val localVersion = local?.let(DofusClientVersion::parse)
        val reference = DofusClientVersion.parse(DOFUS_CLIENT_VERSION_REFERENCE)
        return when {
            local == null || localVersion == null || reference == null ->
                VersionCheck.Unknown(DOFUS_CLIENT_VERSION_REFERENCE, local)
            localVersion == reference -> VersionCheck.UpToDate(DOFUS_CLIENT_VERSION_REFERENCE, local)
            localVersion > reference -> VersionCheck.ClientAhead(DOFUS_CLIENT_VERSION_REFERENCE, local)
            else -> VersionCheck.ClientBehind(DOFUS_CLIENT_VERSION_REFERENCE, local)
        }
    }

    @Serializable
    private data class RemoteConfig(
        @SerialName("connectionHosts")
        val connectionHosts: List<String> = emptyList(),
    )

    companion object {
        const val DEFAULT_CONFIG_URL = "https://dofus2.cdn.ankama.com/config/dofus3.json"

        /**
         * Version du client Dofus sur laquelle BingoBreed a été construit à sa release :
         * référence pour [checkVersion]. À mettre à jour à chaque réalignement des
         * descripteurs / du parsing protobuf sur une nouvelle version du client.
         */
        const val DOFUS_CLIENT_VERSION_REFERENCE = "3.6.4.3"

        /**
         * Fichier `version` du client, sous le `Dofus_Data` de l'install Ankama. Résolu via
         * `%LOCALAPPDATA%` pour rester valable quel que soit le compte Windows (Dofus
         * s'installe toujours sous ce dossier), avec repli sur `%USERPROFILE%`.
         */
        val VERSION_FILE: Path = Path.of(
            System.getenv("LOCALAPPDATA")
                ?: "${System.getenv("USERPROFILE")}\\AppData\\Local",
            "Ankama", "Dofus-dofus3", "Dofus_Data", "StreamingAssets", "version",
        )
    }
}
