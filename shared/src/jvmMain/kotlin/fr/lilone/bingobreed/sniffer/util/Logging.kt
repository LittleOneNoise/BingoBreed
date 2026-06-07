package fr.lilone.bingobreed.sniffer.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Récupère un logger slf4j nommé d'après la classe appelante. */
inline fun <reified T : Any> T.logger(): Logger = LoggerFactory.getLogger(T::class.java)
