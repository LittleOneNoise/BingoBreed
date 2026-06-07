package fr.lilone.bingobreed.sniffer.diagnostics

import com.chatofus.proto.login_message.LoginMessage
import fr.lilone.bingobreed.sniffer.model.DofusFrame

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
