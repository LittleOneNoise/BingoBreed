package fr.lilone.bingobreed.sniffer

import fr.lilone.bingobreed.sniffer.parser.ProtobufFrameDecoder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtobufFrameDecoderTest {

    /** Encode une frame : varint(taille) + payload. */
    private fun frame(payload: ByteArray): ByteArray = encodeVarInt(payload.size) + payload

    private fun encodeVarInt(value: Int): ByteArray {
        var v = value
        val out = ArrayList<Byte>()
        while (true) {
            val b = v and 0x7F
            v = v ushr 7
            if (v == 0) { out.add(b.toByte()); break }
            out.add((b or 0x80).toByte())
        }
        return out.toByteArray()
    }

    @Test
    fun `une frame complete en un seul chunk`() {
        val decoder = ProtobufFrameDecoder()
        val payload = byteArrayOf(1, 2, 3, 4)
        val frames = decoder.decode(frame(payload))
        assertEquals(1, frames.size)
        assertContentEquals(payload, frames[0])
    }

    @Test
    fun `plusieurs frames dans un meme chunk`() {
        val decoder = ProtobufFrameDecoder()
        val a = byteArrayOf(10, 20)
        val b = byteArrayOf(30, 40, 50)
        val frames = decoder.decode(frame(a) + frame(b))
        assertEquals(2, frames.size)
        assertContentEquals(a, frames[0])
        assertContentEquals(b, frames[1])
    }

    @Test
    fun `frame fragmentee sur deux chunks`() {
        val decoder = ProtobufFrameDecoder()
        val payload = ByteArray(5) { it.toByte() }
        val full = frame(payload)
        val firstHalf = full.copyOfRange(0, 3)
        val secondHalf = full.copyOfRange(3, full.size)

        assertTrue(decoder.decode(firstHalf).isEmpty(), "frame incomplète => rien")
        val frames = decoder.decode(secondHalf)
        assertEquals(1, frames.size)
        assertContentEquals(payload, frames[0])
    }

    @Test
    fun `varint multi-octets (payload de 300 octets)`() {
        val decoder = ProtobufFrameDecoder()
        val payload = ByteArray(300) { (it % 256).toByte() } // taille 300 => varint sur 2 octets
        val frames = decoder.decode(frame(payload))
        assertEquals(1, frames.size)
        assertContentEquals(payload, frames[0])
    }

    @Test
    fun `varint lui-meme fragmente`() {
        val decoder = ProtobufFrameDecoder()
        val payload = ByteArray(300) { 7 }
        val full = frame(payload) // 2 octets de varint + 300
        // On coupe au milieu du varint (1er octet seulement)
        assertTrue(decoder.decode(full.copyOfRange(0, 1)).isEmpty())
        val frames = decoder.decode(full.copyOfRange(1, full.size))
        assertEquals(1, frames.size)
        assertContentEquals(payload, frames[0])
    }

    @Test
    fun `le reliquat est conserve entre les appels`() {
        val decoder = ProtobufFrameDecoder()
        val a = byteArrayOf(1, 2)
        val b = byteArrayOf(3, 4, 5)
        // 1ere frame complete + début de la 2e
        val combined = frame(a) + frame(b)
        val cut = frame(a).size + 1
        val first = decoder.decode(combined.copyOfRange(0, cut))
        assertEquals(1, first.size)
        assertContentEquals(a, first[0])

        val second = decoder.decode(combined.copyOfRange(cut, combined.size))
        assertEquals(1, second.size)
        assertContentEquals(b, second[0])
    }
}
