package fr.lilone.bingobreed.sniffer.diagnostics

import com.chatofus.proto.login_message.LoginMessage
import com.google.protobuf.Message
import fr.lilone.bingobreed.sniffer.model.DecodedGameAny
import fr.lilone.bingobreed.sniffer.model.DofusFrame
import fr.lilone.bingobreed.sniffer.model.SnifferEvent

/**
 * Produit un rapport lisible d'une [DofusFrame] pour le mode diagnostic :
 * décodage brut (sans schéma) + parse typé, afin de comparer la structure
 * attendue avec les octets réellement reçus.
 */
object ProtobufDiagnostics {

    /** Rapport brut seul (utilisé pour les frames de jeu, pas encore typées). */
    fun reportRaw(frame: DofusFrame): String = buildString {
        appendLine(header(frame))
        appendLine("[brut sans schéma]")
        append(RawProtobufDumper.dump(frame.protobuf).prependIndent("  "))
    }

    /** Rapport pour le flux connexion : brut + parse typé LoginMessage + champs inconnus. */
    fun reportConnection(frame: DofusFrame): String = buildString {
        appendLine(header(frame))
        appendLine("[brut sans schéma]")
        appendLine(RawProtobufDumper.dump(frame.protobuf).prependIndent("  "))
        appendLine("[parse typé LoginMessage]")
        append(loginTyped(frame).prependIndent("  "))
    }

    /** Rapport d'un event de message de jeu (évite d'exposer protobuf aux consommateurs). */
    fun reportGameMessage(event: SnifferEvent.GameMessage): String =
        reportGameMessage(event.message, event.dynamic)

    /**
     * Rapport d'un message de jeu : structure décodée via descripteur
     * ([DynamicMessage], noms de champs obfusqués mais types/valeurs corrects),
     * ou décodage brut si le code est inconnu du descripteur.
     */
    fun reportGameMessage(message: DecodedGameAny, dynamic: Message?): String = buildString {
        val source = if (dynamic != null) "descripteur" else "brut"
        val id = message.requestId?.let { " id=$it" } ?: ""
        appendLine("── ${message.direction} ${message.typeUrl} [$source]$id")
        val body = dynamic?.toString()?.ifBlank { "(message vide)" }
            ?: RawProtobufDumper.dump(message.value)
        append(body.prependIndent("  "))
    }

    private fun loginTyped(frame: DofusFrame): String = try {
        val msg = LoginMessage.parseFrom(frame.protobuf)
        val unknown = msg.unknownFields.asMap().keys
        val warn = if (unknown.isNotEmpty()) {
            "⚠️ champs INCONNUS (proto périmé ?): $unknown\n"
        } else {
            ""
        }
        warn + msg.toString().ifBlank { "(message vide / oneof non renseigné)" }
    } catch (e: Exception) {
        "⚠️ parseFrom(LoginMessage) a échoué: ${e.message}"
    }

    private fun header(frame: DofusFrame) =
        "── ${frame.direction} | ${frame.size} bytes | ${frame.stream}"
}
