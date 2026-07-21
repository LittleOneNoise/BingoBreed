package fr.lilone.bingobreed.sniffer.parser.breeding

import com.google.protobuf.Descriptors
import com.google.protobuf.Message
import fr.lilone.bingobreed.sniffer.model.DecodedGameAny
import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.FuelGauge
import fr.lilone.bingobreed.sniffer.model.breeding.Mount
import fr.lilone.bingobreed.sniffer.model.breeding.MountEffect
import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
import fr.lilone.bingobreed.sniffer.model.breeding.Paddock
import fr.lilone.bingobreed.sniffer.model.breeding.Sex

/**
 * Transforme le [Message] (DynamicMessage) d'un message d'élevage en [Paddock] / montures.
 *
 * ⚠️ **Couche fragile au patch.** Toute l'identité **wire** obfusquée (codes `type_url` 3 lettres
 * dans [CODE_CONTENT] etc., numéros de champ dans [F]) est isolée ici : c'est le **seul** endroit
 * à resynchroniser après une MAJ Dofus. Les **noms** de constantes sont **sémantiques et stables**
 * (ex. [F.MOUNTS], pas `HTU_MOUNTS`) → un patch ne change que des **valeurs** + leur commentaire
 * d'identité, jamais le corps des mappers ni les appelants. Le reste (modèle de domaine,
 * descripteur, DynamicMessage) est robuste.
 */
object PaddockMapper {

    /**
     * Codes type_url (sans préfixe `type.ankama.com/`). À ré-identifier après patch.
     * Resynchronisés au patch 2026-07 (client 3.6.6.6). Les noms obfusqués sont relevés dans les
     * logs de diagnostic puis croisés avec `output.proto` pour leurs numéros de champ.
     */
    const val CODE_CONTENT = "hss"        // S→C : contenu d'enclos sur sélection (hss.fnan(3)→hsq.fnag(2)=huh)
    const val CODE_UPDATE = "htr"         // S→C : push de mise à jour de l'enclos (htr.fnek(1)=huh)
    const val CODE_SELECT = "hsg"         // C→S : sélection d'un enclos (porte l'index, hsg.fmzd(2))
    const val CODE_TRANSFER = "hsw"       // S→C : changement d'emplacement de montures (hsw.fnbd map<uuid,hst>)
    const val CODE_STABLE = "hrp"         // S→C : collection complète des montures (hrp.fmxg→hrn, maps fmwz/fmxb)
    const val CODE_GAUGE_ON = "hqa"       // C→S : activer une jauge (hqa.fmtj hpo)
    const val CODE_GAUGE_OFF = "hts"      // C→S : désactiver une jauge (hts.fneo/fnep hpo)
    const val CODE_GAUGE_ON_RESP = "hsm"  // S→C : réponse d'activation (jauges auto-désactivées)
    const val CODE_BREED = "htf"          // C→S : clic « Accoupler » (porte les 2 UUID parents, htf.fnbz/fncc)
    const val CODE_BREED_RESULT = "hqq"   // S→C : résultat d'accouplement (parents à jour + nouveau-né)
    const val CODE_CLONE_RESULT = "htb"   // S→C : monture obtenue par clonage (« dupliquer »)
    const val CODE_PADDOCK_LIST = "huf"   // S→C : état verrouillé/déverrouillé des 6 enclos, event poussé à
                                           // l'ouverture de l'écran d'élevage (déclenché par `htz`, requête vide)

    /**
     * Numéros de champ wire — UNIQUE point de resynchronisation après un patch. Noms **sémantiques**
     * (jamais à renommer) ; ne mettre à jour que la **valeur** et le **commentaire d'identité**
     * (`message.champ`). Identité courante : patch 2026-06.
     */
    private object F {
        // Contenu complet d'enclos : `hss`.fnan(3) → hsq.fnag(2) → huh
        const val CONTENT_WRAPPER = 3     // hss.fnan → hsq
        const val CONTENT_BODY = 2        // hsq.fnag → huh (jauges + montures)
        // Mise à jour d'enclos : `htr`.fnek(1) → huh
        const val UPDATE_BODY = 1         // htr.fnek → huh
        // Sélection d'enclos : `hsg`.fmzd(2) int32 (index 1..6)
        const val SELECT_INDEX = 2        // hsg.fmzd
        // Transfert de montures : `hsw`.fnbd(1) map<string,hst> (clé = UUID)
        const val TRANSFER_MAP = 1        // hsw.fnbd
        // (Dés)activation d'une jauge : élément hpo
        const val GAUGE_ON_ELEMENT = 1    // hqa.fmtj
        const val GAUGE_OFF_ELEMENT = 2   // hts.fnep (l'élément désactivé ; fneo(1) reste vide) ✓
        // Accouplement : `htf` porte les 2 UUID parents (string), fnbz(1) et fncc(3)
        const val BREED_PARENT_1 = 1      // htf.fnbz
        const val BREED_PARENT_2 = 3      // htf.fncc
        // Résultat d'accouplement : `hqq`.fmva(2) → hqo ; parents fmur(3), nouveau-né fmuu(6) <string,hty>
        const val BREED_RESULT_BODY = 2   // hqq.fmva → hqo
        const val BREED_RESULT_PARENTS = 3 // hqo.fmur  map<string,hty>
        const val BREED_RESULT_CHILD = 6  // hqo.fmuu  map<string,hty>
        // Résultat de clonage : `htb`.fnbo(1) → hsz{fnbi(1)=uuid, fnbj(2)=hty}
        const val CLONE_RESULT_BODY = 1   // htb.fnbo → hsz
        const val CLONE_UUID = 1          // hsz.fnbi
        const val CLONE_MOUNT = 2         // hsz.fnbj  hty
        // Réponse d'activation (jauges auto-désactivées) : `hsm`.fmzu(1) → hsk.fmzq(1) rep hpo
        const val GAUGE_RESP_BODY = 1     // hsm.fmzu → hsk
        const val GAUGE_RESP_ELEMENTS = 1 // hsk.fmzq
        // Collection montures : `hrp`.fmxg(1) → hrn ; montures dans fmwz(1) et/ou fmxb(3) <string,hty>
        const val STABLE_WRAPPER = 1      // hrp.fmxg → hrn
        const val STABLE_MOUNTS_A = 1     // hrn.fmwz
        const val STABLE_MOUNTS_B = 3     // hrn.fmxb
        // Liste des enclos : `huf`.fngx(2) map<int32,bool> (index 1..6 → déverrouillé). Confirmé en jeu
        // (capture 2026-07 : 5 enclos à true, le 6ᵉ à false, cohérent avec le palier "tous les 40 niveaux
        // d'éleveur"). `huf`.fngw(1) existe aussi (map<int32,bool>) mais sa sémantique n'est pas identifiée
        // — non mappé pour l'instant.
        const val PADDOCK_UNLOCKED = 2    // huf.fngx
        // Contenu d'enclos (huh)
        const val ACTIVE_ELEMENTS = 2     // huh.fnhh  rep hpo
        const val FUEL_GAUGES = 1         // huh.fnhg  rep hqg
        const val MOUNTS = 3              // huh.fnhi  map<string,hty>
        // Jauge carburant (hqg)
        const val FUEL_VALUE = 2          // hqg.fmua
        const val FUEL_ELEMENT = 1        // hqg.fmtz  hpo
        // Monture (hty). Ints désambiguïsés par plage ; bools par sanity.
        const val MOUNT_APPEARANCE = 1    // hty.fnfq
        const val MOUNT_NAME = 7          // hty.fnfw (seul string)
        const val MOUNT_LEVEL = 3         // hty.fnfs (⚠️ int candidat niveau ; à confirmer sur montures connues)
        const val MOUNT_XP = 11           // hty.fngb (≈ total jauges ; non critique)
        const val MOUNT_PARENTS = 10      // hty.fnga (sous-msg htw)
        const val MOUNT_EFFECTS = 9       // hty.fnfz  rep ldk
        const val MOUNT_SERENITY = 12     // hty.fngc (int, peut être négatif)
        const val MOUNT_STERILE = 4       // hty.fnft (true=stérile ; présent sur muldo stérile, absent sur féconde ✓)
        const val MOUNT_SEX = 2           // hty.fnfr (true=un sexe ; diffère entre les 2 parents accouplés ✓)
        const val MOUNT_GAUGES = 5        // hty.fnfu  rep htv
        // Généalogie (htw)
        const val PARENT_1 = 2            // htw.fnfl
        const val PARENT_2 = 3            // htw.fnfm
        // Jauge monture (htv)
        const val MGAUGE_TYPE = 3         // htv.fnfg  hpp (fnfe(1) = 2ᵉ hpp, non discriminant)
        const val MGAUGE_VALUE = 2        // htv.fnff
        // Effet (ldk)
        const val EFFECT_ID = 2           // ldk.gahn
        const val EFFECT_VALUE = 3        // ldk.gahr (valeur simple ; complexe = gaia=10 ldd)
        // Entrée de map protobuf (standard)
        const val MAP_KEY = 1
        const val MAP_VALUE = 2
    }

    /** Dispatcher depuis un message de jeu décodé ; null si ce n'est pas un enclos. */
    fun fromGameMessage(message: DecodedGameAny, dynamic: Message?): Paddock? {
        val msg = dynamic ?: return null
        return when (message.code) {
            CODE_CONTENT -> msg.msg(F.CONTENT_WRAPPER)?.msg(F.CONTENT_BODY)?.let(::fromContent)
            CODE_UPDATE -> msg.msg(F.UPDATE_BODY)?.let(::fromContent)
            else -> null
        }
    }

    /** Index d'enclos (1..6) d'une requête de sélection, ou null si autre message. */
    fun selectedPaddockIndex(message: DecodedGameAny, dynamic: Message?): Int? {
        if (message.code != CODE_SELECT) return null
        return dynamic?.intOrNull(F.SELECT_INDEX)
    }

    /**
     * UUIDs des montures dont l'emplacement vient de changer (réponse de transfert), ou null si ce
     * n'est pas ce message. Le sens (entrée/sortie d'enclos) se déduit côté appelant via l'état.
     */
    fun transferredMountIds(message: DecodedGameAny, dynamic: Message?): Set<String>? {
        if (message.code != CODE_TRANSFER) return null
        val msg = dynamic ?: return null
        return msg.messageList(F.TRANSFER_MAP).mapNotNull { it.str(F.MAP_KEY) }.toSet().ifEmpty { null }
    }

    /**
     * UUIDs des 2 montures **venant d'être accouplées** (clic « Accoupler », `htq`), ou null si ce
     * n'est pas ce message. Les parents sont alors **consommés** (jauges remises à zéro côté jeu) :
     * l'appelant les retire du calcul du planificateur jusqu'à un état frais qui les rétablit.
     */
    fun bredPair(message: DecodedGameAny, dynamic: Message?): Set<String>? {
        if (message.code != CODE_BREED) return null
        val msg = dynamic ?: return null
        return setOfNotNull(msg.str(F.BREED_PARENT_1), msg.str(F.BREED_PARENT_2)).ifEmpty { null }
    }

    /**
     * Montures issues d'un **accouplement** (`hqq`) indexées par UUID : le **nouveau-né** ainsi que
     * l'état **à jour des 2 parents** (post-repro : jauges remises à zéro, stérilité éventuelle).
     * null si ce n'est pas ce message. Sert à garder l'étable fraîche sans attendre un nouveau push
     * complet — `hrp` (collection) n'arrive qu'à la 1ʳᵉ ouverture de l'interface d'élevage.
     */
    fun bredOffspring(message: DecodedGameAny, dynamic: Message?): Map<String, Mount>? {
        if (message.code != CODE_BREED_RESULT) return null
        val body = dynamic?.msg(F.BREED_RESULT_BODY) ?: return null
        val entries = body.messageList(F.BREED_RESULT_PARENTS) + body.messageList(F.BREED_RESULT_CHILD)
        return entries.mapNotNull { entry ->
            val uuid = entry.str(F.MAP_KEY) ?: return@mapNotNull null
            val value = entry.msg(F.MAP_VALUE) ?: return@mapNotNull null
            uuid to toMount(uuid, value)
        }.toMap().ifEmpty { null }
    }

    /**
     * Monture obtenue par **clonage** (`htb`) indexée par UUID, ou null si autre message. Comme
     * [bredOffspring], maintient l'étable à jour entre deux push complets.
     */
    fun clonedMount(message: DecodedGameAny, dynamic: Message?): Map<String, Mount>? {
        if (message.code != CODE_CLONE_RESULT) return null
        val hsz = dynamic?.msg(F.CLONE_RESULT_BODY) ?: return null
        val uuid = hsz.str(F.CLONE_UUID) ?: return null
        val value = hsz.msg(F.CLONE_MOUNT) ?: return null
        return mapOf(uuid to toMount(uuid, value))
    }

    /** Élément de jauge venant d'être **activé**, ou null si autre message. */
    fun activatedElement(message: DecodedGameAny, dynamic: Message?): Int? {
        if (message.code != CODE_GAUGE_ON) return null
        return dynamic?.enumNumber(F.GAUGE_ON_ELEMENT)?.takeIf { it >= 0 }
    }

    /** Élément de jauge venant d'être **désactivé**, ou null si autre message. */
    fun deactivatedElement(message: DecodedGameAny, dynamic: Message?): Int? {
        if (message.code != CODE_GAUGE_OFF) return null
        return dynamic?.enumNumber(F.GAUGE_OFF_ELEMENT)?.takeIf { it >= 0 }
    }

    /**
     * Jauges **auto-désactivées** par le serveur suite à une activation : application des règles
     * « 1 jauge de sérénité » et « 2 jauges max » (éviction FIFO). Liste vide si rien n'a été
     * désactivé ; null si ce n'est pas la réponse d'activation.
     */
    fun autoDeactivatedElements(message: DecodedGameAny, dynamic: Message?): List<Int>? {
        if (message.code != CODE_GAUGE_ON_RESP) return null
        val body = dynamic?.msg(F.GAUGE_RESP_BODY) ?: return emptyList()
        return body.enumList(F.GAUGE_RESP_ELEMENTS)
    }

    /**
     * État verrouillé/déverrouillé des 6 enclos (`huf`), indexé par numéro d'enclos (1..6) →
     * déverrouillé, ou null si ce n'est pas ce message. Seule source **fiable** du nombre d'enclos
     * débloqués : [fromGameMessage]/[paddocks] ne connaissent que les enclos déjà **ouverts** par le
     * joueur, ce qui sous-estime tant que tous les onglets n'ont pas été visités.
     */
    fun unlockedPaddocks(message: DecodedGameAny, dynamic: Message?): Map<Int, Boolean>? {
        if (message.code != CODE_PADDOCK_LIST) return null
        val msg = dynamic ?: return null
        return msg.messageList(F.PADDOCK_UNLOCKED)
            .associate { entry -> entry.int(F.MAP_KEY) to entry.bool(F.MAP_VALUE) }
            .ifEmpty { null }
    }

    /** Montures de l'étable indexées par UUID, ou null si autre message / étable absente. */
    fun stableMounts(message: DecodedGameAny, dynamic: Message?): Map<String, Mount>? {
        if (message.code != CODE_STABLE) return null
        val wrapper = dynamic?.msg(F.STABLE_WRAPPER) ?: return null
        val entries = wrapper.messageList(F.STABLE_MOUNTS_A) + wrapper.messageList(F.STABLE_MOUNTS_B)
        return entries.mapNotNull { entry ->
            val uuid = entry.str(F.MAP_KEY) ?: return@mapNotNull null
            val value = entry.msg(F.MAP_VALUE) ?: return@mapNotNull null
            uuid to toMount(uuid, value)
        }.toMap().ifEmpty { null }
    }

    /** Cœur du mapping : le contenu d'enclos (jauges + montures) → [Paddock]. */
    fun fromContent(content: Message): Paddock = Paddock(
        activeElements = content.enumList(F.ACTIVE_ELEMENTS),
        fuelGauges = content.messageList(F.FUEL_GAUGES).map { gauge ->
            FuelGauge(element = gauge.enumNumber(F.FUEL_ELEMENT), value = gauge.int(F.FUEL_VALUE))
        },
        mounts = content.messageList(F.MOUNTS).mapNotNull { entry ->
            val uuid = entry.str(F.MAP_KEY) ?: return@mapNotNull null
            val value = entry.msg(F.MAP_VALUE) ?: return@mapNotNull null
            uuid to toMount(uuid, value)
        }.toMap(),
    )

    private fun toMount(uuid: String, m: Message): Mount {
        val gauges = m.messageList(F.MOUNT_GAUGES).map { gauge ->
            MountGauge(type = gauge.enumNumber(F.MGAUGE_TYPE), value = gauge.int(F.MGAUGE_VALUE))
        }
        val sterile = m.bool(F.MOUNT_STERILE)
        return Mount(
            uuid = uuid,
            name = m.str(F.MOUNT_NAME),
            level = m.int(F.MOUNT_LEVEL),
            experience = m.int(F.MOUNT_XP),
            serenity = m.int(F.MOUNT_SERENITY),
            sex = if (m.bool(F.MOUNT_SEX)) Sex.MALE else Sex.FEMALE,
            sterile = sterile,
            fertility = Fertility.of(sterile, gauges),
            appearanceId = m.int(F.MOUNT_APPEARANCE),
            parents = m.msg(F.MOUNT_PARENTS)?.let { p ->
                listOfNotNull(p.intOrNull(F.PARENT_1), p.intOrNull(F.PARENT_2))
            } ?: emptyList(),
            gauges = gauges,
            effects = m.messageList(F.MOUNT_EFFECTS).map { effect ->
                MountEffect(effectId = effect.int(F.EFFECT_ID), value = effect.intOrNull(F.EFFECT_VALUE))
            },
        )
    }

    // --- Helpers de lecture DynamicMessage par numéro de champ ---

    private fun Message.field(n: Int): Descriptors.FieldDescriptor? =
        descriptorForType.findFieldByNumber(n)

    private fun Message.msg(n: Int): Message? {
        val f = field(n) ?: return null
        if (f.isRepeated || !hasField(f)) return null
        return getField(f) as? Message
    }

    private fun Message.int(n: Int): Int {
        val f = field(n) ?: return 0
        if (f.isRepeated) return 0
        return (getField(f) as? Number)?.toInt() ?: 0
    }

    /** Comme [int] mais null si le champ (souvent un membre de oneof) n'est pas présent. */
    private fun Message.intOrNull(n: Int): Int? {
        val f = field(n) ?: return null
        if (f.isRepeated || !hasField(f)) return null
        return (getField(f) as? Number)?.toInt()
    }

    /** Bool proto3 sans présence : getField renvoie le défaut (false) si absent. */
    private fun Message.bool(n: Int): Boolean {
        val f = field(n) ?: return false
        if (f.isRepeated) return false
        return (getField(f) as? Boolean) ?: false
    }

    private fun Message.str(n: Int): String? {
        val f = field(n) ?: return null
        if (f.isRepeated) return null
        return getField(f) as? String
    }

    private fun Message.enumNumber(n: Int): Int {
        val f = field(n) ?: return -1
        if (f.isRepeated) return -1
        return (getField(f) as? Descriptors.EnumValueDescriptor)?.number ?: -1
    }

    @Suppress("UNCHECKED_CAST")
    private fun Message.messageList(n: Int): List<Message> {
        val f = field(n) ?: return emptyList()
        if (!f.isRepeated) return emptyList()
        return getField(f) as List<Message>
    }

    private fun Message.enumList(n: Int): List<Int> {
        val f = field(n) ?: return emptyList()
        if (!f.isRepeated) return emptyList()
        return (getField(f) as List<*>).mapNotNull { (it as? Descriptors.EnumValueDescriptor)?.number }
    }
}
