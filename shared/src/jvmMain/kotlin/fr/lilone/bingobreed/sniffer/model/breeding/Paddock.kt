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
)

/** Une jauge de carburant d'enclos (0..100000). */
data class FuelGauge(
    /** Ordinal de l'élément (enum hhc, 0..5) — à nommer (feu/eau/terre/air/…). */
    val element: Int,
    /** Valeur de remplissage (0..100000). */
    val value: Int,
)

/**
 * Une monture telle que vue dans le résumé d'enclos (`hlo`).
 *
 * Note : génération, couleur, fertilité et généalogie ne figurent PAS dans ce
 * résumé — elles proviendront d'un message de détail (au clic sur la monture),
 * à identifier ultérieurement.
 */
data class Mount(
    val uuid: String,
    /** Nom personnalisé, si défini (champ feaj). */
    val name: String?,
    /** Niveau (champ fean — confirmé). */
    val level: Int,
    /** Expérience (champ feao — confirmé). */
    val experience: Int,
    /** Sérénité, signée (champ feas — confirmé). */
    val serenity: Int,
    /** Jauges amour/maturité/endurance (champ feav). */
    val gauges: List<MountGauge>,
    /** Effets/bonus (champ fear) : PM, puissance, fuite… */
    val effects: List<MountEffect>,
)

/** Une jauge de monture. */
data class MountGauge(
    /** Ordinal du type (enum hhd) : 0 = amour (confirmé) ; 1/2 = maturité/endurance (à départager). */
    val type: Int,
    val value: Int,
) {
    companion object {
        const val TYPE_LOVE = 0 // HHD_DXNX, confirmé (amour)
    }
}

/** Un effet de monture : id d'effet + valeur (ex. 138=Puissance, 128=PM). */
data class MountEffect(
    /** Id d'effet Dofus (champ fppp). */
    val effectId: Int,
    /** Valeur simple si l'effet en a une (champ fpps), sinon null (effet complexe). */
    val value: Int?,
)
