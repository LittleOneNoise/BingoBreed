package fr.lilone.bingobreed.tools

import fr.lilone.bingobreed.sniffer.model.breeding.MuldoRobes
import fr.lilone.bingobreed.sniffer.model.breeding.Robes
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
 * **Outils hors-ligne** (ce ne sont pas de vrais tests) : téléchargent **une fois** les images de
 * robes depuis l'API DofusDB (objets `typeId` = 332 Muldo / 333 Volkorne) et les écrivent en
 * ressources bundlées (`shared/src/jvmMain/resources/<famille>/<clé>.png`).
 *
 * Le pont DofusDB ↔ BingoBreed se fait par **nom de robe** (l'`id`/`iconId`/`appearanceId` DofusDB
 * ne sont PAS l'id d'apparence in-game) : `name.fr` « Muldo … » → robe canonique →
 * [MuldoRobes.imageKey]. En jeu : `appearanceId` → [Robes.IDS] → nom → même clé.
 *
 * Garde-fou par propriété système pour ne pas frapper le réseau à chaque `jvmTest` :
 *
 *   ./gradlew :shared:jvmTest --tests "*MountImageDownloader*" -DdownloadMountImages=true
 *
 * Filtrer par famille avec `--tests "*MountImageDownloader.muldo*"` (resp. `.volkorne`).
 */
class MountImageDownloader {

    @Test
    fun muldo() {
        if (!enabled()) return
        // Robes attendues = catalogue muldo complet ; `resolve` tolère casse, accents et ordre.
        download(
            typeId = 332,
            namePrefix = "Muldo ",
            outDir = Paths.get("src", "jvmMain", "resources", "muldo"),
            expected = MuldoRobes.ALL.map { it.name },
            resolve = { loose -> MuldoRobes.resolve(loose)?.name },
        )
    }

    @Test
    fun volkorne() {
        if (!enabled()) return
        // Le Volkorne n'a pas de catalogue de recettes (pas d'élevage planifié) : la référence est
        // [Robes.VOLKORNE_IDS], et la résolution passe par la clé normalisée (ordre des couleurs
        // indifférent). Les variantes « sauvage » n'ont pas de certificat → absentes des items.
        val byKey = Robes.VOLKORNE_IDS.values.associateBy { MuldoRobes.imageKey(it) }
        download(
            typeId = 333,
            namePrefix = "Volkorne ",
            outDir = Paths.get("src", "jvmMain", "resources", "volkorne"),
            expected = byKey.values.filterNot { it.endsWith(" Sauvage") },
            resolve = { loose -> byKey[MuldoRobes.imageKey(loose)] },
        )
    }

    private fun enabled(): Boolean = System.getProperty("downloadMountImages") == "true"
}

/**
 * Pagine les items DofusDB de [typeId], résout chaque `name.fr` en robe canonique via [resolve] et
 * écrit son image sous `outDir/<clé>.png`. Journalise les items non résolus et les robes [expected]
 * restées sans image — les deux doivent être vides pour une famille complète.
 */
private fun download(
    typeId: Int,
    namePrefix: String,
    outDir: Path,
    expected: List<String>,
    resolve: (String) -> String?,
) {
    Files.createDirectories(outDir)
    val client = HttpClient.newHttpClient()
    val pageSize = 50
    var skip = 0
    var total = Int.MAX_VALUE
    val written = HashSet<String>()
    val unresolved = LinkedHashSet<String>()

    while (skip < total) {
        // Pas de `$select` : l'API dérive `img` de champs annexes et renverrait `undefined.png`.
        val query = "typeId[\$in][]=$typeId&level[\$gte]=0&level[\$lte]=200&\$skip=$skip&\$limit=$pageSize&lang=fr"
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
            val robe = resolve(nameFr.removePrefix(namePrefix).trim())
            if (robe == null) {
                unresolved += nameFr
                continue
            }
            val key = MuldoRobes.imageKey(robe)
            if (!written.add(key)) continue // garde la 1re (tri -id = plus récente) en cas de doublon
            val bytes = client.send(HttpRequest.newBuilder(URI(img)).GET().build(), HttpResponse.BodyHandlers.ofByteArray()).body()
            Files.write(outDir.resolve("$key.png"), bytes)
        }
        skip += pageSize
    }

    println("Images typeId=$typeId : ${written.size} écrites dans ${outDir.toAbsolutePath()}")
    if (unresolved.isNotEmpty()) println("Items non résolus (ignorés) : $unresolved")
    val missing = expected.filterNot { MuldoRobes.imageKey(it) in written }
    if (missing.isNotEmpty()) println("Robes connues sans image (${missing.size}) : $missing")
}
