package fr.lilone.bingobreed.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.lilone.bingobreed.sniffer.model.breeding.SerenityBand

/**
 * Smiley de sérénité dessiné au Canvas, teinté par la bande. La forme de la bouche
 * encode l'humeur in-game : :D (amour) → :C (endurance).
 */
@Composable
fun SerenitySmiley(band: SerenityBand, modifier: Modifier = Modifier, diameter: Dp = 18.dp) {
    val color = serenityColor(band)
    Canvas(modifier.size(diameter)) {
        val d = size.minDimension
        val r = d / 2f
        val center = Offset(r, r)
        val line = Stroke(width = d * 0.09f)

        drawCircle(color = color.copy(alpha = 0.18f), radius = r, center = center)
        drawCircle(color = color, radius = r, center = center, style = Stroke(width = d * 0.08f))

        // yeux
        val eyeR = d * 0.055f
        drawCircle(color, eyeR, Offset(r * 0.66f, r * 0.78f))
        drawCircle(color, eyeR, Offset(r * 1.34f, r * 0.78f))

        // bouche : courbure selon l'humeur (vers le haut = content)
        val left = r * 0.58f
        val right = r * 1.42f
        val (baseY, ctrlY) = when (band) {
            SerenityBand.GREEN -> r * 1.05f to r * 1.72f  // grand sourire :D
            SerenityBand.PURPLE -> r * 1.18f to r * 1.5f  // sourire :)
            SerenityBand.BLUE -> r * 1.48f to r * 1.12f   // moue :(
            SerenityBand.RED -> r * 1.52f to r * 1.0f     // grande moue :C
        }
        val mouth = Path().apply {
            moveTo(left, baseY)
            quadraticTo(r, ctrlY, right, baseY)
        }
        drawPath(mouth, color, style = line)
    }
}
