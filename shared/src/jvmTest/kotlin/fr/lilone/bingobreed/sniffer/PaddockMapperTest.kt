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
    fun `decode l'enclos depuis le contenu hqm`() {
        // Structure post-patch 2026-07 / client 3.6.6.6 (cf. PaddockMapper.F) : hqm (contenu),
        // htf (carburant, valeur=1/élément=2), htd (monture), hta (jauge monture, type=2/valeur=3),
        // lgy (effet, id=11/valeur=2), htb (généalogie).
        val hqm = desc("hqm")
        val htf = hqm.findFieldByNumber(3).messageType     // jauge carburant
        val mountsField = hqm.findFieldByNumber(1)         // map<string, htd>
        val entry = mountsField.messageType                // entrée de map
        val htd = entry.findFieldByNumber(2).messageType   // monture
        val hta = htd.findFieldByNumber(9).messageType     // jauge monture
        val lgy = htd.findFieldByNumber(5).messageType     // effet
        val htb = htd.findFieldByNumber(4).messageType     // généalogie

        // Jauge de carburant : valeur 53440 (foec=1) / élément #2 (foed=2, enum hqh)
        val fuel = DynamicMessage.newBuilder(htf)
            .setField(htf.findFieldByNumber(1), 53440)
            .setField(htf.findFieldByNumber(2), htf.findFieldByNumber(2).enumType.findValueByNumber(2))
            .build()

        // Jauge monture : type (focx=2, enum hqi) / valeur (focy=3)
        fun gauge(type: Int, value: Int) = DynamicMessage.newBuilder(hta).apply {
            setField(hta.findFieldByNumber(2), hta.findFieldByNumber(2).enumType.findValueByNumber(type))
            setField(hta.findFieldByNumber(3), value)
        }.build()

        // Effet : 138 (Puissance, gbsp=11) = 10 (valeur simple gbss=2)
        val effect = DynamicMessage.newBuilder(lgy)
            .setField(lgy.findFieldByNumber(11), 138)
            .setField(lgy.findFieldByNumber(2), 10)
            .build()

        // Généalogie : robes des 2 parents (fodd=1, fode=2)
        val parents = DynamicMessage.newBuilder(htb)
            .setField(htb.findFieldByNumber(1), 115)
            .setField(htb.findFieldByNumber(2), 120)
            .build()

        val mount = DynamicMessage.newBuilder(htd)
            .setField(htd.findFieldByNumber(3), 97)                    // apparence/robe (fodk)
            .setField(htd.findFieldByNumber(7), "enclos4monturegen4")  // nom (fodo)
            .setField(htd.findFieldByNumber(12), 26)                   // niveau (fodu)
            .setField(htd.findFieldByNumber(10), 49)                   // sérénité (fods)
            .setField(htd.findFieldByNumber(8), 8090)                  // xp (fodq)
            .setField(htd.findFieldByNumber(4), parents)               // généalogie (fodl)
            .addRepeatedField(htd.findFieldByNumber(9), gauge(MountGauge.TYPE_LOVE, 16180))
            .addRepeatedField(htd.findFieldByNumber(9), gauge(1, 0))
            .addRepeatedField(htd.findFieldByNumber(9), gauge(2, 0))
            .addRepeatedField(htd.findFieldByNumber(5), effect)
            .build()

        val mapEntry = DynamicMessage.newBuilder(entry)
            .setField(entry.findFieldByNumber(1), "e4c09997-96e5-46d7-b753-8500b83091b8")
            .setField(entry.findFieldByNumber(2), mount)
            .build()

        val content = DynamicMessage.newBuilder(hqm)
            .addRepeatedField(hqm.findFieldByNumber(3), fuel)
            .addRepeatedField(hqm.findFieldByNumber(1), mapEntry)
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
        // Sample : ♀ (sex absent), non stérile → fertile.
        assertEquals(Sex.FEMALE, m.sex)
        assertEquals(false, m.sterile)
        assertEquals(Fertility.FERTILE, m.fertility)
    }

    @Test
    fun `bredPair extrait les 2 UUID parents du message hty`() {
        val hty = desc("hty")
        val a = "b649d8dd-1251-4e88-9151-e42a93e63096"
        val b = "b8303efb-4ab4-49af-9cb2-f942bdb824c3"
        val msg = DynamicMessage.newBuilder(hty)
            .setField(hty.findFieldByNumber(1), a)   // fogk
            .setField(hty.findFieldByNumber(2), b)   // fogl
            .build()
        val decoded = DecodedGameAny(
            direction = Direction.CLIENT_TO_SERVER,
            envelopeField = 1,
            requestId = null,
            typeUrl = "type.ankama.com/hty",
            value = ByteArray(0),
            knownName = "hty",
        )
        assertEquals(setOf(a, b), PaddockMapper.bredPair(decoded, msg))
        // Autre message : pas de paire.
        val other = DecodedGameAny(Direction.CLIENT_TO_SERVER, 1, null, "type.ankama.com/hsf", ByteArray(0), null)
        assertEquals(null, PaddockMapper.bredPair(other, msg))
    }

    @Test
    fun `une monture avec fodj=true est sterile`() {
        val hqm = desc("hqm")
        val entry = hqm.findFieldByNumber(1).messageType
        val htd = entry.findFieldByNumber(2).messageType
        val mount = DynamicMessage.newBuilder(htd)
            .setField(htd.findFieldByNumber(3), 93)     // apparence (fodk)
            .setField(htd.findFieldByNumber(2), true)   // stérile (fodj)
            .build()
        val mapEntry = DynamicMessage.newBuilder(entry)
            .setField(entry.findFieldByNumber(1), "u")
            .setField(entry.findFieldByNumber(2), mount)
            .build()
        val content = DynamicMessage.newBuilder(hqm)
            .addRepeatedField(hqm.findFieldByNumber(1), mapEntry)
            .build()
        val m = PaddockMapper.fromContent(content).mounts["u"]
        assertNotNull(m)
        assertEquals(true, m.sterile)
        assertEquals(Fertility.STERILE, m.fertility)
    }

    @Test
    fun `deactivatedElement lit l'element en fofo (champ 1) de htp`() {
        val htp = desc("htp")
        val endurance = htp.findFieldByNumber(1).enumType.findValueByNumber(2)
        val msg = DynamicMessage.newBuilder(htp).setField(htp.findFieldByNumber(1), endurance).build()
        val decoded = DecodedGameAny(Direction.CLIENT_TO_SERVER, 1, null, "type.ankama.com/htp", ByteArray(0), "htp")
        assertEquals(2, PaddockMapper.deactivatedElement(decoded, msg))
    }

    @Test
    fun `bredOffspring extrait les 2 parents et le nouveau-ne de hsy`() {
        val hsy = desc("hsy")
        val hsw = hsy.findFieldByNumber(2).messageType
        val parentEntry = hsw.findFieldByNumber(3).messageType // map entry <string, htd>
        val childEntry = hsw.findFieldByNumber(6).messageType
        val htd = parentEntry.findFieldByNumber(2).messageType
        fun mount(app: Int) = DynamicMessage.newBuilder(htd).setField(htd.findFieldByNumber(3), app).build()
        fun entry(d: Descriptor, uuid: String, app: Int) = DynamicMessage.newBuilder(d)
            .setField(d.findFieldByNumber(1), uuid)
            .setField(d.findFieldByNumber(2), mount(app))
            .build()
        val body = DynamicMessage.newBuilder(hsw)
            .addRepeatedField(hsw.findFieldByNumber(3), entry(parentEntry, "p1", 1))
            .addRepeatedField(hsw.findFieldByNumber(3), entry(parentEntry, "p2", 2))
            .addRepeatedField(hsw.findFieldByNumber(6), entry(childEntry, "baby", 3))
            .build()
        val msg = DynamicMessage.newBuilder(hsy).setField(hsy.findFieldByNumber(2), body).build()
        val decoded = DecodedGameAny(Direction.SERVER_TO_CLIENT, 1, null, "type.ankama.com/hsy", ByteArray(0), "hsy")
        val result = PaddockMapper.bredOffspring(decoded, msg)
        assertNotNull(result)
        assertEquals(setOf("p1", "p2", "baby"), result.keys)
        assertEquals(3, result["baby"]?.appearanceId)
    }

    @Test
    fun `unlockedPaddocks lit la map index-deverrouille de hrw`() {
        // Échantillon réel (capture 2026-07) : enclos 1..5 déverrouillés, 6ᵉ verrouillé.
        val hrw = desc("hrw")
        val entry = hrw.findFieldByNumber(2).messageType // map<int32, bool>
        fun mapEntry(idx: Int, unlocked: Boolean) = DynamicMessage.newBuilder(entry)
            .setField(entry.findFieldByNumber(1), idx)
            .setField(entry.findFieldByNumber(2), unlocked)
            .build()
        val msg = DynamicMessage.newBuilder(hrw).apply {
            (1..5).forEach { addRepeatedField(hrw.findFieldByNumber(2), mapEntry(it, true)) }
            addRepeatedField(hrw.findFieldByNumber(2), mapEntry(6, false))
        }.build()
        val decoded = DecodedGameAny(Direction.SERVER_TO_CLIENT, 1, null, "type.ankama.com/hrw", ByteArray(0), "hrw")
        val result = PaddockMapper.unlockedPaddocks(decoded, msg)
        assertEquals(mapOf(1 to true, 2 to true, 3 to true, 4 to true, 5 to true, 6 to false), result)
        // Autre message : pas de liste d'enclos.
        val other = DecodedGameAny(Direction.SERVER_TO_CLIENT, 1, null, "type.ankama.com/hsf", ByteArray(0), null)
        assertEquals(null, PaddockMapper.unlockedPaddocks(other, msg))
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
