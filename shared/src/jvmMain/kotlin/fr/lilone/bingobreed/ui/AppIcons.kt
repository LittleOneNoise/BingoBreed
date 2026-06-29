package fr.lilone.bingobreed.ui

import fr.lilone.bingobreed.shared.generated.resources.Res
import fr.lilone.bingobreed.shared.generated.resources.amour
import fr.lilone.bingobreed.shared.generated.resources.baffeur
import fr.lilone.bingobreed.shared.generated.resources.caresseur
import fr.lilone.bingobreed.shared.generated.resources.endurance
import fr.lilone.bingobreed.shared.generated.resources.maturite
import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
import org.jetbrains.compose.resources.DrawableResource

/**
 * Mapping centralisé domaine → ressource graphique, pour garder les écrans propres.
 * Les icônes sont nommées par **effet** ; le modèle indexe les jauges carburant par
 * **ordinal d'item** (enum hhc), d'où la table ci-dessous.
 */
object AppIcons {

    /**
     * Icône d'une jauge de carburant par ordinal d'élément (0..5), ou `null` quand on
     * rend un texte à la place (Mangeoire → « XP »).
     */
    fun fuel(element: Int): DrawableResource? = when (element) {
        0 -> Res.drawable.baffeur     // baisse la sérénité
        1 -> Res.drawable.caresseur   // augmente la sérénité
        2 -> Res.drawable.endurance   // foudroyeur → endurance
        3 -> Res.drawable.maturite    // abreuvoir → maturité
        4 -> Res.drawable.amour       // dragofesse → amour
        else -> null                  // 5 mangeoire → texte « XP »
    }

    /** Icône d'une jauge de monture (amour/maturité/endurance). */
    fun gauge(type: Int): DrawableResource = when (type) {
        MountGauge.TYPE_LOVE -> Res.drawable.amour
        MountGauge.TYPE_MATURITY -> Res.drawable.maturite
        else -> Res.drawable.endurance
    }

    /** Caresseur : élément d'enclos qui **augmente** la sérénité. */
    val serenityUp: DrawableResource = Res.drawable.caresseur

    /** Baffeur : élément d'enclos qui **baisse** la sérénité. */
    val serenityDown: DrawableResource = Res.drawable.baffeur
}
