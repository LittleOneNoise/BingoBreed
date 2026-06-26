package fr.lilone.bingobreed.sniffer

import fr.lilone.bingobreed.sniffer.capture.PacketCaptureFactory
import fr.lilone.bingobreed.sniffer.capture.TcpStreamReassembler
import fr.lilone.bingobreed.sniffer.config.DofusConfigProvider
import fr.lilone.bingobreed.sniffer.listener.ConnectionEvent
import fr.lilone.bingobreed.sniffer.listener.ConnectionServerListener
import fr.lilone.bingobreed.sniffer.listener.GameServerListener
import fr.lilone.bingobreed.sniffer.model.ServerEndpoint
import fr.lilone.bingobreed.sniffer.model.SnifferEvent
import fr.lilone.bingobreed.sniffer.model.VersionCheck
import fr.lilone.bingobreed.sniffer.model.breeding.Achievement
import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.Mount
import fr.lilone.bingobreed.sniffer.model.breeding.Paddock
import fr.lilone.bingobreed.sniffer.parser.breeding.AchievementMapper
import fr.lilone.bingobreed.sniffer.parser.breeding.PaddockMapper
import fr.lilone.bingobreed.sniffer.net.HostResolver
import fr.lilone.bingobreed.sniffer.net.NetworkInterfaceDetector
import fr.lilone.bingobreed.sniffer.parser.ConnectionMessageInterpreter
import fr.lilone.bingobreed.sniffer.parser.GameServerSelection
import fr.lilone.bingobreed.sniffer.parser.DescriptorRegistry
import fr.lilone.bingobreed.sniffer.parser.GameAnyExtractor
import fr.lilone.bingobreed.sniffer.parser.GameMessageDecoder
import fr.lilone.bingobreed.sniffer.parser.LoginConnectionMessageInterpreter
import fr.lilone.bingobreed.sniffer.parser.TypeUrlRegistry
import fr.lilone.bingobreed.sniffer.util.NpcapNativeSetup
import fr.lilone.bingobreed.sniffer.util.logger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.pcap4j.core.PcapNetworkInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrateur du sniffer.
 *
 * Cycle de vie :
 *  1. [NetworkInterfaceDetector] : auto-détection de l'interface (IP locale -> NIF).
 *  2. [DofusConfigProvider] : lecture de `connectionHosts` puis résolution DNS
 *     ([HostResolver]) -> endpoints des serveurs de connexion.
 *  3. Lancement de l'écoute serveur de connexion (coroutine **long-lived**).
 *  4. À chaque serveur de jeu détecté, lancement d'un [GameServerListener] **frère**
 *     (coroutine indépendante). L'écoute connexion continue : un changement de
 *     serveur => nouveau listener, en parallèle des précédents.
 *
 * Robustesse : un [SupervisorJob] isole les listeners — l'échec de l'un n'arrête
 * pas les autres ni l'écoute connexion. Les erreurs sont remontées via [events]
 * ([SnifferEvent.Failure]).
 */
class SnifferEngine(
    private val interfaceDetector: NetworkInterfaceDetector = NetworkInterfaceDetector(),
    private val configProvider: DofusConfigProvider = DofusConfigProvider(),
    private val hostResolver: HostResolver = HostResolver(),
    private val captureFactory: PacketCaptureFactory = PacketCaptureFactory(),
    private val interpreter: ConnectionMessageInterpreter = LoginConnectionMessageInterpreter(),
    private val typeUrlRegistry: TypeUrlRegistry = TypeUrlRegistry(),
    private val gameAnyExtractor: GameAnyExtractor = GameAnyExtractor(),
    descriptorRegistry: DescriptorRegistry = DescriptorRegistry.loadFromClasspath(),
    private val gameMessageDecoder: GameMessageDecoder = GameMessageDecoder(descriptorRegistry),
) {
    private val log = logger()
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + Dispatchers.IO + CoroutineName("sniffer"))

    private val _events = MutableSharedFlow<SnifferEvent>(
        replay = 16,
        extraBufferCapacity = 256,
    )
    /** Flux unique d'événements pour l'UI / les logs. */
    val events: SharedFlow<SnifferEvent> = _events.asSharedFlow()

    private val _versionCheck = MutableStateFlow<VersionCheck?>(null)
    /** Dernier contrôle d'écart de version (null tant que le bootstrap n'a pas tourné). */
    val versionCheck: StateFlow<VersionCheck?> = _versionCheck.asStateFlow()

    private val _activePaddock = MutableStateFlow<Paddock?>(null)
    /** Dernier état d'enclos décodé, pour binding direct par l'UI (null si rien encore vu). */
    val activePaddock: StateFlow<Paddock?> = _activePaddock.asStateFlow()

    private val _lastGameFrameAt = MutableStateFlow<Long?>(null)
    /**
     * Horodatage (epoch ms) du dernier frame de jeu **quelconque** (flux bavard). Santé
     * **réseau/sniffer** globale, indépendante de toute activité d'enclos.
     */
    val lastGameFrameAt: StateFlow<Long?> = _lastGameFrameAt.asStateFlow()

    /** Listeners de jeu actifs, indexés par host pour éviter les doublons. */
    private val gameJobs = ConcurrentHashMap<String, Job>()

    /** Dernier index d'enclos sélectionné (requête `hkv`) — le contenu `him` ne le porte pas. */
    @Volatile
    private var lastPaddockIndex: Int? = null

    /** Dernière catégorie de succès demandée (requête `lfc`) — la réponse `lfd` ne la porte pas. */
    @Volatile
    private var lastAchievementCategory: Int? = null

    private val _achievements = MutableStateFlow<Map<Int, Achievement>>(emptyMap())
    /**
     * Succès vus passer, indexés par id (alimenté par les listes détaillées `lfd`, rattachées à la
     * catégorie de la dernière requête `lfc`). Exposé à l'UI (onglet Succès).
     */
    val achievements: StateFlow<Map<Int, Achievement>> = _achievements.asStateFlow()

    private val _stableMounts = MutableStateFlow<Map<String, Mount>>(emptyMap())
    /**
     * Registre des montures connues de l'**étable** (depuis `hhv`, + celles sorties de l'enclos),
     * exposé à l'UI (onglet Étable) et utilisé pour réinjecter les données d'une monture qui
     * (re)entre dans l'enclos via un transfert `hif` (le `hif` ne porte que l'UUID).
     */
    val stableMounts: StateFlow<Map<String, Mount>> = _stableMounts.asStateFlow()

    private val _consumedMounts = MutableStateFlow<Set<String>>(emptySet())
    /**
     * UUIDs des montures **fraîchement accouplées** (clic « Accoupler » `htq`), donc plus fécondes
     * même si l'état encore en cache les montre à 20000. Le planificateur de repro les exclut pour ne
     * pas rester fixé dessus. Purgé dès qu'un état **frais non-fécond** arrive pour la monture
     * (jauges remises à zéro côté jeu) — cf. [clearConsumedFrom].
     */
    val consumedMounts: StateFlow<Set<String>> = _consumedMounts.asStateFlow()

    /** Démarre le sniffer (non bloquant). */
    fun start() {
        log.info("Démarrage du sniffer")
        NpcapNativeSetup.ensureOnLibraryPath()
        scope.launch(CoroutineName("bootstrap")) {
            runCatching { bootstrap() }
                .onFailure { fail("bootstrap", it) }
        }
    }

    /** Arrête tout (écoute connexion + tous les listeners de jeu). */
    fun stop() {
        log.info("Arrêt du sniffer ({} listener(s) de jeu actif(s))", gameJobs.size)
        scope.cancel()
    }

    private suspend fun bootstrap() {
        checkClientVersion()

        log.info("Étape 1/3 — détection de l'interface réseau…")
        val detected = interfaceDetector.detect()
        _events.emit(
            SnifferEvent.InterfaceSelected(
                name = detected.nif.name,
                localIp = detected.localAddress.hostAddress,
            )
        )

        log.info("Étape 2/3 — récupération config Ankama + résolution des IPs serveurs…")
        val connectionEndpoints = configProvider.fetchConnectionHosts().flatMap { host ->
            hostResolver.resolveEndpoints(host.host, host.preferredPort)
        }
        check(connectionEndpoints.isNotEmpty()) { "Aucun endpoint serveur de connexion résolu" }
        log.info("Étape 2/3 OK — {} endpoint(s) serveur de connexion à surveiller", connectionEndpoints.size)
        _events.emit(SnifferEvent.ConnectionEndpointsResolved(connectionEndpoints))

        log.info("Étape 3/3 — écoute serveur de connexion lancée, en attente de la connexion du joueur…")
        launchConnectionListener(detected.nif, connectionEndpoints)
    }

    /**
     * Contrôle l'écart entre la version du client Dofus installé et la version de référence
     * de BingoBreed. Best-effort : ne bloque jamais le démarrage, mais loggue un warning
     * et émet l'événement pour que l'UI puisse alerter en cas de désynchronisation du parsing.
     */
    private suspend fun checkClientVersion() {
        val check = configProvider.checkVersion()
        when (check) {
            is VersionCheck.UpToDate ->
                log.info("Version client alignée sur la référence BingoBreed: {}", check.local)
            is VersionCheck.ClientAhead ->
                log.warn(
                    "Client Dofus plus récent que la référence BingoBreed ({} > {}) — " +
                        "parsing/descripteurs potentiellement obsolètes.",
                    check.local, check.reference,
                )
            is VersionCheck.ClientBehind ->
                log.warn(
                    "Client Dofus plus ancien que la référence BingoBreed ({} < {}).",
                    check.local, check.reference,
                )
            is VersionCheck.Unknown ->
                log.warn(
                    "Version du client local indéterminée (lu: {}), référence {} — écart non vérifiable.",
                    check.local ?: "?", check.reference,
                )
        }
        _versionCheck.value = check
        _events.emit(SnifferEvent.VersionChecked(check))
    }

    /** Écoute serveur de connexion : long-lived, réagit aux serveurs de jeu détectés. */
    private fun launchConnectionListener(nif: PcapNetworkInterface, endpoints: List<ServerEndpoint>) {
        scope.launch(CoroutineName("connection-listener")) {
            val capture = captureFactory.create(nif, endpoints)
            val reassembler = TcpStreamReassembler(endpoints)
            val listener = ConnectionServerListener(capture, reassembler, interpreter)
            runCatching {
                listener.listen().collect { event ->
                    when (event) {
                        is ConnectionEvent.Frame ->
                            _events.emit(SnifferEvent.ConnectionFrame(event.frame))
                        is ConnectionEvent.GameServer ->
                            onGameServerSelected(nif, event.selection)
                    }
                }
            }.onFailure { fail("connection-listener", it) }
        }
    }

    /** Lance (idempotent par host) un listener de jeu frère, sans toucher aux autres. */
    private fun onGameServerSelected(nif: PcapNetworkInterface, selection: GameServerSelection) {
        gameJobs.computeIfAbsent(selection.host) {
            log.info("Nouveau listener de jeu pour {}:{}", selection.host, selection.port)
            scope.launch(CoroutineName("game-listener-${selection.host}")) {
                runCatching {
                    val endpoints = hostResolver.resolveEndpoints(selection.host, selection.port)
                    _events.emit(SnifferEvent.GameServerDetected(selection.host, endpoints))

                    val capture = captureFactory.create(nif, endpoints)
                    val reassembler = TcpStreamReassembler(endpoints)
                    GameServerListener(capture, reassembler).listen().collect { frame ->
                        _lastGameFrameAt.value = System.currentTimeMillis()
                        _events.emit(SnifferEvent.GameFrame(selection.host, frame))
                        gameAnyExtractor.extract(frame, typeUrlRegistry)?.let { decoded ->
                            val dynamic = gameMessageDecoder.decode(decoded)
                            log.debug(
                                "[GAME {}] {} {} ({}b){} {}",
                                selection.host, decoded.direction, decoded.typeUrl, decoded.value.size,
                                decoded.knownName?.let { " = $it" } ?: "",
                                if (dynamic != null) "✓descripteur" else "(non résolu)",
                            )
                            _events.emit(SnifferEvent.GameMessage(selection.host, decoded, dynamic))

                            PaddockMapper.selectedPaddockIndex(decoded, dynamic)?.let { lastPaddockIndex = it }

                            PaddockMapper.stableMounts(decoded, dynamic)?.let { found ->
                                _stableMounts.update { it + found }
                                clearConsumedFrom(found)
                            }

                            // Accouplement (`htq`) : les 2 parents sont consommés → exclus du planner.
                            PaddockMapper.bredPair(decoded, dynamic)?.let { ids ->
                                _consumedMounts.update { it + ids }
                            }

                            AchievementMapper.requestedCategory(decoded, dynamic)?.let { lastAchievementCategory = it }
                            AchievementMapper.detailedAchievements(decoded, dynamic, lastAchievementCategory)?.let { list ->
                                _achievements.update { it + list.associateBy(Achievement::id) }
                            }
                            // Liste générale `mdz` (vue d'ensemble à l'ouverture des succès) — complète l'index.
                            AchievementMapper.listedAchievements(decoded, dynamic)?.let { list ->
                                _achievements.update { it + list.associateBy(Achievement::id) }
                            }

                            PaddockMapper.fromGameMessage(decoded, dynamic)?.let { paddock ->
                                val withId = paddock.copy(id = lastPaddockIndex)
                                _activePaddock.value = withId
                                clearConsumedFrom(paddock.mounts)
                                _events.emit(SnifferEvent.PaddockUpdated(selection.host, withId))
                            }

                            PaddockMapper.transferredMountIds(decoded, dynamic)?.let { ids ->
                                applyMountTransfer(selection.host, ids)
                            }

                            PaddockMapper.activatedElement(decoded, dynamic)?.let { el ->
                                applyGaugeActive(selection.host, el, active = true)
                            }
                            PaddockMapper.deactivatedElement(decoded, dynamic)?.let { el ->
                                applyGaugeActive(selection.host, el, active = false)
                            }
                            // Réponse serveur : jauges auto-désactivées (règles 1 sérénité / 2 max).
                            PaddockMapper.autoDeactivatedElements(decoded, dynamic)?.forEach { el ->
                                applyGaugeActive(selection.host, el, active = false)
                            }
                        }
                    }
                }.onFailure { fail("game-listener-${selection.host}", it) }
            }.also { job ->
                job.invokeOnCompletion {
                    gameJobs.remove(selection.host, job)
                    log.debug("Listener de jeu {} terminé", selection.host)
                }
            }
        }
    }

    /**
     * Applique un transfert de montures (`hif`) à l'enclos actif **en live**, sans attendre le
     * prochain push complet : on déduit le sens par l'état courant — UUID présent dans l'enclos
     * → il en sort (retiré, mémorisé dans l'étable) ; sinon → il y entre (réinjecté depuis le
     * registre étable si connu).
     */
    private suspend fun applyMountTransfer(host: String, ids: Set<String>) {
        val current = _activePaddock.value ?: return
        val mounts = current.mounts.toMutableMap()
        val toStable = mutableMapOf<String, Mount>()
        var changed = false
        for (uuid in ids) {
            val inEnclos = mounts[uuid]
            if (inEnclos != null) {
                toStable[uuid] = inEnclos
                mounts.remove(uuid)
                changed = true
            } else {
                _stableMounts.value[uuid]?.let { mounts[uuid] = it; changed = true }
            }
        }
        if (toStable.isNotEmpty()) _stableMounts.update { it + toStable }
        if (changed) {
            val updated = current.copy(mounts = mounts)
            _activePaddock.value = updated
            _events.emit(SnifferEvent.PaddockUpdated(host, updated))
        }
    }

    /**
     * Applique en live l'(dés)activation d'une jauge de l'enclos actif, sans attendre le
     * prochain push (qui n'arrive pas s'il n'y a ni monture ni consommation).
     */
    private suspend fun applyGaugeActive(host: String, element: Int, active: Boolean) {
        val current = _activePaddock.value ?: return
        val elements = current.activeElements.toMutableList()
        val changed = if (active) {
            if (element !in elements) elements.add(element).let { true } else false
        } else {
            elements.remove(element)
        }
        if (changed) {
            val updated = current.copy(activeElements = elements)
            _activePaddock.value = updated
            _events.emit(SnifferEvent.PaddockUpdated(host, updated))
        }
    }

    /**
     * Purge de l'ensemble « consommées » les montures pour lesquelles un état **frais non-fécond**
     * vient d'arriver (jauges remises à zéro après l'accouplement) : la marque optimiste posée par
     * `htq` n'a plus lieu d'être, l'état réel prend le relais. Une monture qui resterait fécond dans
     * les pushs (cache 20000) reste consommée — on fait confiance à `htq`.
     */
    private fun clearConsumedFrom(mounts: Map<String, Mount>) {
        if (_consumedMounts.value.isEmpty()) return
        val nowFresh = mounts.filterValues { it.fertility != Fertility.FECONDE }.keys
        if (nowFresh.isNotEmpty()) _consumedMounts.update { it - nowFresh }
    }

    /** Loggue l'erreur et la propage sur le flux d'events (sans casser le SupervisorJob). */
    private suspend fun fail(context: String, cause: Throwable) {
        log.error("Échec [{}]: {}", context, cause.message, cause)
        _events.emit(SnifferEvent.Failure(context, cause))
    }
}
