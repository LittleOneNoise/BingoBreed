package fr.lilone.bingobreed.tools

import fr.lilone.bingobreed.sniffer.model.breeding.MuldoRobes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * **Outil hors-ligne** (ce n'est pas un vrai test) : télécharge **une fois** les images de robes de
 * Muldo depuis l'API DofusDB (objets `typeId=332`) et les écrit en ressources bundlées
 * (`shared/src/jvmMain/resources/muldo/<clé>.png`), nommées par [MuldoRobes.imageKey].
 *
 * Le pont DofusDB ↔ BingoBreed se fait par **nom de robe** (l'`id`/`iconId`/`appearanceId` DofusDB
 * ne sont PAS l'id d'apparence in-game) : `name.fr` « Muldo … » → [MuldoRobes.resolve] → robe
 * canonique → [MuldoRobes.imageKey]. En jeu : `appearanceId` → `Robes.IDS` → nom → même clé.
 *
 * Garde-fou par propriété système pour ne pas frapper le réseau à chaque `jvmTest` :
 *
 *   ./gradlew :shared:jvmTest --tests "*MuldoImageDownloader*" -DdownloadMuldoImages=true
 */
class MuldoImageDownloader {
    @Test
    fun download() {
        if (System.getProperty("downloadMuldoImages") != "true") return
        run()
    }
}

private const val TYPE_MULDO = 332

/** Relatif au répertoire du module `shared` (working dir des tests gradle). */
private val OUT_DIR: Path = Paths.get("src", "jvmMain", "resources", "muldo")

private fun run() {
    Files.createDirectories(OUT_DIR)
    val client = HttpClient.newHttpClient()
    val pageSize = 50
    var skip = 0
    var total = Int.MAX_VALUE
    val written = HashSet<String>()
    val unresolved = LinkedHashSet<String>()

    while (skip < total) {
        val query =
            "typeId[\$in][]=$TYPE_MULDO&level[\$gte]=0&level[\$lte]=200&\$skip=$skip&\$limit=$pageSize&lang=fr"
        val uri = URI("https", "api.dofusdb.fr", "/items", query, null) // encode les caractères réservés
        val resp = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString())
        val obj = Json.parseToJsonElement(resp.body()).jsonObject
        total = obj["total"]!!.jsonPrimitive.int
        val data = obj["data"]!!.jsonArray
        if (data.isEmpty()) break

        for (el in data) {
            val item = el.jsonObject
            val nameFr = item["name"]?.jsonObject?.get("fr")?.jsonPrimitive?.contentOrNull ?: continue
            val img = item["img"]?.jsonPrimitive?.contentOrNull ?: continue
            val loose = nameFr.removePrefix("Muldo ").trim()
            val robe = MuldoRobes.resolve(loose)
            if (robe == null) {
                unresolved += nameFr
                continue
            }
            val key = MuldoRobes.imageKey(robe.name)
            if (!written.add(key)) continue // garde la 1re (tri -id = plus récente) en cas de doublon
            val bytes = client.send(HttpRequest.newBuilder(URI(img)).GET().build(), HttpResponse.BodyHandlers.ofByteArray()).body()
            Files.write(OUT_DIR.resolve("$key.png"), bytes)
        }
        skip += pageSize
    }

    println("Muldo images : ${written.size} écrites dans ${OUT_DIR.toAbsolutePath()}")
    if (unresolved.isNotEmpty()) println("Items non résolus (ignorés) : $unresolved")
    val missing = MuldoRobes.ALL.map { it.name }.filterNot { MuldoRobes.imageKey(it) in written }
    if (missing.isNotEmpty()) println("Robes connues sans image (${missing.size}) : $missing")
}
