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
     * Resynchronisés au patch 2026-06 (cf. README §2). Les noms obfusqués sont relevés dans les
     * logs de diagnostic puis croisés avec `output.proto` pour leurs numéros de champ.
     */
    const val CODE_CONTENT = "hrk"        // S→C : push complet du contenu de l'enclos (fnke→fnjw→htu)
    const val CODE_UPDATE = "hpv"         // S→C : push de mise à jour de l'enclos (fnfr→htu)
    const val CODE_SELECT = "hsi"         // C→S : sélection d'un enclos (porte l'index)
    const val CODE_TRANSFER = "hqb"       // S→C : changement d'emplacement de montures (enclos↔étable)
    const val CODE_STABLE = "hqp"         // S→C : contenu de l'étable (réponse à `htf`)
    const val CODE_GAUGE_ON = "hse"       // C→S : activer une jauge
    const val CODE_GAUGE_OFF = "hsr"      // C→S : désactiver une jauge
    const val CODE_GAUGE_ON_RESP = "hpr"  // S→C : réponse d'activation (jauges auto-désactivées)

    /**
     * Numéros de champ wire — UNIQUE point de resynchronisation après un patch. Noms **sémantiques**
     * (jamais à renommer) ; ne mettre à jour que la **valeur** et le **commentaire d'identité**
     * (`message.champ`). Identité courante : patch 2026-06.
     */
    private object F {
        // Contenu complet d'enclos : `hrk`.fnke(3) → hri.fnjw(2) → htu
        const val CONTENT_WRAPPER = 3     // hrk.fnke → hri
        const val CONTENT_BODY = 2        // hri.fnjw → htu (jauges + montures)
        // Mise à jour d'enclos : `hpv`.fnfr(2) → htu
        const val UPDATE_BODY = 2         // hpv.fnfr → htu
        // Sélection d'enclos : `hsi`.fnng(3) int32 (index 1..6)
        const val SELECT_INDEX = 3        // hsi.fnng
        // Transfert de montures : `hqb`.fngm(2) map<string,hpz> (clé = UUID)
        const val TRANSFER_MAP = 2        // hqb.fngm
        // (Dés)activation d'une jauge : élément hpd
        const val GAUGE_ON_ELEMENT = 2    // hse.fnmw
        const val GAUGE_OFF_ELEMENT = 3   // hsr.fnoh
        // Réponse d'activation (jauges auto-désactivées) : `hpr`.fnfc(2) → hpp.fnev(2) rep hpd
        const val GAUGE_RESP_BODY = 2     // hpr.fnfc → hpp  (⚠️ liste à confirmer, capture vide)
        const val GAUGE_RESP_ELEMENTS = 2 // hpp.fnev
        // Étable : `hqp`.fnhu(1) → hqn ; montures dans fnho(1) et/ou fnhq(3) <string,hsx>
        const val STABLE_WRAPPER = 1      // hqp.fnhu → hqn
        const val STABLE_MOUNTS_A = 1     // hqn.fnho
        const val STABLE_MOUNTS_B = 3     // hqn.fnhq
        // Contenu d'enclos (htu)
        const val ACTIVE_ELEMENTS = 4     // htu.fnsn  rep hpd
        const val FUEL_GAUGES = 2         // htu.fnsl  rep hrm
        const val MOUNTS = 5              // htu.fnso  map<string,hsx>
        // Jauge carburant (hrm)
        const val FUEL_VALUE = 2          // hrm.fnkn
        const val FUEL_ELEMENT = 3        // hrm.fnko  hpd
        // Monture (hsx) — cf. README §2. Ints désambiguïsés par plage ; bools par sanity.
        const val MOUNT_APPEARANCE = 3    // hsx.fnpj
        const val MOUNT_NAME = 4          // hsx.fnpk (seul string)
        const val MOUNT_LEVEL = 5         // hsx.fnpm (seul int ≤ 200)
        const val MOUNT_XP = 11           // hsx.fnps (≈ total jauges ; non critique)
        const val MOUNT_PARENTS = 2       // hsx.fnpi (sous-msg hsv)
        const val MOUNT_EFFECTS = 13      // hsx.fnpu
        const val MOUNT_SERENITY = 7      // hsx.fnpo (seul int dans ±5000)
        const val MOUNT_STERILE = 9       // hsx.fnpq (true ; n'apparaît que sur jauges max)
        const val MOUNT_SEX = 8           // hsx.fnpp (true=mâle ; fnpn=6 = bool inconnu)
        const val MOUNT_GAUGES = 10       // hsx.fnpr  rep hsu
        // Généalogie (hsv)
        const val PARENT_1 = 1            // hsv.fnpb
        const val PARENT_2 = 2            // hsv.fnpc
        // Jauge monture (hsu)
        const val MGAUGE_TYPE = 1         // hsu.fnow  hpe
        const val MGAUGE_VALUE = 2        // hsu.fnox
        // Effet (lip)
        const val EFFECT_ID = 11          // lip.gbpd
        const val EFFECT_VALUE = 10       // lip.gbpo (valeur simple ; complexe = gbpl=7)
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
