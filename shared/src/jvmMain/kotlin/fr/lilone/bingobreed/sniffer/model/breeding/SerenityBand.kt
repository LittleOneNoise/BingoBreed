package fr.lilone.bingobreed.sniffer.model.breeding

/**
 * Bande de sérénité — **dérivé client** (pas un champ réseau).
 *
 * La sérénité ([Mount.serenity]) va de [MIN] à [MAX] et détermine **quelles jauges la
 * monture peut gagner**. Les règles in-game se chevauchent (endurance sur sérénité
 * négative, maturité au centre, amour sur sérénité positive) ; leur combinaison donne
 * 4 bandes, chacune affichée en jeu par un smiley coloré :
 *  - [GREEN] :D  amour
 *  - [PURPLE] :) amour + maturité
 *  - [BLUE] :(   endurance + maturité
 *  - [RED] :C    endurance
 *
 * [enables] liste les types de [MountGauge] que la bande autorise à monter.
 */
enum class SerenityBand(val min: Int, val max: Int, val enables: Set<Int>) {
    RED(-5000, -2001, setOf(MountGauge.TYPE_ENDURANCE)),
    BLUE(-2000, -1, setOf(MountGauge.TYPE_ENDURANCE, MountGauge.TYPE_MATURITY)),
    PURPLE(0, 2000, setOf(MountGauge.TYPE_MATURITY, MountGauge.TYPE_LOVE)),
    GREEN(2001, 5000, setOf(MountGauge.TYPE_LOVE));

    companion object {
        const val MIN = -5000
        const val MAX = 5000

        fun of(serenity: Int): SerenityBand {
            val s = serenity.coerceIn(MIN, MAX)
            return entries.first { s in it.min..it.max }
        }
    }
}
