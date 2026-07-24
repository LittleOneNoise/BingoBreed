package fr.lilone.bingobreed.log

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.AppenderBase

/**
 * Appender logback qui recopie chaque ligne dans [LogMirror], pour que l'onglet Debug affiche
 * exactement ce que voit la console. Déclaré dans `logback.xml` — jamais instancié à la main.
 *
 * Contrairement aux appenders console/fichier, celui-ci n'a pas de seuil : il reçoit tout ce que les
 * loggers laissent passer (TRACE compris), le tri se faisant côté UI.
 */
class LogMirrorAppender : AppenderBase<ILoggingEvent>() {

    override fun append(event: ILoggingEvent) {
        LogMirror.publish(
            LogRecord(
                timestampMs = event.timeStamp,
                level = event.level.toLogLevel(),
                logger = event.loggerName,
                thread = event.threadName,
                message = event.formattedMessage,
                stackTrace = event.throwableProxy?.let(ThrowableProxyUtil::asString),
            )
        )
    }

    private fun Level.toLogLevel(): LogLevel = when (levelInt) {
        Level.TRACE_INT -> LogLevel.TRACE
        Level.DEBUG_INT -> LogLevel.DEBUG
        Level.WARN_INT -> LogLevel.WARN
        Level.ERROR_INT -> LogLevel.ERROR
        else -> LogLevel.INFO
    }
}
