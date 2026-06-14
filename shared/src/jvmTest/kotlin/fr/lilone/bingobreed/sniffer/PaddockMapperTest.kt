package fr.lilone.bingobreed.sniffer

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.DynamicMessage
import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
import fr.lilone.bingobreed.sniffer.model.breeding.Sex
import fr.lilone.bingobreed.sniffer.parser.DescriptorRegistry
import fr.lilone.bingobreed.sniffer.parser.breeding.PaddockMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    fun `decode l'enclos depuis le contenu him`() {
        val him = desc("him")
        val hhx = him.findFieldByNumber(2).messageType   // jauge carburant
        val mountsField = him.findFieldByNumber(3)        // map<string, hlo>
        val entry = mountsField.messageType               // entrée de map
        val hlo = entry.findFieldByNumber(2).messageType  // monture
        val hll = hlo.findFieldByNumber(13).messageType   // jauge monture
        val kiv = hlo.findFieldByNumber(9).messageType    // effet

        // Jauge de carburant : valeur 53440, élément #2
        val fuel = DynamicMessage.newBuilder(hhx)
            .setField(hhx.findFieldByNumber(2), 53440)
            .setField(hhx.findFieldByNumber(3), hhx.findFieldByNumber(3).enumType.findValueByNumber(2))
            .build()

        // Jauge monture (type hhd / valeur)
        fun gauge(type: Int, value: Int) = DynamicMessage.newBuilder(hll).apply {
            setField(hll.findFieldByNumber(1), value)
            setField(hll.findFieldByNumber(2), hll.findFieldByNumber(2).enumType.findValueByNumber(type))
        }.build()

        // Effet : 138 (Puissance) = 10
        val effect = DynamicMessage.newBuilder(kiv)
            .setField(kiv.findFieldByNumber(8), 138)
            .setField(kiv.findFieldByNumber(3), 10)
            .build()

        val mount = DynamicMessage.newBuilder(hlo)
            .setField(hlo.findFieldByNumber(2), "enclos4monturegen4")
            .setField(hlo.findFieldByNumber(5), 26)
            .setField(hlo.findFieldByNumber(6), 8090)
            .setField(hlo.findFieldByNumber(10), 49)
            .addRepeatedField(hlo.findFieldByNumber(13), gauge(MountGauge.TYPE_LOVE, 16180))
            .addRepeatedField(hlo.findFieldByNumber(13), gauge(1, 0))
            .addRepeatedField(hlo.findFieldByNumber(13), gauge(2, 0))
            .addRepeatedField(hlo.findFieldByNumber(9), effect)
            .build()

        val mapEntry = DynamicMessage.newBuilder(entry)
            .setField(entry.findFieldByNumber(1), "e4c09997-96e5-46d7-b753-8500b83091b8")
            .setField(entry.findFieldByNumber(2), mount)
            .build()

        val content = DynamicMessage.newBuilder(him)
            .addRepeatedField(him.findFieldByNumber(2), fuel)
            .addRepeatedField(him.findFieldByNumber(3), mapEntry)
            .build()

        val paddock = PaddockMapper.fromContent(content)

        assertEquals(1, paddock.fuelGauges.size)
        assertEquals(53440, paddock.fuelGauges[0].value)

        val m = paddock.mounts["e4c09997-96e5-46d7-b753-8500b83091b8"]
        assertNotNull(m)
        assertEquals("enclos4monturegen4", m.name)
        assertEquals(26, m.level)
        assertEquals(8090, m.experience)
        assertEquals(49, m.serenity)
        assertEquals(16180, m.gauges.first { it.type == MountGauge.TYPE_LOVE }.value)
        assertEquals(10, m.effects.first { it.effectId == 138 }.value)
        // Champs ajoutés post-refonte : défauts du sample (♀, fertile, sans robe).
        assertEquals(Sex.FEMALE, m.sex)
        assertEquals(false, m.sterile)
        assertEquals(Fertility.FERTILE, m.fertility)
        assertNull(m.colors)
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
