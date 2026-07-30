package fr.lilone.bingobreed.sniffer

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.DynamicMessage
import fr.lilone.bingobreed.sniffer.model.DecodedGameAny
import fr.lilone.bingobreed.sniffer.model.Direction
import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
import fr.lilone.bingobreed.sniffer.model.breeding.Sex
import fr.lilone.bingobreed.sniffer.parser.DescriptorRegistry
import fr.lilone.bingobreed.sniffer.parser.breeding.PaddockMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Reconstruit des échantillons réels (relevés en jeu) à partir des descripteurs protodec, puis vérifie
 * que [PaddockMapper] les décode bien. Sert d'ancrage : si un patch change les numéros de champ, ce
 * test casse. Identité courante : patch **2026-07-30** (monture `hvf`, contenu d'enclos `hrp`).
 */
class PaddockMapperTest {

    private val registry = DescriptorRegistry.loadFromClasspath()

    private fun desc(code: String): Descriptor =
        registry.findByTypeUrl("type.ankama.com/$code")
            ?: error("Descripteur '$code' introuvable")

    /** Contenu d'enclos (`hrp`) : map montures (1), éléments actifs (2), jauges carburant (3). */
    private val hrp get() = desc("hrp")

    /** Descripteur de la monture `hvf`, atteint via l'entrée de map `hrp.foau`. */
    private val hvf get() = hrp.findFieldByNumber(1).messageType.findFieldByNumber(2).messageType

    private fun enumOf(d: Descriptor, field: Int, number: Int) =
        d.findFieldByNumber(field).enumType.findValueByNumber(number)

    /** Jauge monture `hvc` : valeur (fomh=1) puis type (fomi=2, enum `hqu`). */
    private fun gauge(type: Int, value: Int): DynamicMessage {
        val hvc = hvf.findFieldByNumber(3).messageType
        return DynamicMessage.newBuilder(hvc)
            .setField(hvc.findFieldByNumber(1), value)
            .setField(hvc.findFieldByNumber(2), enumOf(hvc, 2, type))
            .build()
    }

    /** Emballe une monture dans une entrée de `map<string, hvf>` du champ [mapField] de [owner]. */
    private fun mountEntry(owner: Descriptor, mapField: Int, uuid: String, mount: DynamicMessage): DynamicMessage {
        val entry = owner.findFieldByNumber(mapField).messageType
        return DynamicMessage.newBuilder(entry)
            .setField(entry.findFieldByNumber(1), uuid)
            .setField(entry.findFieldByNumber(2), mount)
            .build()
    }

    @Test
    fun `decode l'enclos depuis le contenu hrp`() {
        val huh = hrp.findFieldByNumber(3).messageType    // jauge carburant
        val hvd = hvf.findFieldByNumber(4).messageType    // généalogie
        val ldn = hvf.findFieldByNumber(9).messageType    // effet

        // Jauge de carburant : élément #2 (fojn=1, enum hqt) / valeur 53440 (fojo=2)
        val fuel = DynamicMessage.newBuilder(huh)
            .setField(huh.findFieldByNumber(1), enumOf(huh, 1, 2))
            .setField(huh.findFieldByNumber(2), 53440)
            .build()

        // Effet : 138 (Puissance, gbce=9) = 10 (valeur simple gbcn=8)
        val effect = DynamicMessage.newBuilder(ldn)
            .setField(ldn.findFieldByNumber(9), 138)
            .setField(ldn.findFieldByNumber(8), 10)
            .build()

        // Généalogie : robes des 2 parents (fomn=1, fomo=2)
        val parents = DynamicMessage.newBuilder(hvd)
            .setField(hvd.findFieldByNumber(1), 115)
            .setField(hvd.findFieldByNumber(2), 120)
            .build()

        val mount = DynamicMessage.newBuilder(hvf)
            .setField(hvf.findFieldByNumber(12), 97)                    // apparence/robe (fone)
            .setField(hvf.findFieldByNumber(8), "enclos4monturegen4")   // nom (fomz)
            .setField(hvf.findFieldByNumber(6), 26)                     // niveau (fomx)
            .setField(hvf.findFieldByNumber(7), 49)                     // sérénité (fomy)
            .setField(hvf.findFieldByNumber(11), 8090)                  // xp (fond)
            .setField(hvf.findFieldByNumber(4), parents)                // généalogie (fomv)
            .addRepeatedField(hvf.findFieldByNumber(3), gauge(MountGauge.TYPE_LOVE, 16180))
            .addRepeatedField(hvf.findFieldByNumber(3), gauge(1, 0))
            .addRepeatedField(hvf.findFieldByNumber(3), gauge(2, 0))
            .addRepeatedField(hvf.findFieldByNumber(9), effect)
            .build()

        val content = DynamicMessage.newBuilder(hrp)
            .addRepeatedField(hrp.findFieldByNumber(3), fuel)
            .addRepeatedField(hrp.findFieldByNumber(1), mountEntry(hrp, 1, "e4c09997-96e5-46d7-b753-8500b83091b8", mount))
            .build()

        val paddock = PaddockMapper.fromContent(content)

        assertEquals(1, paddock.fuelGauges.size)
        assertEquals(53440, paddock.fuelGauges[0].value)
        assertEquals(2, paddock.fuelGauges[0].element)

        val m = paddock.mounts["e4c09997-96e5-46d7-b753-8500b83091b8"]
        assertNotNull(m)
        assertEquals("enclos4monturegen4", m.name)
        assertEquals(97, m.appearanceId)
        assertEquals(26, m.level)
        assertEquals(8090, m.experience)
        assertEquals(49, m.serenity)
        assertEquals(16180, m.gauges.first { it.type == MountGauge.TYPE_LOVE }.value)
        assertEquals(10, m.effects.first { it.effectId == 138 }.value)
        assertEquals(listOf(115, 120), m.parents)
        // Sample : ♀ (sexe absent), non stérile → fertile.
        assertEquals(Sex.FEMALE, m.sex)
        assertEquals(false, m.sterile)
        assertEquals(Fertility.FERTILE, m.fertility)
    }

    @Test
    fun `bredPair extrait les 2 UUID parents du message hsp`() {
        val hsp = desc("hsp")
        val a = "b649d8dd-1251-4e88-9151-e42a93e63096"
        val b = "b8303efb-4ab4-49af-9cb2-f942bdb824c3"
        val msg = DynamicMessage.newBuilder(hsp)
            .setField(hsp.findFieldByNumber(3), a)   // foej
            .setField(hsp.findFieldByNumber(4), b)   // foek
            .build()
        val decoded = decoded(Direction.CLIENT_TO_SERVER, PaddockMapper.CODE_BREED)
        assertEquals(setOf(a, b), PaddockMapper.bredPair(decoded, msg))
        // Autre message : pas de paire.
        assertEquals(null, PaddockMapper.bredPair(decoded(Direction.CLIENT_TO_SERVER, "hug"), msg))
    }

    /**
     * `fomt`=true sur une monture aux 3 jauges au max = **stérile**, pas féconde : c'est tout l'enjeu du
     * champ (les deux états ont les jauges à 20000, seul ce bool les départage). Régression vue en jeu :
     * une monture stérile in-game ressortait fertile tant qu'on lisait `foms`(1).
     */
    @Test
    fun `une monture avec fomt=true est sterile malgre ses jauges au max`() {
        val mount = DynamicMessage.newBuilder(hvf)
            .setField(hvf.findFieldByNumber(12), 93)    // apparence (fone)
            .setField(hvf.findFieldByNumber(2), true)   // stérile (fomt)
            .addRepeatedField(hvf.findFieldByNumber(3), gauge(MountGauge.TYPE_LOVE, 20000))
            .addRepeatedField(hvf.findFieldByNumber(3), gauge(MountGauge.TYPE_MATURITY, 20000))
            .addRepeatedField(hvf.findFieldByNumber(3), gauge(MountGauge.TYPE_ENDURANCE, 20000))
            .build()
        val content = DynamicMessage.newBuilder(hrp)
            .addRepeatedField(hrp.findFieldByNumber(1), mountEntry(hrp, 1, "u", mount))
            .build()
        val m = PaddockMapper.fromContent(content).mounts["u"]
        assertNotNull(m)
        assertEquals(true, m.sterile)
        assertEquals(Fertility.STERILE, m.fertility)
    }

    /** Même monture sans le flag : jauges au max ⇒ féconde. C'est le seul bit qui départage. */
    @Test
    fun `sans fomt les jauges au max donnent une feconde`() {
        val mount = DynamicMessage.newBuilder(hvf)
            .setField(hvf.findFieldByNumber(12), 93)
            .addRepeatedField(hvf.findFieldByNumber(3), gauge(MountGauge.TYPE_LOVE, 20000))
            .addRepeatedField(hvf.findFieldByNumber(3), gauge(MountGauge.TYPE_MATURITY, 20000))
            .addRepeatedField(hvf.findFieldByNumber(3), gauge(MountGauge.TYPE_ENDURANCE, 20000))
            .build()
        val content = DynamicMessage.newBuilder(hrp)
            .addRepeatedField(hrp.findFieldByNumber(1), mountEntry(hrp, 1, "u", mount))
            .build()
        val m = PaddockMapper.fromContent(content).mounts["u"]
        assertNotNull(m)
        assertEquals(false, m.sterile)
        assertEquals(Fertility.FECONDE, m.fertility)
    }

    @Test
    fun `deactivatedElement lit l'element en foah (champ 1) de hrm`() {
        val hrm = desc("hrm")
        val msg = DynamicMessage.newBuilder(hrm).setField(hrm.findFieldByNumber(1), enumOf(hrm, 1, 2)).build()
        val decoded = decoded(Direction.CLIENT_TO_SERVER, PaddockMapper.CODE_GAUGE_OFF)
        assertEquals(2, PaddockMapper.deactivatedElement(decoded, msg))
    }

    @Test
    fun `autoDeactivatedElements lit les jauges evincees de hrg`() {
        // Capture 2026-07-30 : activer la sérénité négative (baffeur, 0) désactive la positive (caresseur, 1).
        val hrg = desc("hrg")
        val hre = hrg.findFieldByNumber(1).messageType
        val body = DynamicMessage.newBuilder(hre)
            .addRepeatedField(hre.findFieldByNumber(1), enumOf(hre, 1, 1))
            .build()
        val msg = DynamicMessage.newBuilder(hrg).setField(hrg.findFieldByNumber(1), body).build()
        val decoded = decoded(Direction.SERVER_TO_CLIENT, PaddockMapper.CODE_GAUGE_ON_RESP)
        assertEquals(listOf(1), PaddockMapper.autoDeactivatedElements(decoded, msg))
    }

    @Test
    fun `bredOffspring separe les 2 parents du nouveau-ne de huu`() {
        val huu = desc("huu")
        val hus = huu.findFieldByNumber(1).messageType
        fun mount(app: Int) = DynamicMessage.newBuilder(hvf).setField(hvf.findFieldByNumber(12), app).build()
        val body = DynamicMessage.newBuilder(hus)
            .addRepeatedField(hus.findFieldByNumber(2), mountEntry(hus, 2, "p1", mount(1)))
            .addRepeatedField(hus.findFieldByNumber(2), mountEntry(hus, 2, "p2", mount(2)))
            .addRepeatedField(hus.findFieldByNumber(3), mountEntry(hus, 3, "baby", mount(3)))
            .build()
        val msg = DynamicMessage.newBuilder(huu).setField(huu.findFieldByNumber(1), body).build()
        val decoded = decoded(Direction.SERVER_TO_CLIENT, PaddockMapper.CODE_BREED_RESULT)
        val result = PaddockMapper.bredOffspring(decoded, msg)
        assertNotNull(result)
        assertEquals(setOf("p1", "p2"), result.parents.keys)
        assertEquals(setOf("baby"), result.newborns.keys)
        assertEquals(3, result.newborns["baby"]?.appearanceId)
        assertEquals(setOf("p1", "p2", "baby"), result.all.keys)
    }

    @Test
    fun `clonedMount lit la monture et son uuid dans hvm`() {
        val hvm = desc("hvm")
        val hvk = hvm.findFieldByNumber(1).messageType
        // Capture 2026-07-30 : clone de parents {93 Pourpre, 98 Turquoise} → robe 140 « Turquoise et Pourpre ».
        val mount = DynamicMessage.newBuilder(hvf).setField(hvf.findFieldByNumber(12), 140).build()
        val body = DynamicMessage.newBuilder(hvk)
            .setField(hvk.findFieldByNumber(1), mount)
            .setField(hvk.findFieldByNumber(2), "ac9f2286-1739-48cf-b7a4-792ae2de3067")
            .build()
        val msg = DynamicMessage.newBuilder(hvm).setField(hvm.findFieldByNumber(1), body).build()
        val decoded = decoded(Direction.SERVER_TO_CLIENT, PaddockMapper.CODE_CLONE_RESULT)
        val result = PaddockMapper.clonedMount(decoded, msg)
        assertNotNull(result)
        assertEquals(140, result["ac9f2286-1739-48cf-b7a4-792ae2de3067"]?.appearanceId)
    }

    @Test
    fun `unlockedPaddocks lit la map index-deverrouille de hsk`() {
        // Échantillon réel (capture 2026-07-30) : enclos 1..5 déverrouillés, 6ᵉ verrouillé.
        val hsk = desc("hsk")
        val entry = hsk.findFieldByNumber(1).messageType // map<int32, bool>
        fun mapEntry(idx: Int, unlocked: Boolean) = DynamicMessage.newBuilder(entry)
            .setField(entry.findFieldByNumber(1), idx)
            .setField(entry.findFieldByNumber(2), unlocked)
            .build()
        val msg = DynamicMessage.newBuilder(hsk).apply {
            (1..5).forEach { addRepeatedField(hsk.findFieldByNumber(1), mapEntry(it, true)) }
            addRepeatedField(hsk.findFieldByNumber(1), mapEntry(6, false))
        }.build()
        val decoded = decoded(Direction.SERVER_TO_CLIENT, PaddockMapper.CODE_PADDOCK_LIST)
        val result = PaddockMapper.unlockedPaddocks(decoded, msg)
        assertEquals(mapOf(1 to true, 2 to true, 3 to true, 4 to true, 5 to true, 6 to false), result)
        // Autre message : pas de liste d'enclos.
        assertEquals(null, PaddockMapper.unlockedPaddocks(decoded(Direction.SERVER_TO_CLIENT, "hug"), msg))
    }

    @Test
    fun `mountsToInventory ne retient que les sorties acceptees par le serveur`() {
        val hul = desc("hul")
        val entry = hul.findFieldByNumber(1).messageType // map<string, hui>
        fun mapEntry(uuid: String, code: Int) = DynamicMessage.newBuilder(entry)
            .setField(entry.findFieldByNumber(1), uuid)
            .setField(entry.findFieldByNumber(2), enumOf(entry, 2, code))
            .build()
        val msg = DynamicMessage.newBuilder(hul)
            .addRepeatedField(hul.findFieldByNumber(1), mapEntry("a7a8ed1f-4c31-4f20-aa03-7dd809a66894", 0))
            .addRepeatedField(hul.findFieldByNumber(1), mapEntry("refusée", 3))
            .build()
        val decoded = decoded(Direction.SERVER_TO_CLIENT, PaddockMapper.CODE_TO_INVENTORY)
        assertEquals(setOf("a7a8ed1f-4c31-4f20-aa03-7dd809a66894"), PaddockMapper.mountsToInventory(decoded, msg))
    }

    @Test
    fun `mountsFromInventory reconstruit la monture rendue par le certificat`() {
        val hrk = desc("hrk")
        val entry = hrk.findFieldByNumber(1).messageType   // map<int32, hri>
        val hri = entry.findFieldByNumber(2).messageType
        val mount = DynamicMessage.newBuilder(hvf)
            .setField(hvf.findFieldByNumber(12), 93)   // robe Pourpre (fone)
            .setField(hvf.findFieldByNumber(6), 81)    // niveau (fomx)
            .setField(hvf.findFieldByNumber(8), "2")   // nom (fomz)
            .build()
        val slot = DynamicMessage.newBuilder(hri)
            .setField(hri.findFieldByNumber(1), "a7a8ed1f-4c31-4f20-aa03-7dd809a66894")
            .setField(hri.findFieldByNumber(2), mount)
            .build()
        val msg = DynamicMessage.newBuilder(hrk)
            .addRepeatedField(
                hrk.findFieldByNumber(1),
                DynamicMessage.newBuilder(entry)
                    .setField(entry.findFieldByNumber(1), 99784468)
                    .setField(entry.findFieldByNumber(2), slot)
                    .build(),
            )
            .build()
        val decoded = decoded(Direction.SERVER_TO_CLIENT, PaddockMapper.CODE_FROM_INVENTORY)
        val result = PaddockMapper.mountsFromInventory(decoded, msg)
        assertNotNull(result)
        val m = result["a7a8ed1f-4c31-4f20-aa03-7dd809a66894"]
        assertNotNull(m)
        assertEquals(93, m.appearanceId)
        assertEquals(81, m.level)
        assertEquals("2", m.name)
    }

    @Test
    fun `derive la fertilite cote client`() {
        val maxed = listOf(MountGauge(0, 20000), MountGauge(1, 20000), MountGauge(2, 20000))
        val partial = listOf(MountGauge(0, 80), MountGauge(1, 20000), MountGauge(2, 0))
        // Stérile prime, même jauges au max.
        assertEquals(Fertility.STERILE, Fertility.of(sterile = true, maxed))
        assertEquals(Fertility.FECONDE, Fertility.of(sterile = false, maxed))
        assertEquals(Fertility.FERTILE, Fertility.of(sterile = false, partial))
    }

    private fun decoded(direction: Direction, code: String) = DecodedGameAny(
        direction = direction,
        envelopeField = 1,
        requestId = null,
        typeUrl = "type.ankama.com/$code",
        value = ByteArray(0),
        knownName = code,
    )
}
