package fr.lilone.bingobreed.sniffer

import com.google.protobuf.ByteString
import com.google.protobuf.UnknownFieldSet
import fr.lilone.bingobreed.sniffer.model.Direction
import fr.lilone.bingobreed.sniffer.model.DofusFrame
import fr.lilone.bingobreed.sniffer.model.StreamKey
import fr.lilone.bingobreed.sniffer.parser.GameAnyExtractor
import fr.lilone.bingobreed.sniffer.parser.TypeUrlRegistry
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameAnyExtractorTest {

    private val extractor = GameAnyExtractor()
    private val stream = StreamKey(
        InetSocketAddress.createUnresolved("client", 1),
        InetSocketAddress.createUnresolved("server", 5555),
    )

    private fun lenField(number: Int, bytes: ByteString) = UnknownFieldSet.Field.newBuilder()
        .addLengthDelimited(bytes).build().let { number to it }

    private fun any(typeUrl: String, value: ByteArray): ByteString {
        val builder = UnknownFieldSet.newBuilder()
        lenField(1, ByteString.copyFromUtf8(typeUrl)).also { builder.addField(it.first, it.second) }
        lenField(2, ByteString.copyFrom(value)).also { builder.addField(it.first, it.second) }
        return builder.build().toByteString()
    }

    private fun frame(direction: Direction, envelope: ByteString, topField: Int): DofusFrame {
        val top = UnknownFieldSet.newBuilder()
        lenField(topField, envelope).also { top.addField(it.first, it.second) }
        return DofusFrame(stream, direction, top.build().toByteArray())
    }

    @Test
    fun `serveur vers client - enveloppe field 1 contenant un Any`() {
        val payload = byteArrayOf(9, 8, 7)
        val envelope = UnknownFieldSet.newBuilder()
            .also { val f = lenField(2, any("type.ankama.com/jtg", payload)); it.addField(f.first, f.second) }
            .build().toByteString()

        val decoded = extractor.extract(frame(Direction.SERVER_TO_CLIENT, envelope, topField = 1), TypeUrlRegistry())
        assertNotNull(decoded)
        assertEquals("type.ankama.com/jtg", decoded.typeUrl)
        assertEquals("jtg", decoded.code)
        assertEquals(1, decoded.envelopeField)
        assertContentEquals(payload, decoded.value)
        assertNull(decoded.requestId)
    }

    @Test
    fun `client vers serveur - enveloppe avec id et Any`() {
        val payload = byteArrayOf(1, 2)
        val envelope = UnknownFieldSet.newBuilder()
            .addField(1, UnknownFieldSet.Field.newBuilder().addVarint(42).build())
            .also { val f = lenField(3, any("type.ankama.com/jtu", payload)); it.addField(f.first, f.second) }
            .build().toByteString()

        val decoded = extractor.extract(frame(Direction.CLIENT_TO_SERVER, envelope, topField = 2), TypeUrlRegistry())
        assertNotNull(decoded)
        assertEquals("jtu", decoded.code)
        assertEquals(2, decoded.envelopeField)
        assertEquals(42L, decoded.requestId)
        assertContentEquals(payload, decoded.value)
    }

    @Test
    fun `nom resolu depuis le registre`() {
        val envelope = UnknownFieldSet.newBuilder()
            .also { val f = lenField(2, any("type.ankama.com/ial", byteArrayOf())); it.addField(f.first, f.second) }
            .build().toByteString()
        val registry = TypeUrlRegistry().registerCode("ial", "AveragePrices")

        val decoded = extractor.extract(frame(Direction.SERVER_TO_CLIENT, envelope, topField = 1), registry)
        assertNotNull(decoded)
        assertEquals("AveragePrices", decoded.knownName)
    }

    @Test
    fun `frame non protobuf renvoie null`() {
        val decoded = extractor.extract(
            DofusFrame(stream, Direction.SERVER_TO_CLIENT, byteArrayOf(0xFF.toByte(), 0xFF.toByte())),
            TypeUrlRegistry(),
        )
        assertNull(decoded)
    }
}
