package fr.lilone.bingobreed.sniffer.parser

import com.google.protobuf.DynamicMessage
import fr.lilone.bingobreed.sniffer.model.DecodedGameAny

/**
 * Décode le `value` d'un message de jeu en [DynamicMessage] via le descripteur
 * résolu par `type_url`. Renvoie null si le code est inconnu du descripteur
 * (descripteur périmé : régénérer output.proto via protodec) ou si le payload
 * ne correspond pas.
 */
class GameMessageDecoder(private val descriptors: DescriptorRegistry) {

    fun decode(any: DecodedGameAny): DynamicMessage? {
        val descriptor = descriptors.findByTypeUrl(any.typeUrl) ?: return null
        return runCatching { DynamicMessage.parseFrom(descriptor, any.value) }.getOrNull()
    }
}
