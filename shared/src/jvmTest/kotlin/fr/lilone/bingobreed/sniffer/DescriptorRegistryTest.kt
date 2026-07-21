package fr.lilone.bingobreed.sniffer

import fr.lilone.bingobreed.sniffer.parser.DescriptorRegistry
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DescriptorRegistryTest {

    @Test
    fun `charge le descripteur protodec et resout les codes type_url`() {
        val registry = DescriptorRegistry.loadFromClasspath()
        assertTrue(registry.size > 1000, "descripteur protodec attendu (>1000 messages), trouvé ${registry.size}")

        // Codes observés dans le flux de jeu réel (élevage, patch 2026-07 / client 3.6.6.6).
        for (code in listOf("hqz", "hte", "huc", "htw", "hsn", "hty", "hsy")) {
            assertNotNull(registry.findByTypeUrl("type.ankama.com/$code"), "code '$code' introuvable")
        }
    }
}
