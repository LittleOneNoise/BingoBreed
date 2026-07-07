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
 * Reconstruit l'échantillon réel "enclos4monturegen4" (relevé en jeu) à partir
 * des descripteurs protodec, puis vérifie que [PaddockMapper] le décode bien.
 * Sert d'ancrage : si un patch change les numéros de champ, ce test casse.
 */
class PaddockMapperTest {

    private val registry = DescriptorRegistry.loadFromClasspath()

    private fun desc(code: String): Descriptor =
        registry.findByTypeUrl("type.ankama.com/$code")
            ?: error("Descripteur '$code' introuvable")

    @Test
    fun `decode l'enclos depuis le contenu huh`() {
        // Structure post-patch 2026-07 / client 3.6.6.6 (cf. PaddockMapper.F) : huh (contenu),
        // hqg (carburant), hty (monture), htv (jauge monture, type=3/valeur=2), ldk (effet).
        val huh = desc("huh")
        val hqg = huh.findFieldByNumber(1).messageType    // jauge carburant
        val mountsField = huh.findFieldByNumber(3)         // map<string, hty>
        val entry = mountsField.messageType                // entrée de map
        val hty = entry.findFieldByNumber(2).messageType   // monture
        val htv = hty.findFieldByNumber(5).messageType     // jauge monture
        val ldk = hty.findFieldByNumber(9).messageType     // effet
        val htw = hty.findFieldByNumber(10).messageType    // généalogie

        // Jauge de carburant : élément #2 (fmtz=1, enum hpo) / valeur 53440 (fmua=2)
        val fuel = DynamicMessage.newBuilder(hqg)
            .setField(hqg.findFieldByNumber(1), hqg.findFieldByNumber(1).enumType.findValueByNumber(2))
            .setField(hqg.findFieldByNumber(2), 53440)
            .build()

        // Jauge monture : valeur (fnff=2) / type (fnfg=3, enum hpp)
        fun gauge(type: Int, value: Int) = DynamicMessage.newBuilder(htv).apply {
            setField(htv.findFieldByNumber(2), value)
            setField(htv.findFieldByNumber(3), htv.findFieldByNumber(3).enumType.findValueByNumber(type))
        }.build()

        // Effet : 138 (Puissance, gahn=2) = 10 (valeur simple gahr=3)
        val effect = DynamicMessage.newBuilder(ldk)
            .setField(ldk.findFieldByNumber(2), 138)
            .setField(ldk.findFieldByNumber(3), 10)
            .build()

        // Généalogie : robes des 2 parents (fnfl=2, fnfm=3)
        val parents = DynamicMessage.newBuilder(htw)
            .setField(htw.findFieldByNumber(2), 115)
            .setField(htw.findFieldByNumber(3), 120)
            .build()

        val mount = DynamicMessage.newBuilder(hty)
            .setField(hty.findFieldByNumber(1), 97)                    // apparence/robe (fnfq)
            .setField(hty.findFieldByNumber(7), "enclos4monturegen4")  // nom (fnfw)
            .setField(hty.findFieldByNumber(3), 26)                    // niveau (fnfs)
            .setField(hty.findFieldByNumber(12), 49)                   // sérénité (fngc)
            .setField(hty.findFieldByNumber(11), 8090)                 // xp (fngb)
            .setField(hty.findFieldByNumber(10), parents)              // généalogie (fnga)
            .addRepeatedField(hty.findFieldByNumber(5), gauge(MountGauge.TYPE_LOVE, 16180))
            .addRepeatedField(hty.findFieldByNumber(5), gauge(1, 0))
            .addRepeatedField(hty.findFieldByNumber(5), gauge(2, 0))
            .addRepeatedField(hty.findFieldByNumber(9), effect)
            .build()

        val mapEntry = DynamicMessage.newBuilder(entry)
            .setField(entry.findFieldByNumber(1), "e4c09997-96e5-46d7-b753-8500b83091b8")
            .setField(entry.findFieldByNumber(2), mount)
            .build()

        val content = DynamicMessage.newBuilder(huh)
            .addRepeatedField(huh.findFieldByNumber(1), fuel)
            .addRepeatedField(huh.findFieldByNumber(3), mapEntry)
            .build()

        val paddock = PaddockMapper.fromContent(content)

        assertEquals(1, paddock.fuelGauges.size)
        assertEquals(53440, paddock.fuelGauges[0].value)

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
        // Sample : ♀ (sex absent), non stérile → fertile.
        assertEquals(Sex.FEMALE, m.sex)
        assertEquals(false, m.sterile)
        assertEquals(Fertility.FERTILE, m.fertility)
    }

    @Test
    fun `bredPair extrait les 2 UUID parents du message htf`() {
        val htf = desc("htf")
        val a = "b649d8dd-1251-4e88-9151-e42a93e63096"
        val b = "b8303efb-4ab4-49af-9cb2-f942bdb824c3"
        val msg = DynamicMessage.newBuilder(htf)
            .setField(htf.findFieldByNumber(1), a)   // fnbz
            .setField(htf.findFieldByNumber(3), b)   // fncc
            .build()
        val decoded = DecodedGameAny(
            direction = Direction.CLIENT_TO_SERVER,
            envelopeField = 1,
            requestId = null,
            typeUrl = "type.ankama.com/htf",
            value = ByteArray(0),
            knownName = "htf",
        )
        assertEquals(setOf(a, b), PaddockMapper.bredPair(decoded, msg))
        // Autre message : pas de paire.
        val other = DecodedGameAny(Direction.CLIENT_TO_SERVER, 1, null, "type.ankama.com/hqa", ByteArray(0), null)
        assertEquals(null, PaddockMapper.bredPair(other, msg))
    }

    @Test
    fun `une monture avec fnft=true est sterile`() {
        val huh = desc("huh")
        val entry = huh.findFieldByNumber(3).messageType
        val hty = entry.findFieldByNumber(2).messageType
        val mount = DynamicMessage.newBuilder(hty)
            .setField(hty.findFieldByNumber(1), 93)     // apparence (fnfq)
            .setField(hty.findFieldByNumber(4), true)   // stérile (fnft)
            .build()
        val mapEntry = DynamicMessage.newBuilder(entry)
            .setField(entry.findFieldByNumber(1), "u")
            .setField(entry.findFieldByNumber(2), mount)
            .build()
        val content = DynamicMessage.newBuilder(huh)
            .addRepeatedField(huh.findFieldByNumber(3), mapEntry)
            .build()
        val m = PaddockMapper.fromContent(content).mounts["u"]
        assertNotNull(m)
        assertEquals(true, m.sterile)
        assertEquals(Fertility.STERILE, m.fertility)
    }

    @Test
    fun `deactivatedElement lit l'element en fnep (champ 2) de hts`() {
        val hts = desc("hts")
        val endurance = hts.findFieldByNumber(2).enumType.findValueByNumber(2) // HPO_EDTZ
        val msg = DynamicMessage.newBuilder(hts).setField(hts.findFieldByNumber(2), endurance).build()
        val decoded = DecodedGameAny(Direction.CLIENT_TO_SERVER, 1, null, "type.ankama.com/hts", ByteArray(0), "hts")
        assertEquals(2, PaddockMapper.deactivatedElement(decoded, msg))
    }

    @Test
    fun `bredOffspring extrait les 2 parents et le nouveau-ne de hqq`() {
        val hqq = desc("hqq")
        val hqo = hqq.findFieldByNumber(2).messageType
        val parentEntry = hqo.findFieldByNumber(3).messageType // map entry <string, hty>
        val childEntry = hqo.findFieldByNumber(6).messageType
        val hty = parentEntry.findFieldByNumber(2).messageType
        fun mount(app: Int) = DynamicMessage.newBuilder(hty).setField(hty.findFieldByNumber(1), app).build()
        fun entry(d: Descriptor, uuid: String, app: Int) = DynamicMessage.newBuilder(d)
            .setField(d.findFieldByNumber(1), uuid)
            .setField(d.findFieldByNumber(2), mount(app))
            .build()
        val body = DynamicMessage.newBuilder(hqo)
            .addRepeatedField(hqo.findFieldByNumber(3), entry(parentEntry, "p1", 1))
            .addRepeatedField(hqo.findFieldByNumber(3), entry(parentEntry, "p2", 2))
            .addRepeatedField(hqo.findFieldByNumber(6), entry(childEntry, "baby", 3))
            .build()
        val msg = DynamicMessage.newBuilder(hqq).setField(hqq.findFieldByNumber(2), body).build()
        val decoded = DecodedGameAny(Direction.SERVER_TO_CLIENT, 1, null, "type.ankama.com/hqq", ByteArray(0), "hqq")
        val result = PaddockMapper.bredOffspring(decoded, msg)
        assertNotNull(result)
        assertEquals(setOf("p1", "p2", "baby"), result.keys)
        assertEquals(3, result["baby"]?.appearanceId)
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
}
