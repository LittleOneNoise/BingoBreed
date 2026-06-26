package fr.lilone.bingobreed.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.lilone.bingobreed.sniffer.model.breeding.MuldoRobes
import fr.lilone.bingobreed.sniffer.model.breeding.Robes
import org.jetbrains.skia.Image as SkiaImage
import java.util.concurrent.ConcurrentHashMap

/**
 * Chargement (et cache mémoire) des **images de robe** bundlées en ressources
 * (`/muldo/<clé>.png`, cf. `tools/MuldoImageDownloader`). Clé = [MuldoRobes.imageKey] du nom de robe
 * canonique. Renvoie null si la robe n'a pas d'image bundlée → l'UI retombe sur la pastille couleur.
 */
private val cache = ConcurrentHashMap<String, ImageBitmap>()
private val misses = ConcurrentHashMap.newKeySet<String>()

/** Image de la robe [robe] (nom canonique) si bundlée, sinon null. Chargement paresseux + caché. */
fun robeImage(robe: String): ImageBitmap? {
    cache[robe]?.let { return it }
    if (robe in misses) return null
    val key = MuldoRobes.imageKey(robe)
    val bytes = RobeImagesAnchor::class.java.getResourceAsStream("/muldo/$key.png")?.use { it.readBytes() }
    val bitmap = bytes?.let { runCatching { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }
    if (bitmap != null) cache[robe] = bitmap else misses += robe
    return bitmap
}

/**
 * Vignette de robe par **nom canonique** : image de monture bundlée si disponible, sinon repli sur
 * la pastille couleur. Carrée (les images DofusDB le sont), légèrement arrondie.
 */
@Composable
internal fun RobeMark(robe: String, size: Dp) {
    val img = robeImage(robe)
    if (img != null) {
        Image(img, robe, Modifier.size(size).clip(RoundedCornerShape(3.dp)), contentScale = ContentScale.Fit)
    } else {
        Box(Modifier.size(size).background(robeColorByName(robe), CircleShape))
    }
}

/**
 * Vignette de robe par **id d'apparence** in-game (robe propre `feam` / parents `feap`) : image si la
 * robe est connue ([Robes.robeName]) et bundlée, sinon repli sur la pastille couleur [robeColor].
 */
@Composable
internal fun RobeMark(appearanceId: Int, size: Dp) {
    val name = Robes.robeName(appearanceId)
    val img = name?.let { robeImage(it) }
    if (img != null) {
        Image(img, name, Modifier.size(size).clip(RoundedCornerShape(3.dp)), contentScale = ContentScale.Fit)
    } else {
        Box(Modifier.size(size).background(robeColor(appearanceId), CircleShape))
    }
}

/** Ancre stable pour `getResourceAsStream` (classloader de ce module). */
private class RobeImagesAnchor
