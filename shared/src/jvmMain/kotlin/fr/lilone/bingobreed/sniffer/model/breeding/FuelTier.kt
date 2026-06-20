package fr.lilone.bingobreed.sniffer.model.breeding

/**
 * Palier d'efficacité d'une jauge de carburant — **dérivé client** (pas un champ réseau).
 *
 * La jauge va de 0 à [FUEL_MAX]. Quand l'enclos contient au moins une monture, chaque
 * jauge **active** consomme [drainPer10s] toutes les 10 s et les montures gagnent autant
 * dans la jauge correspondante. Plus le palier est haut, plus le gain est rapide
 * ([multiplier] ×1..×4) — mais plus la jauge se vide vite.
 */
enum class FuelTier(val min: Int, val max: Int, val drainPer10s: Int, val multiplier: Int) {
    T1(0, 40_000, 10, 1),
    T2(40_001, 70_000, 20, 2),
    T3(70_001, 90_000, 30, 3),
    T4(90_001, 100_000, 40, 4);

    companion object {
        const val FUEL_MAX = 100_000

        fun of(value: Int): FuelTier {
            val v = value.coerceIn(0, FUEL_MAX)
            return entries.first { v in it.min..it.max }
        }

        /**
         * Temps (s) avant que la jauge ne retombe au palier inférieur, en supposant la jauge
         * active et l'enclos peuplé. Pour [T1], temps avant vidage complet.
         */
        fun secondsToTierDrop(value: Int): Int {
            val tier = of(value)
            val above = value.coerceIn(0, FUEL_MAX) - tier.min
            return (above.toLong() * 10 / tier.drainPer10s).toInt()
        }
    }
}
