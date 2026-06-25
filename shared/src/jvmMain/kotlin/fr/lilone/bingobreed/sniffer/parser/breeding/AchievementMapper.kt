package fr.lilone.bingobreed.sniffer.parser.breeding

import com.google.protobuf.Descriptors
import com.google.protobuf.Message
import fr.lilone.bingobreed.sniffer.model.DecodedGameAny
import fr.lilone.bingobreed.sniffer.model.breeding.Achievement
import fr.lilone.bingobreed.sniffer.model.breeding.AchievementObjective

/**
 * Transforme les messages de succès en [Achievement].
 *  - `mea` (C→S) : requête de détail d'une catégorie — porte l'**id de catégorie**.
 *  - `mdu` (S→C) : liste détaillée des succès de la **dernière catégorie demandée**.
 *  - `mdz` (S→C) : liste générale (succès presque terminés / en cours), non catégorisée.
 *
 * ⚠️ **Couche fragile au patch** (cf. [PaddockMapper]) : les numéros de champ ([F]) viennent
 * d'`output.proto` et sont le seul point à resynchroniser après une MAJ Dofus.
 * Resynchronisé au patch 2026-06.
 */
object AchievementMapper {

    const val CODE_CATEGORY_REQ = "mea" // C→S : requête détail catégorie (porte l'id)
    const val CODE_DETAILED = "mdu"     // S→C : liste détaillée d'une catégorie
    const val CODE_LIST = "mdz"         // S→C : liste générale (en cours)

    /**
     * Numéros de champ wire — UNIQUE point de resynchronisation. Noms **sémantiques** stables ; ne
     * mettre à jour que la **valeur** + le commentaire d'identité (`message.champ`). Patch 2026-06.
     */
    private object F {
        const val REQUEST_CATEGORY = 1   // mea.gexi (id de catégorie demandée)
        // Liste détaillée : `mdu` a deux listes `mds` — les deux sont lues
        const val DETAILED_LIST_A = 1    // mdu.gewc  rep mds
        const val DETAILED_LIST_B = 2    // mdu.gewd  rep mds
        const val OVERVIEW_LIST = 1      // mdz.gexe  rep mds (vue d'ensemble)
        // Succès (mds)
        const val ACH_ID = 1             // mds.gevr
        const val ACH_OBJECTIVES = 2     // mds.gevs  rep mdq
        // Objectif (mdq)
        const val OBJ_TARGET = 2         // mdq.gevk (cible)
        const val OBJ_ID = 3             // mdq.gevl
        const val OBJ_CURRENT = 4        // mdq.gevm (optional ; absent = terminé)
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
        return (msg.messageList(F.DETAILED_LIST_A) + msg.messageList(F.DETAILED_LIST_B))
            .map { toAchievement(it, categoryId) }
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

    /** null si le champ optional (ex. `fthm`) est absent → objectif terminé. */
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
