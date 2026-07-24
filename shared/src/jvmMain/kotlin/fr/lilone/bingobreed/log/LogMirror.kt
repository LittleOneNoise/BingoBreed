package fr.lilone.bingobreed.log

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Niveaux slf4j/logback, du plus bavard au plus grave. */
enum class LogLevel { TRACE, DEBUG, INFO, WARN, ERROR }

/**
 * Une ligne de log capturée, telle qu'elle part vers la console.
 *
 * [logger] est le nom **complet** de la classe émettrice : l'abréviation (`f.l.b.s.SnifferEngine`)
 * est un choix d'affichage, et le filtre par logger doit pouvoir matcher le nom entier.
 */
data class LogRecord(
    val timestampMs: Long,
    val level: LogLevel,
    val logger: String,
    val thread: String,
    val message: String,
    /** Stacktrace déjà formatée si l'événement portait un throwable, sinon `null`. */
    val stackTrace: String? = null,
)

/**
 * Miroir en mémoire de la sortie console, alimenté par [LogMirrorAppender] (branché dans
 * `logback.xml`) et consommé par l'onglet Debug.
 *
 * Le `replay` fait office de tampon circulaire : ouvrir l'onglet Debug après coup rejoue les
 * [CAPACITY] dernières lignes, y compris celles émises pendant le bootstrap — c'est justement là
 * qu'on trouve les échecs d'initialisation (Npcap absent, config Ankama injoignable…).
 *
 * `DROP_OLDEST` garantit que [publish] ne bloque ni n'échoue jamais : un appender logback tourne sur
 * le thread émetteur, il ne doit en aucun cas ralentir le sniffer.
 */
object LogMirror {
    const val CAPACITY = 3000

    private val _records = MutableSharedFlow<LogRecord>(
        replay = CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val records: SharedFlow<LogRecord> = _records.asSharedFlow()

    fun publish(record: LogRecord) {
        _records.tryEmit(record)
    }
}
