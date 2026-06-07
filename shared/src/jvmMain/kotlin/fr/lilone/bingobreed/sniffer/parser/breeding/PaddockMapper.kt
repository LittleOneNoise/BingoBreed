package fr.lilone.bingobreed.sniffer.parser.breeding

import com.google.protobuf.Descriptors
import com.google.protobuf.Message
import fr.lilone.bingobreed.sniffer.model.DecodedGameAny
import fr.lilone.bingobreed.sniffer.model.breeding.FuelGauge
import fr.lilone.bingobreed.sniffer.model.breeding.Mount
import fr.lilone.bingobreed.sniffer.model.breeding.MountEffect
import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
import fr.lilone.bingobreed.sniffer.model.breeding.Paddock

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

    /** Numéros de champ — UNIQUE point de resynchronisation après un patch. */
    private object F {
        // hiy { fdrs=1 ; fdrt=2 : him }
        const val HIY_CONTENT = 2
        // hle { oneof { fdzc=2 : hlc } } ; hlc { fdyv=1 ; fdyw=2 : him }
        const val HLE_HLC = 2
        const val HLC_CONTENT = 2
        // him { fdql=1 repeated enum ; fdqm=2 repeated hhx ; fdqn=3 map<string,hlo> }
        const val HIM_ACTIVE_ELEMENTS = 1
        const val HIM_FUEL_GAUGES = 2
        const val HIM_MOUNTS = 3
        // hhx (jauge carburant) { fdov=2 valeur ; fdow=3 élément (hhc) }
        const val FUEL_VALUE = 2
        const val FUEL_ELEMENT = 3
        // hlo (monture) { feaj=2 nom ; fean=5 niveau ; feao=6 xp ; fear=9 effets ; feas=10 sérénité ; feav=13 jauges }
        const val MOUNT_NAME = 2
        const val MOUNT_LEVEL = 5
        const val MOUNT_XP = 6
        const val MOUNT_EFFECTS = 9
        const val MOUNT_SERENITY = 10
        const val MOUNT_GAUGES = 13
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

    private fun toMount(uuid: String, m: Message): Mount = Mount(
        uuid = uuid,
        name = m.str(F.MOUNT_NAME),
        level = m.int(F.MOUNT_LEVEL),
        experience = m.int(F.MOUNT_XP),
        serenity = m.int(F.MOUNT_SERENITY),
        gauges = m.messageList(F.MOUNT_GAUGES).map { gauge ->
            MountGauge(type = gauge.enumNumber(F.MGAUGE_TYPE), value = gauge.int(F.MGAUGE_VALUE))
        },
        effects = m.messageList(F.MOUNT_EFFECTS).map { effect ->
            MountEffect(effectId = effect.int(F.EFFECT_ID), value = effect.intOrNull(F.EFFECT_VALUE))
        },
    )

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
