package fr.lilone.bingobreed.sniffer.model.breeding

/**
 * Modèle de domaine **stable** de l'élevage — découplé des numéros de champ
 * protobuf. Le mapping wire → domaine ([fr.lilone.bingobreed.sniffer.parser.breeding.PaddockMapper])
 * est la seule pièce à resynchroniser après un patch ; ces classes ne bougent pas.
 * C'est l'API consommée par la logique du bot / l'UI.
 */

/** Un enclos (paddock) : ses jauges de carburant et les montures qu'il contient. */
data class Paddock(
    /** Éléments des jauges actuellement activées (max 2). Ordinaux de l'enum hhc. */
    val activeElements: List<Int>,
    /** Les 6 jauges de carburant. */
    val fuelGauges: List<FuelGauge>,
    /** Montures présentes, indexées par UUID. */
    val mounts: Map<String, Mount>,
    /**
     * Index de l'enclos (1..6), issu de la **dernière requête de sélection `hkv`**
     * (le contenu `him` ne le transporte pas). Null tant qu'aucune sélection n'a été vue.
     */
    val id: Int? = null,
)

/** Une jauge de carburant d'enclos (0..100000). */
data class FuelGauge(
    /**
     * Ordinal de l'item de carburant (enum hhc, 0..5), dans l'ordre :
     * 0 = baffeur, 1 = caresseur, 2 = foudroyeur, 3 = abreuvoir, 4 = dragofesse, 5 = mangeoire.
     */
    val element: Int,
    /** Valeur de remplissage (0..100000). */
    val value: Int,
)

/**
 * Une monture telle que vue dans le résumé d'enclos (`hlo`).
 *
 * Note : **génération et généalogie** ne figurent PAS dans ce résumé — elles
 * proviennent d'un message de détail (au clic sur la monture), à identifier.
 * Voir `parser/breeding/README.md` pour le mapping wire et la ré-identification
 * (les noms de champ obfusqués changent à chaque patch).
 */
data class Mount(
    val uuid: String,
    /** Nom personnalisé, si défini. */
    val name: String?,
    /** Niveau (confirmé). */
    val level: Int,
    /** Expérience (confirmé). */
    val experience: Int,
    /** Sérénité, signée (confirmé). Renseignée même si stérile (l'UI la masque alors). */
    val serenity: Int,
    /** Sexe (confirmé). */
    val sex: Sex,
    /** True si la monture est stérile (confirmé). */
    val sterile: Boolean,
    /** État de fertilité — **dérivé client** de [sterile] + [gauges] (pas un champ réseau). */
    val fertility: Fertility,
    /** Id d'apparence/robe (observé ; sémantique exacte à confirmer). */
    val appearanceId: Int,
    /**
     * **Généalogie** : robes des 2 parents (sous-message `feap`, champ 7 ; `feac`=parent 1,
     * `fead`=parent 2). Mêmes ids que [appearanceId] : **espace de robes commun** (cf.
     * `Robes.IDS`). Déjà présent dans le résumé d'enclos (sans paquet réseau supplémentaire).
     * Liste vide si la monture n'a pas de généalogie (robe de base / parents inconnus).
     */
    val parents: List<Int>,
    /** Jauges amour/maturité/endurance. */
    val gauges: List<MountGauge>,
    /** Effets/bonus : PM, puissance, fuite, résistances… */
    val effects: List<MountEffect>,
)

/** Sexe d'une monture (champ wire `feau` : true = mâle). */
enum class Sex { MALE, FEMALE }

/**
 * État de fertilité — **calcul côté client**, ce n'est pas un champ réseau :
 *  - stérile → [STERILE] ;
 *  - sinon toutes les jauges au max → [FECONDE] (prête à se reproduire) ;
 *  - sinon → [FERTILE].
 *
 * Preuve : stérile et féconde ont toutes deux les jauges au max ; seul le flag
 * stérile les départage.
 */
enum class Fertility {
    FERTILE, FECONDE, STERILE;

    companion object {
        /** Valeur max d'une jauge de monture (amour/maturité/endurance). */
        const val GAUGE_MAX = 20000

        fun of(sterile: Boolean, gauges: List<MountGauge>): Fertility = when {
            sterile -> STERILE
            gauges.isNotEmpty() && gauges.all { it.value >= GAUGE_MAX } -> FECONDE
            else -> FERTILE
        }
    }
}

/** Une jauge de monture. */
data class MountGauge(
    /** Ordinal du type (enum hhd) : 0 = amour, 1 = maturité, 2 = endurance. */
    val type: Int,
    val value: Int,
) {
    companion object {
        const val TYPE_LOVE = 0       // HHD_DXNX, confirmé (amour)
        const val TYPE_MATURITY = 1
        const val TYPE_ENDURANCE = 2
    }
}

/** Un effet de monture : id d'effet + valeur (ex. 138=Puissance, 128=PM). */
data class MountEffect(
    /** Id d'effet Dofus (champ fppp). */
    val effectId: Int,
    /** Valeur simple si l'effet en a une (champ fpps), sinon null (effet complexe). */
    val value: Int?,
)
