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
 * test casse. Identité courante : patch **2026-08-04** (monture `hqu`, contenu d'enclos `hta`).
 */
class PaddockMapperTest {

    private val registry = DescriptorRegistry.loadFromClasspath()

    private fun desc(code: String): Descriptor =
        registry.findByTypeUrl("type.ankama.com/$code")
            ?: error("Descripteur '$code' introuvable")

    /** Contenu d'enclos (`hta`) : jauges carburant (1), éléments actifs (2), map montures (3). */
    private val hta get() = desc("hta")

    /** Descripteur de la monture `hqu`, atteint via l'entrée de map `hta.fnpe`(3). */
    private val hqu get() = hta.findFieldByNumber(3).messageType.findFieldByNumber(2).messageType

    private fun enumOf(d: Descriptor, field: Int, number: Int) =
        d.findFieldByNumber(field).enumType.findValueByNumber(number)

    /** Jauge monture `hqr` : valeur (fngr=1) puis type (fngt=3, enum `hqg`). */
    private fun gauge(type: Int, value: Int): DynamicMessage {
        val hqr = hqu.findFieldByNumber(6).messageType
        return DynamicMessage.newBuilder(hqr)
            .setField(hqr.findFieldByNumber(1), value)
            .setField(hqr.findFieldByNumber(3), enumOf(hqr, 3, type))
            .build()
    }

    /** Emballe une monture dans une entrée de `map<string, hqu>` du champ [mapField] de [owner]. */
    private fun mountEntry(owner: Descriptor, mapField: Int, uuid: String, mount: DynamicMessage): DynamicMessage {
        val entry = owner.findFieldByNumber(mapField).messageType
        return DynamicMessage.newBuilder(entry)
            .setField(entry.findFieldByNumber(1), uuid)
            .setField(entry.findFieldByNumber(2), mount)
            .build()
    }

    @Test
    fun `decode l'enclos depuis le contenu hta`() {
        val htz = hta.findFieldByNumber(1).messageType    // jauge carburant
        val hqs = hqu.findFieldByNumber(9).messageType    // généalogie
        val lng = hqu.findFieldByNumber(2).messageType    // effet

        // Jauge de carburant : élément #2 (fnrw=1, enum hqf) / valeur 53440 (fnrx=2)
        val fuel = DynamicMessage.newBuilder(htz)
            .setField(htz.findFieldByNumber(1), enumOf(htz, 1, 2))
            .setField(htz.findFieldByNumber(2), 53440)
            .build()

        // Effet : 138 (Puissance, gcbz=11) = 10 (valeur simple gccf=4)
        val effect = DynamicMessage.newBuilder(lng)
            .setField(lng.findFieldByNumber(11), 138)
            .setField(lng.findFieldByNumber(4), 10)
            .build()

        // Généalogie : robes des 2 parents (fngx=1, fngy=2)
        val parents = DynamicMessage.newBuilder(hqs)
            .setField(hqs.findFieldByNumber(1), 115)
            .setField(hqs.findFieldByNumber(2), 120)
            .build()

        val mount = DynamicMessage.newBuilder(hqu)
            .setField(hqu.findFieldByNumber(12), 97)                    // apparence/robe (fnho)
            .setField(hqu.findFieldByNumber(5), "enclos4monturegen4")   // nom (fnhg)
            .setField(hqu.findFieldByNumber(3), 26)                     // niveau (fnhe)
            .setField(hqu.findFieldByNumber(11), 49)                    // sérénité (fnhn)
            .setField(hqu.findFieldByNumber(1), 8090)                   // xp (fnhc)
            .setField(hqu.findFieldByNumber(9), parents)                // généalogie (fnhl)
            .addRepeatedField(hqu.findFieldByNumber(6), gauge(MountGauge.TYPE_LOVE, 16180))
            .addRepeatedField(hqu.findFieldByNumber(6), gauge(1, 0))
            .addRepeatedField(hqu.findFieldByNumber(6), gauge(2, 0))
            .addRepeatedField(hqu.findFieldByNumber(2), effect)
            .build()

        val content = DynamicMessage.newBuilder(hta)
            .addRepeatedField(hta.findFieldByNumber(1), fuel)
            .addRepeatedField(hta.findFieldByNumber(3), mountEntry(hta, 3, "e4c09997-96e5-46d7-b753-8500b83091b8", mount))
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
    fun `bredPair extrait les 2 UUID parents du message htu`() {
        val htu = desc("htu")
        val a = "b649d8dd-1251-4e88-9151-e42a93e63096"
        val b = "b8303efb-4ab4-49af-9cb2-f942bdb824c3"
        val msg = DynamicMessage.newBuilder(htu)
            .setField(htu.findFieldByNumber(1), a)   // fnrd
            .setField(htu.findFieldByNumber(3), b)   // fnrg
            .build()
        val decoded = decoded(Direction.CLIENT_TO_SERVER, PaddockMapper.CODE_BREED)
        assertEquals(setOf(a, b), PaddockMapper.bredPair(decoded, msg))
        // Autre message : pas de paire.
        assertEquals(null, PaddockMapper.bredPair(decoded(Direction.CLIENT_TO_SERVER, "hun"), msg))
    }

    /**
     * `fnhm`(10)=true sur une monture aux 3 jauges au max = **stérile**, pas féconde : c'est tout l'enjeu
     * du champ (les deux états ont les jauges à 20000, seul ce bool les départage). Régression vue en jeu
     * au patch précédent : une monture stérile in-game ressortait fertile tant qu'on lisait le mauvais bool.
     */
    @Test
    fun `une monture avec fnhm=true est sterile malgre ses jauges au max`() {
        val mount = DynamicMessage.newBuilder(hqu)
            .setField(hqu.findFieldByNumber(12), 93)    // apparence (fnho)
            .setField(hqu.findFieldByNumber(10), true)  // stérile (fnhm)
            .addRepeatedField(hqu.findFieldByNumber(6), gauge(MountGauge.TYPE_LOVE, 20000))
            .addRepeatedField(hqu.findFieldByNumber(6), gauge(MountGauge.TYPE_MATURITY, 20000))
            .addRepeatedField(hqu.findFieldByNumber(6), gauge(MountGauge.TYPE_ENDURANCE, 20000))
            .build()
        val content = DynamicMessage.newBuilder(hta)
            .addRepeatedField(hta.findFieldByNumber(3), mountEntry(hta, 3, "u", mount))
            .build()
        val m = PaddockMapper.fromContent(content).mounts["u"]
        assertNotNull(m)
        assertEquals(true, m.sterile)
        assertEquals(Fertility.STERILE, m.fertility)
    }

    /** Même monture sans le flag : jauges au max ⇒ féconde. C'est le seul bit qui départage. */
    @Test
    fun `sans fnhm les jauges au max donnent une feconde`() {
        val mount = DynamicMessage.newBuilder(hqu)
            .setField(hqu.findFieldByNumber(12), 93)
            .addRepeatedField(hqu.findFieldByNumber(6), gauge(MountGauge.TYPE_LOVE, 20000))
            .addRepeatedField(hqu.findFieldByNumber(6), gauge(MountGauge.TYPE_MATURITY, 20000))
            .addRepeatedField(hqu.findFieldByNumber(6), gauge(MountGauge.TYPE_ENDURANCE, 20000))
            .build()
        val content = DynamicMessage.newBuilder(hta)
            .addRepeatedField(hta.findFieldByNumber(3), mountEntry(hta, 3, "u", mount))
            .build()
        val m = PaddockMapper.fromContent(content).mounts["u"]
        assertNotNull(m)
        assertEquals(false, m.sterile)
        assertEquals(Fertility.FECONDE, m.fertility)
    }

    /** Le sexe est le seul bool qui **diffère** entre les 2 parents d'un accouplement (`hqu.fnhf`). */
    @Test
    fun `le sexe se lit sur fnhf`() {
        val male = DynamicMessage.newBuilder(hqu).setField(hqu.findFieldByNumber(4), true).build()
        val content = DynamicMessage.newBuilder(hta)
            .addRepeatedField(hta.findFieldByNumber(3), mountEntry(hta, 3, "m", male))
            .build()
        assertEquals(Sex.MALE, PaddockMapper.fromContent(content).mounts["m"]?.sex)
    }

    @Test
    fun `deactivatedElement lit l'element en fnhx (champ 1) de hqw`() {
        val hqw = desc("hqw")
        val msg = DynamicMessage.newBuilder(hqw).setField(hqw.findFieldByNumber(1), enumOf(hqw, 1, 2)).build()
        val decoded = decoded(Direction.CLIENT_TO_SERVER, PaddockMapper.CODE_GAUGE_OFF)
        assertEquals(2, PaddockMapper.deactivatedElement(decoded, msg))
    }

    @Test
    fun `autoDeactivatedElements lit les jauges evincees de htr`() {
        // Capture 2026-08-04 : activer la sérénité négative (baffeur, 0) désactive la positive (caresseur, 1).
        val htr = desc("htr")
        val htp = htr.findFieldByNumber(2).messageType
        val body = DynamicMessage.newBuilder(htp)
            .addRepeatedField(htp.findFieldByNumber(1), enumOf(htp, 1, 1))
            .build()
        val msg = DynamicMessage.newBuilder(htr).setField(htr.findFieldByNumber(2), body).build()
        val decoded = decoded(Direction.SERVER_TO_CLIENT, PaddockMapper.CODE_GAUGE_ON_RESP)
        assertEquals(listOf(1), PaddockMapper.autoDeactivatedElements(decoded, msg))
    }

    @Test
    fun `bredOffspring separe les 2 parents du nouveau-ne de hsu`() {
        val hsu = desc("hsu")
        val hss = hsu.findFieldByNumber(2).messageType
        fun mount(app: Int) = DynamicMessage.newBuilder(hqu).setField(hqu.findFieldByNumber(12), app).build()
        // ⚠️ nouveau-né en fnnk(2), parents en fnnn(5) — l'inverse de l'ancien `hus`.
        val body = DynamicMessage.newBuilder(hss)
            .addRepeatedField(hss.findFieldByNumber(5), mountEntry(hss, 5, "p1", mount(1)))
            .addRepeatedField(hss.findFieldByNumber(5), mountEntry(hss, 5, "p2", mount(2)))
            .addRepeatedField(hss.findFieldByNumber(2), mountEntry(hss, 2, "baby", mount(3)))
            .build()
        val msg = DynamicMessage.newBuilder(hsu).setField(hsu.findFieldByNumber(2), body).build()
        val decoded = decoded(Direction.SERVER_TO_CLIENT, PaddockMapper.CODE_BREED_RESULT)
        val result = PaddockMapper.bredOffspring(decoded, msg)
        assertNotNull(result)
        assertEquals(setOf("p1", "p2"), result.parents.keys)
        assertEquals(setOf("baby"), result.newborns.keys)
        assertEquals(3, result.newborns["baby"]?.appearanceId)
        assertEquals(setOf("p1", "p2", "baby"), result.all.keys)
    }

    @Test
    fun `clonedMount lit la monture et son uuid dans hsm`() {
        val hsm = desc("hsm")
        val hsk = hsm.findFieldByNumber(1).messageType
        // Capture 2026-08-04 : clone de généalogie {96 Amande, 94 Doré} → robe 121 « Doré et Amande ».
        val mount = DynamicMessage.newBuilder(hqu).setField(hqu.findFieldByNumber(12), 121).build()
        val body = DynamicMessage.newBuilder(hsk)
            .setField(hsk.findFieldByNumber(1), mount)
            .setField(hsk.findFieldByNumber(2), "dc2c0439-f19b-4f89-a649-59fd483e8da5")
            .build()
        val msg = DynamicMessage.newBuilder(hsm).setField(hsm.findFieldByNumber(1), body).build()
        val decoded = decoded(Direction.SERVER_TO_CLIENT, PaddockMapper.CODE_CLONE_RESULT)
        val result = PaddockMapper.clonedMount(decoded, msg)
        assertNotNull(result)
        assertEquals(121, result["dc2c0439-f19b-4f89-a649-59fd483e8da5"]?.appearanceId)
    }

    @Test
    fun `unlockedPaddocks lit la map index-deverrouille de hus`() {
        // Échantillon : enclos 1..5 déverrouillés, 6ᵉ verrouillé.
        val hus = desc("hus")
        val entry = hus.findFieldByNumber(1).messageType // map<int32, bool>
        fun mapEntry(idx: Int, unlocked: Boolean) = DynamicMessage.newBuilder(entry)
            .setField(entry.findFieldByNumber(1), idx)
            .setField(entry.findFieldByNumber(2), unlocked)
            .build()
        val msg = DynamicMessage.newBuilder(hus).apply {
            (1..5).forEach { addRepeatedField(hus.findFieldByNumber(1), mapEntry(it, true)) }
            addRepeatedField(hus.findFieldByNumber(1), mapEntry(6, false))
        }.build()
        val decoded = decoded(Direction.SERVER_TO_CLIENT, PaddockMapper.CODE_PADDOCK_LIST)
        val result = PaddockMapper.unlockedPaddocks(decoded, msg)
        assertEquals(mapOf(1 to true, 2 to true, 3 to true, 4 to true, 5 to true, 6 to false), result)
        // Autre message : pas de liste d'enclos.
        assertEquals(null, PaddockMapper.unlockedPaddocks(decoded(Direction.SERVER_TO_CLIENT, "hun"), msg))
    }

    @Test
    fun `transferredMountIds lit les uuid deplaces de hur`() {
        // Capture 2026-08-04 : étable → enclos, réponse map<uuid, code> avec HUO_EEWH(0) = OK.
        val hur = desc("hur")
        val entry = hur.findFieldByNumber(2).messageType // map<string, huo>
        val msg = DynamicMessage.newBuilder(hur)
            .addRepeatedField(
                hur.findFieldByNumber(2),
                DynamicMessage.newBuilder(entry)
                    .setField(entry.findFieldByNumber(1), "02d3b6b7-f08a-481b-9eaa-16fad17f4529")
                    .setField(entry.findFieldByNumber(2), enumOf(entry, 2, 0))
                    .build(),
            )
            .build()
        val decoded = decoded(Direction.SERVER_TO_CLIENT, PaddockMapper.CODE_TRANSFER)
        assertEquals(setOf("02d3b6b7-f08a-481b-9eaa-16fad17f4529"), PaddockMapper.transferredMountIds(decoded, msg))
    }

    @Test
    fun `stableMounts lit la collection complete de huz`() {
        // Capture 2026-08-04 : `hqx` (C→S vide) → `huz` (37 Ko) = toutes les montures, hors enclos.
        val huz = desc("huz")
        val hux = huz.findFieldByNumber(2).messageType
        val mount = DynamicMessage.newBuilder(hqu)
            .setField(hqu.findFieldByNumber(12), 151)  // robe « Prune et Roux »
            .setField(hqu.findFieldByNumber(3), 80)    // niveau
            .build()
        val body = DynamicMessage.newBuilder(hux)
            .addRepeatedField(
                hux.findFieldByNumber(1),
                mountEntry(hux, 1, "49f8ba0d-3145-4fa9-9cde-7e013ab18a7e", mount),
            )
            .build()
        val msg = DynamicMessage.newBuilder(huz).setField(huz.findFieldByNumber(2), body).build()
        val decoded = decoded(Direction.SERVER_TO_CLIENT, PaddockMapper.CODE_STABLE)
        val result = PaddockMapper.stableMounts(decoded, msg)
        assertNotNull(result)
        assertEquals(151, result["49f8ba0d-3145-4fa9-9cde-7e013ab18a7e"]?.appearanceId)
        assertEquals(80, result["49f8ba0d-3145-4fa9-9cde-7e013ab18a7e"]?.level)
    }

    @Test
    fun `mountsToInventory ne retient que les sorties acceptees par le serveur`() {
        val hti = desc("hti")
        val entry = hti.findFieldByNumber(1).messageType // map<string, htg>
        fun mapEntry(uuid: String, code: Int) = DynamicMessage.newBuilder(entry)
            .setField(entry.findFieldByNumber(1), uuid)
            .setField(entry.findFieldByNumber(2), enumOf(entry, 2, code))
            .build()
        val msg = DynamicMessage.newBuilder(hti)
            .addRepeatedField(hti.findFieldByNumber(1), mapEntry("a7a8ed1f-4c31-4f20-aa03-7dd809a66894", 0))
            .addRepeatedField(hti.findFieldByNumber(1), mapEntry("refusée", 3))
            .build()
        val decoded = decoded(Direction.SERVER_TO_CLIENT, PaddockMapper.CODE_TO_INVENTORY)
        assertEquals(setOf("a7a8ed1f-4c31-4f20-aa03-7dd809a66894"), PaddockMapper.mountsToInventory(decoded, msg))
    }

    @Test
    fun `mountsFromInventory reconstruit la monture rendue par le certificat`() {
        val hty = desc("hty")
        val entry = hty.findFieldByNumber(1).messageType   // map<int32, htw>
        val htw = entry.findFieldByNumber(2).messageType
        val mount = DynamicMessage.newBuilder(hqu)
            .setField(hqu.findFieldByNumber(12), 93)   // robe Pourpre (fnho)
            .setField(hqu.findFieldByNumber(3), 81)    // niveau (fnhe)
            .setField(hqu.findFieldByNumber(5), "2")   // nom (fnhg)
            .build()
        // ⚠️ monture en fnrm(1), uuid en fnrn(2) — l'inverse de l'ancien `hri`.
        val slot = DynamicMessage.newBuilder(htw)
            .setField(htw.findFieldByNumber(1), mount)
            .setField(htw.findFieldByNumber(2), "a7a8ed1f-4c31-4f20-aa03-7dd809a66894")
            .build()
        val msg = DynamicMessage.newBuilder(hty)
            .addRepeatedField(
                hty.findFieldByNumber(1),
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
