package fr.lilone.bingobreed.sniffer.parser.breeding

import com.google.protobuf.Descriptors
import com.google.protobuf.Message
import fr.lilone.bingobreed.sniffer.model.DecodedGameAny
import fr.lilone.bingobreed.sniffer.model.breeding.Achievement
import fr.lilone.bingobreed.sniffer.model.breeding.AchievementObjective

/**
 * Transforme les messages de succès en [Achievement].
 *  - `mff` (C→S) : requête de détail d'une catégorie — porte l'**id de catégorie**.
 *  - `mfo` (S→C) : liste détaillée des succès de la **dernière catégorie demandée**.
 *  - `mgb` (S→C) : liste générale (succès presque terminés / en cours), non catégorisée.
 *
 * ⚠️ **Couche fragile au patch** (cf. [PaddockMapper]) : les numéros de champ ([F]) viennent
 * d'`output.proto` et sont le seul point à resynchroniser après une MAJ Dofus.
 * Resynchronisé au patch **2026-08-04**.
 */
object AchievementMapper {

    const val CODE_CATEGORY_REQ = "mff" // C→S : requête détail catégorie (porte l'id, mff.gevp)
                                        // (candidat : seul message à `int32` unique du bloc succès,
                                        // forme de l'ancien `mgi` — la capture 2026-08-04 n'a que les S→C)
    const val CODE_DETAILED = "mfo"     // S→C : liste détaillée d'une catégorie ✓ (ex-`mfn`)
    const val CODE_LIST = "mgb"         // S→C : liste générale (en cours) ✓ (ex-`mfo`)

    /**
     * Numéros de champ wire — UNIQUE point de resynchronisation. Noms **sémantiques** stables ; ne
     * mettre à jour que la **valeur** + le commentaire d'identité (`message.champ`). Patch 2026-08-04.
     */
    private object F {
        const val REQUEST_CATEGORY = 1   // mff.gevp (id de catégorie demandée ; 78/79/80 = dragodinde/muldo/
                                         // volkorne, 119 = élevage général — relevé au patch 2026-07-30)
        const val DETAILED_LIST = 2      // mfo.gewz  rep mfl (gewy(1) = bool, rôle non identifié)
        const val OVERVIEW_LIST = 1      // mgb.geyx  rep mfl (vue d'ensemble)
        // Succès (mfl) — ⚠️ l'id passe de 1 à 2 ; gewf(1) reste non identifié (jamais renseigné)
        const val ACH_ID = 2             // mfl.gewg ✓ (1496 = « Muldo : Huitième génération »)
        const val ACH_OBJECTIVES = 3     // mfl.gewh  rep mfj
        // Objectif (mfj) ✓ le triplet déjà recoupé au patch précédent — cible 1600, courant 1552,
        // id 11141 — se relit ici en {gevx=11141, gevy=1600, gewa=1552}. gevz(3) = enum mfh, non identifié.
        const val OBJ_TARGET = 2         // mfj.gevy (cible)
        const val OBJ_CURRENT = 4        // mfj.gewa (optional ; absent = terminé)
        const val OBJ_ID = 1             // mfj.gevx
    }

    /** Id de catégorie d'une requête de détail, ou null si autre message. */
    fun requestedCategory(message: DecodedGameAny, dynamic: Message?): Int? {
        if (message.code != CODE_CATEGORY_REQ) return null
        return dynamic?.intOrNull(F.REQUEST_CATEGORY)
    }

    /** Succès d'une liste détaillée (rattachés à [categoryId]), ou null si autre message. */
    fun detailedAchievements(message: DecodedGameAny, dynamic: Message?, categoryId: Int?): List<Achievement>? {
        if (message.code != CODE_DETAILED) return null
        val msg = dynamic ?: return null
        return msg.messageList(F.DETAILED_LIST).map { toAchievement(it, categoryId) }
    }

    /** Succès d'une liste générale / vue d'ensemble (sans catégorie), ou null si autre message. */
    fun listedAchievements(message: DecodedGameAny, dynamic: Message?): List<Achievement>? {
        if (message.code != CODE_LIST) return null
        val msg = dynamic ?: return null
        return msg.messageList(F.OVERVIEW_LIST).map { toAchievement(it, categoryId = null) }
    }

    private fun toAchievement(m: Message, categoryId: Int?): Achievement = Achievement(
        id = m.int(F.ACH_ID),
        categoryId = categoryId,
        objectives = m.messageList(F.ACH_OBJECTIVES).map { o ->
            AchievementObjective(
                id = o.int(F.OBJ_ID),
                current = o.longOrNull(F.OBJ_CURRENT),
                target = o.long(F.OBJ_TARGET),
            )
        },
    )

    // --- Helpers de lecture DynamicMessage par numéro de champ ---

    private fun Message.field(n: Int): Descriptors.FieldDescriptor? =
        descriptorForType.findFieldByNumber(n)

    private fun Message.int(n: Int): Int {
        val f = field(n) ?: return 0
        if (f.isRepeated) return 0
        return (getField(f) as? Number)?.toInt() ?: 0
    }

    private fun Message.intOrNull(n: Int): Int? {
        val f = field(n) ?: return null
        if (f.isRepeated || !hasField(f)) return null
        return (getField(f) as? Number)?.toInt()
    }

    private fun Message.long(n: Int): Long {
        val f = field(n) ?: return 0
        if (f.isRepeated) return 0
        return (getField(f) as? Number)?.toLong() ?: 0
    }

    /** null si le champ optional (`mfj.gewa`) est absent → objectif terminé. */
    private fun Message.longOrNull(n: Int): Long? {
        val f = field(n) ?: return null
        if (f.isRepeated || !hasField(f)) return null
        return (getField(f) as? Number)?.toLong()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Message.messageList(n: Int): List<Message> {
        val f = field(n) ?: return emptyList()
        if (!f.isRepeated) return emptyList()
        return getField(f) as List<Message>
    }
}
