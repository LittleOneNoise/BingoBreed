package fr.lilone.bingobreed.sniffer

import fr.lilone.bingobreed.sniffer.capture.PacketCaptureFactory
import fr.lilone.bingobreed.sniffer.capture.TcpStreamReassembler
import fr.lilone.bingobreed.sniffer.config.DofusConfigProvider
import fr.lilone.bingobreed.sniffer.listener.ConnectionEvent
import fr.lilone.bingobreed.sniffer.listener.ConnectionServerListener
import fr.lilone.bingobreed.sniffer.listener.GameServerListener
import fr.lilone.bingobreed.sniffer.model.ServerEndpoint
import fr.lilone.bingobreed.sniffer.model.SnifferEvent
import fr.lilone.bingobreed.sniffer.net.HostResolver
import fr.lilone.bingobreed.sniffer.net.NetworkInterfaceDetector
import fr.lilone.bingobreed.sniffer.parser.ConnectionMessageInterpreter
import fr.lilone.bingobreed.sniffer.parser.GameServerSelection
import fr.lilone.bingobreed.sniffer.parser.LoginConnectionMessageInterpreter
import fr.lilone.bingobreed.sniffer.util.NpcapNativeSetup
import fr.lilone.bingobreed.sniffer.util.logger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** Listeners de jeu actifs, indexés par host pour éviter les doublons. */
    private val gameJobs = ConcurrentHashMap<String, Job>()

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
                        _events.emit(SnifferEvent.GameFrame(selection.host, frame))
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

    /** Loggue l'erreur et la propage sur le flux d'events (sans casser le SupervisorJob). */
    private suspend fun fail(context: String, cause: Throwable) {
        log.error("Échec [{}]: {}", context, cause.message, cause)
        _events.emit(SnifferEvent.Failure(context, cause))
    }
}
