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
 * Transforme le [Message] (DynamicMessage) d'un message d'enclos en [Paddock].
 *  - code `hiy` : état complet de l'enclos
 *  - code `hle` : mise à jour de l'enclos
 *
 * ⚠️ **Couche fragile au patch.** Les numéros de champ ([F]) viennent de
 * `output.proto` (protodec) et doivent être resynchronisés après une MAJ Dofus :
 * c'est le **seul** endroit à mettre à jour. Tout le reste (modèle de domaine,
 * descripteur, DynamicMessage) est robuste.
 */
object PaddockMapper {

    /** Codes type_url (sans préfixe `type.ankama.com/`). À ré-identifier après patch. */
    const val CODE_FULL = "hiy"
    const val CODE_UPDATE = "hle"
    const val CODE_SELECT = "hkv" // C→S : sélection d'un enclos (porte l'index)
    const val CODE_TRANSFER = "hif" // S→C : changement d'emplacement de montures (enclos↔étable)
    const val CODE_STABLE = "hhv" // S→C : contenu complet de l'étable
    const val CODE_GAUGE_ON = "hhw" // C→S : activer une jauge
    const val CODE_GAUGE_OFF = "hki" // C→S : désactiver une jauge
    const val CODE_GAUGE_ON_RESP = "hjj" // S→C : réponse d'activation (jauges auto-désactivées)

    /** Numéros de champ — UNIQUE point de resynchronisation après un patch. */
    private object F {
        // hiy { fdrs=1 (enum hiw) ; fdrt=2 : him } — pas d'id d'enclos transmis
        const val HIY_CONTENT = 2
        // hle { oneof { fdzc=2 : hlc } } ; hlc { fdyv=1 (enum hhf, flag) ; fdyw=2 : him }
        const val HLE_HLC = 2
        const val HLC_CONTENT = 2
        // hkv { fdxp=1 ; oneof { fdxq=2 int32 index enclos ; fdxs=3 } }
        const val HKV_INDEX = 2
        // hif { fdpn=1 map<string,hid> ; fdpo=2 map<string,hid> } — montures déplacées (clé=UUID)
        const val HIF_MAP_A = 1
        const val HIF_MAP_B = 2
        // hhv { fdoh=3 ; oneof { fdoi=1 : hht ; fdoj=2 } } ; hht { fdoc=1 map<string,hlo> }
        const val HHV_HHT = 1
        const val HHT_MOUNTS = 1
        // hhw (activer) { fdop=1 ; fdoq=2 hhc } ; hki (désactiver) { fdvw=1 hhc }
        const val HHW_ELEMENT = 2
        const val HKI_ELEMENT = 1
        // hjj { oneof { fdsu=1 ; fdsw=3 : hjh } } ; hjh { fdsp=1 repeated hhc } — auto-désactivées
        const val HJJ_FDSW = 3
        const val FDSW_ELEMENTS = 1
        // him { fdql=1 repeated enum ; fdqm=2 repeated hhx ; fdqn=3 map<string,hlo> }
        const val HIM_ACTIVE_ELEMENTS = 1
        const val HIM_FUEL_GAUGES = 2
        const val HIM_MOUNTS = 3
        // hhx (jauge carburant) { fdov=2 valeur ; fdow=3 élément (hhc) }
        const val FUEL_VALUE = 2
        const val FUEL_ELEMENT = 3
        // hlo (monture) { feam=4 apparence ; feaj=2 nom ; fean=5 niveau ; feao=6 xp ;
        //   feap=7 couleurs ; fear=9 effets ; feas=10 sérénité ; feat=11 stérile ;
        //   feau=12 sexe(mâle) ; feav=13 jauges }
        const val MOUNT_APPEARANCE = 4
        const val MOUNT_NAME = 2
        const val MOUNT_LEVEL = 5
        const val MOUNT_XP = 6
        const val MOUNT_PARENTS = 7 // feap : robes des 2 parents (généalogie)
        const val MOUNT_EFFECTS = 9
        const val MOUNT_SERENITY = 10
        const val MOUNT_STERILE = 11
        const val MOUNT_SEX = 12
        const val MOUNT_GAUGES = 13
        // hlm (généalogie) { feac=1 robe parent 1 ; fead=2 robe parent 2 ; feae=3 inutilisé }
        const val PARENT_1 = 1
        const val PARENT_2 = 2
        // hll (jauge monture) { fdzx=1 valeur ; fdzy=2 type (hhd) }
        const val MGAUGE_VALUE = 1
        const val MGAUGE_TYPE = 2
        // kiv (effet) { fppp=8 id ; oneof { fpps=3 valeur simple } }
        const val EFFECT_ID = 8
        const val EFFECT_VALUE = 3
        // entrée de map protobuf
        const val MAP_KEY = 1
        const val MAP_VALUE = 2
    }

    /** Dispatcher depuis un message de jeu décodé ; null si ce n'est pas un enclos. */
    fun fromGameMessage(message: DecodedGameAny, dynamic: Message?): Paddock? {
        val msg = dynamic ?: return null
        return when (message.code) {
            CODE_FULL -> fromFull(msg)
            CODE_UPDATE -> fromUpdate(msg)
            else -> null
        }
    }

    /** Index d'enclos (1..6) d'une requête de sélection `hkv`, ou null si autre message. */
    fun selectedPaddockIndex(message: DecodedGameAny, dynamic: Message?): Int? {
        if (message.code != CODE_SELECT) return null
        return dynamic?.intOrNull(F.HKV_INDEX)
    }

    /**
     * UUIDs des montures dont l'emplacement vient de changer (réponse `hif`), ou null si ce
     * n'est pas un `hif`. Le sens (entrée/sortie d'enclos) se déduit côté appelant via l'état.
     */
    fun transferredMountIds(message: DecodedGameAny, dynamic: Message?): Set<String>? {
        if (message.code != CODE_TRANSFER) return null
        val msg = dynamic ?: return null
        val a = msg.messageList(F.HIF_MAP_A).mapNotNull { it.str(F.MAP_KEY) }
        val b = msg.messageList(F.HIF_MAP_B).mapNotNull { it.str(F.MAP_KEY) }
        return (a + b).toSet().ifEmpty { null }
    }

    /** Élément de jauge venant d'être **activé** (requête `hhw`), ou null si autre message. */
    fun activatedElement(message: DecodedGameAny, dynamic: Message?): Int? {
        if (message.code != CODE_GAUGE_ON) return null
        return dynamic?.enumNumber(F.HHW_ELEMENT)?.takeIf { it >= 0 }
    }

    /** Élément de jauge venant d'être **désactivé** (requête `hki`), ou null si autre message. */
    fun deactivatedElement(message: DecodedGameAny, dynamic: Message?): Int? {
        if (message.code != CODE_GAUGE_OFF) return null
        return dynamic?.enumNumber(F.HKI_ELEMENT)?.takeIf { it >= 0 }
    }

    /**
     * Jauges **auto-désactivées** par le serveur suite à une activation (réponse `hjj`) :
     * application des règles « 1 jauge de sérénité » et « 2 jauges max » (éviction FIFO).
     * Liste vide si rien n'a été désactivé ; null si ce n'est pas un `hjj`.
     */
    fun autoDeactivatedElements(message: DecodedGameAny, dynamic: Message?): List<Int>? {
        if (message.code != CODE_GAUGE_ON_RESP) return null
        val fdsw = dynamic?.msg(F.HJJ_FDSW) ?: return emptyList()
        return fdsw.enumList(F.FDSW_ELEMENTS)
    }

    /** Montures de l'étable (`hhv`) indexées par UUID, ou null si autre message / étable absente. */
    fun stableMounts(message: DecodedGameAny, dynamic: Message?): Map<String, Mount>? {
        if (message.code != CODE_STABLE) return null
        val hht = dynamic?.msg(F.HHV_HHT) ?: return null
        return hht.messageList(F.HHT_MOUNTS).mapNotNull { entry ->
            val uuid = entry.str(F.MAP_KEY) ?: return@mapNotNull null
            val value = entry.msg(F.MAP_VALUE) ?: return@mapNotNull null
            uuid to toMount(uuid, value)
        }.toMap().ifEmpty { null }
    }

    fun fromFull(hiy: Message): Paddock? = hiy.msg(F.HIY_CONTENT)?.let(::fromContent)

    fun fromUpdate(hle: Message): Paddock? =
        hle.msg(F.HLE_HLC)?.msg(F.HLC_CONTENT)?.let(::fromContent)

    /** Cœur du mapping : un message `him` → [Paddock]. */
    fun fromContent(him: Message): Paddock = Paddock(
        activeElements = him.enumList(F.HIM_ACTIVE_ELEMENTS),
        fuelGauges = him.messageList(F.HIM_FUEL_GAUGES).map { gauge ->
            FuelGauge(element = gauge.enumNumber(F.FUEL_ELEMENT), value = gauge.int(F.FUEL_VALUE))
        },
        mounts = him.messageList(F.HIM_MOUNTS).mapNotNull { entry ->
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
