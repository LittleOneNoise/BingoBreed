package fr.lilone.bingobreed.sniffer.parser.breeding

import com.google.protobuf.Descriptors
import com.google.protobuf.Message
import fr.lilone.bingobreed.sniffer.model.DecodedGameAny
import fr.lilone.bingobreed.sniffer.model.breeding.Achievement
import fr.lilone.bingobreed.sniffer.model.breeding.AchievementObjective

/**
 * Transforme les messages de succès en [Achievement].
 *  - `meu` (C→S) : requête de détail d'une catégorie — porte l'**id de catégorie**.
 *  - `mey` (S→C) : liste détaillée des succès de la **dernière catégorie demandée**.
 *  - `mff` (S→C) : liste générale (succès presque terminés / en cours), non catégorisée.
 *
 * ⚠️ **Couche fragile au patch** (cf. [PaddockMapper]) : les numéros de champ ([F]) viennent
 * d'`output.proto` et sont le seul point à resynchroniser après une MAJ Dofus.
 * Resynchronisé au patch 2026-07 (client 3.6.6.6).
 */
object AchievementMapper {

    const val CODE_CATEGORY_REQ = "meu" // C→S : requête détail catégorie (porte l'id, meu.gfpd)
    const val CODE_DETAILED = "mey"     // S→C : liste détaillée d'une catégorie
    const val CODE_LIST = "mff"         // S→C : liste générale (en cours)

    /**
     * Numéros de champ wire — UNIQUE point de resynchronisation. Noms **sémantiques** stables ; ne
     * mettre à jour que la **valeur** + le commentaire d'identité (`message.champ`). Patch 2026-07.
     */
    private object F {
        const val REQUEST_CATEGORY = 1   // meu.gfpd (id de catégorie demandée ; 78/79/80 = dragodinde/muldo/
                                         // volkorne, 119 = élevage général — capture 2026-07)
        // Liste détaillée : `mey` a deux listes `mfi` — les deux sont lues
        const val DETAILED_LIST_A = 1    // mey.gfpq  rep mfi
        const val DETAILED_LIST_B = 2    // mey.gfpr  rep mfi
        const val OVERVIEW_LIST = 1      // mff.gfqx  rep mfi (vue d'ensemble)
        // Succès (mfi)
        const val ACH_ID = 2             // mfi.gfrj
        const val ACH_OBJECTIVES = 1     // mfi.gfri  rep mfg
        // Objectif (mfg)
        const val OBJ_ID = 1             // mfg.gfrb
        const val OBJ_CURRENT = 2        // mfg.gfrc (optional ; absent = terminé)
        const val OBJ_TARGET = 3         // mfg.gfre (cible)
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
