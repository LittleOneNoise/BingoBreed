package fr.lilone.bingobreed.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.FuelTier
import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
import fr.lilone.bingobreed.sniffer.model.breeding.SerenityBand
import fr.lilone.bingobreed.sniffer.model.breeding.Sex

private val DarkColors = darkColorScheme(
    background = Color(0xFF0E1116),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF21262D),
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF06121A),
    secondary = Color(0xFF8B949E),
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D),
)

@Composable
fun BingoBreedTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}

/** Couleurs sémantiques **indépendantes du thème** (statuts métier). */
object BreedColors {
    val feconde = Color(0xFF2EE6A6)
    val fertile = Color(0xFF4FC3F7)
    val sterile = Color(0xFF6E7681)

    val male = Color(0xFF58A6FF)
    val female = Color(0xFFE86AA6)

    val serenityGreen = Color(0xFF3FB950)
    val serenityRose = Color(0xFFE86AA6)
    val serenityViolet = Color(0xFFA371F7)
    val serenityPurple = Color(0xFFA371F7)
    val serenityRed = Color(0xFFF85149)

    /** Marron clair pour l'icône d'enclos (barrière en bois), indépendant de l'état de sélection. */
    val fence = Color(0xFFC8A87C)

    /** Jaune or pour l'icône de succès (trophée), indépendant de l'état de sélection. */
    val gold = Color(0xFFE3B341)

    val tier1 = Color(0xFFF85149)
    val tier2 = Color(0xFFD29922)
    val tier3 = Color(0xFF3FB950)
    val tier4 = Color(0xFF2EE6A6)

    val gaugeLove = Color(0xFFE86AA6)
    val gaugeMaturity = Color(0xFF58A6FF)   // bleu
    val gaugeEndurance = Color(0xFFD29922)  // jaune/or
}

fun fertilityColor(f: Fertility): Color = when (f) {
    Fertility.FECONDE -> BreedColors.feconde
    Fertility.FERTILE -> BreedColors.fertile
    Fertility.STERILE -> BreedColors.sterile
}

fun sexColor(s: Sex): Color = if (s == Sex.MALE) BreedColors.male else BreedColors.female

fun tierColor(t: FuelTier): Color = when (t) {
    FuelTier.T1 -> BreedColors.tier1
    FuelTier.T2 -> BreedColors.tier2
    FuelTier.T3 -> BreedColors.tier3
    FuelTier.T4 -> BreedColors.tier4
}

// NB : les noms d'enum (BLUE/PURPLE) sont historiques ; les couleurs affichées suivent la
// convention in-game demandée : ≤ -2000 rouge, -2000..-1 violet, 0..2000 rose, ≥ 2001 vert.
fun serenityColor(b: SerenityBand): Color = when (b) {
    SerenityBand.GREEN -> BreedColors.serenityGreen   // ≥ 2001 : vert
    SerenityBand.PURPLE -> BreedColors.serenityRose   // 0..2000 : rose
    SerenityBand.BLUE -> BreedColors.serenityViolet   // -2000..-1 : violet
    SerenityBand.RED -> BreedColors.serenityRed       // ≤ -2000 : rouge
}

fun gaugeColor(type: Int): Color = when (type) {
    MountGauge.TYPE_LOVE -> BreedColors.gaugeLove
    MountGauge.TYPE_MATURITY -> BreedColors.gaugeMaturity
    else -> BreedColors.gaugeEndurance
}

/** Couleur d'une icône de carburant par ordinal d'élément, alignée sur l'effet produit. */
fun fuelColor(element: Int): Color = when (element) {
    0 -> BreedColors.serenityRed     // baffeur : baisse sérénité
    1 -> BreedColors.serenityPurple  // caresseur : augmente sérénité
    2 -> BreedColors.gaugeEndurance  // foudroyeur → endurance
    3 -> BreedColors.gaugeMaturity   // abreuvoir → maturité
    4 -> BreedColors.gaugeLove       // dragofesse → amour
    else -> BreedColors.serenityGreen // mangeoire → XP (texte)
}

/** Couleur stable dérivée d'un id de robe (faute de mapping robe → couleur réel). */
fun robeColor(id: Int): Color = Color.hsv((id * 47 % 360).toFloat(), 0.55f, 0.85f)
