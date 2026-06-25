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
    fun `decode l'enclos depuis le contenu htu`() {
        // Structure post-patch 2026-06 (cf. PaddockMapper.F) : htu (contenu), hrm (carburant),
        // hsx (monture), hsu (jauge monture, type=1/valeur=2 inversés), lip (effet).
        val htu = desc("htu")
        val hrm = htu.findFieldByNumber(2).messageType    // jauge carburant
        val mountsField = htu.findFieldByNumber(5)         // map<string, hsx>
        val entry = mountsField.messageType                // entrée de map
        val hsx = entry.findFieldByNumber(2).messageType   // monture
        val hsu = hsx.findFieldByNumber(10).messageType    // jauge monture
        val lip = hsx.findFieldByNumber(13).messageType    // effet
        val hsv = hsx.findFieldByNumber(2).messageType     // généalogie

        // Jauge de carburant : valeur 53440 (fnkn=2), élément #2 (fnko=3)
        val fuel = DynamicMessage.newBuilder(hrm)
            .setField(hrm.findFieldByNumber(2), 53440)
            .setField(hrm.findFieldByNumber(3), hrm.findFieldByNumber(3).enumType.findValueByNumber(2))
            .build()

        // Jauge monture : type (fnow=1, enum hpe) / valeur (fnox=2)
        fun gauge(type: Int, value: Int) = DynamicMessage.newBuilder(hsu).apply {
            setField(hsu.findFieldByNumber(1), hsu.findFieldByNumber(1).enumType.findValueByNumber(type))
            setField(hsu.findFieldByNumber(2), value)
        }.build()

        // Effet : 138 (Puissance, gbpd=11) = 10 (valeur simple gbpo=10)
        val effect = DynamicMessage.newBuilder(lip)
            .setField(lip.findFieldByNumber(11), 138)
            .setField(lip.findFieldByNumber(10), 10)
            .build()

        // Généalogie : robes des 2 parents (fnpb=1, fnpc=2)
        val parents = DynamicMessage.newBuilder(hsv)
            .setField(hsv.findFieldByNumber(1), 115)
            .setField(hsv.findFieldByNumber(2), 120)
            .build()

        val mount = DynamicMessage.newBuilder(hsx)
            .setField(hsx.findFieldByNumber(3), 97)                    // apparence/robe
            .setField(hsx.findFieldByNumber(4), "enclos4monturegen4")  // nom
            .setField(hsx.findFieldByNumber(5), 26)                    // niveau
            .setField(hsx.findFieldByNumber(7), 49)                    // sérénité
            .setField(hsx.findFieldByNumber(11), 8090)                 // xp
            .setField(hsx.findFieldByNumber(2), parents)               // généalogie
            .addRepeatedField(hsx.findFieldByNumber(10), gauge(MountGauge.TYPE_LOVE, 16180))
            .addRepeatedField(hsx.findFieldByNumber(10), gauge(1, 0))
            .addRepeatedField(hsx.findFieldByNumber(10), gauge(2, 0))
            .addRepeatedField(hsx.findFieldByNumber(13), effect)
            .build()

        val mapEntry = DynamicMessage.newBuilder(entry)
            .setField(entry.findFieldByNumber(1), "e4c09997-96e5-46d7-b753-8500b83091b8")
            .setField(entry.findFieldByNumber(2), mount)
            .build()

        val content = DynamicMessage.newBuilder(htu)
            .addRepeatedField(htu.findFieldByNumber(2), fuel)
            .addRepeatedField(htu.findFieldByNumber(5), mapEntry)
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
    fun `derive la fertilite cote client`() {
        val maxed = listOf(MountGauge(0, 20000), MountGauge(1, 20000), MountGauge(2, 20000))
        val partial = listOf(MountGauge(0, 80), MountGauge(1, 20000), MountGauge(2, 0))
        // Stérile prime, même jauges au max.
        assertEquals(Fertility.STERILE, Fertility.of(sterile = true, maxed))
        assertEquals(Fertility.FECONDE, Fertility.of(sterile = false, maxed))
        assertEquals(Fertility.FERTILE, Fertility.of(sterile = false, partial))
    }
}
