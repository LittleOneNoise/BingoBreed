package fr.lilone.bingobreed.sniffer.model

/**
 * Version client Dofus sous forme `"a.b.c.d"`, comparable composante par composante.
 *
 * Tolère un nombre quelconque de segments ; les segments absents sont traités comme
 * `0` lors d'une comparaison (`3.5.17` == `3.5.17.0`).
 */
class DofusClientVersion private constructor(private val segments: List<Int>) : Comparable<DofusClientVersion> {

    override fun compareTo(other: DofusClientVersion): Int {
        val count = maxOf(segments.size, other.segments.size)
        for (i in 0 until count) {
            val cmp = segments.getOrElse(i) { 0 }.compareTo(other.segments.getOrElse(i) { 0 })
            if (cmp != 0) return cmp
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is DofusClientVersion && compareTo(other) == 0
    override fun hashCode(): Int = segments.dropLastWhile { it == 0 }.hashCode()
    override fun toString(): String = segments.joinToString(".")

    companion object {
        /** Parse `"3.5.17.26"` ; renvoie `null` si une composante n'est pas numérique. */
        fun parse(raw: String): DofusClientVersion? {
            val segments = raw.trim().split('.').map { it.trim().toIntOrNull() ?: return null }
            return segments.takeIf { it.isNotEmpty() }?.let(::DofusClientVersion)
        }
    }
}

/**
 * Champs extraits du fichier `version` local du client Dofus
 * (`StreamingAssets/version`, format `Clé=Valeur` ligne par ligne).
 */
data class LocalClientInfo(
    /** Valeur brute du champ `Version=` (ex. `"3.5.17.26"`), `null` si absent. */
    val version: String?,
    /** Valeur brute du champ `BuildDate=`, `null` si absent. */
    val buildDate: String?,
    /** Valeur brute du champ `ConfigUrl=`, `null` si absent. */
    val configUrl: String?,
)

/**
 * Résultat du contrôle d'écart entre la version du client Dofus installé localement
 * et la version de [reference] sur laquelle BingoBreeder a été construit à sa release.
 *
 * Un écart ([ClientAhead] / [ClientBehind]) signale que le parsing protobuf et les
 * descripteurs embarqués peuvent être désynchronisés du client réel.
 */
sealed interface VersionCheck {
    /** Version de référence de BingoBreeder, figée au build. */
    val reference: String
    /** Version brute lue dans le fichier local (`null` si illisible). */
    val local: String?

    /** Versions identiques : l'appli est alignée avec le client installé. */
    data class UpToDate(override val reference: String, override val local: String) : VersionCheck

    /** Le client local est plus récent que la référence => parsing potentiellement obsolète. */
    data class ClientAhead(override val reference: String, override val local: String) : VersionCheck

    /** Le client local est plus ancien que la référence (downgrade / build de l'appli en avance). */
    data class ClientBehind(override val reference: String, override val local: String) : VersionCheck

    /** Version locale absente ou non parsable : écart indéterminé. */
    data class Unknown(override val reference: String, override val local: String?) : VersionCheck

    /** `true` dès qu'un écart (avance, retard ou indéterminé) est constaté. */
    val hasDrift: Boolean get() = this !is UpToDate
}
