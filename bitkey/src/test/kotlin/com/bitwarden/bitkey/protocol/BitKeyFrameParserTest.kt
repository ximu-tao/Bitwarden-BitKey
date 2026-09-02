package com.bitwarden.bitkey.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BitKeyFrameParserTest {

    private fun sampleFrame(
        seq: Int = 0,
        flags: Int = 0,
        cmd: Int = BitKeyCommands.CMD_PING,
        payload: ByteArray = ByteArray(0),
    ): ByteArray = BitKeyFrameEncoder.encode(
        BitKeyFrame(seq = seq, flags = flags, cmd = cmd, payload = payload),
    )

    @Test
    fun `feed returns NeedMore when given too few bytes for the header`() {
        val parser = BitKeyFrameParser()
        val results = parser.feed(byteArrayOf(0xA5.toByte(), 0x01, 0x00))

        assertEquals(1, results.size)
        assertEquals(BitKeyParseResult.NeedMore, results.single())
    }

    @Test
    fun `feed decodes a complete empty payload frame`() {
        val parser = BitKeyFrameParser()
        val results = parser.feed(sampleFrame(cmd = BitKeyCommands.CMD_PING))

        val ok = results.filterIsInstance<BitKeyParseResult.Ok>().single()
        assertEquals(BitKeyCommands.CMD_PING, ok.frame.cmd)
        assertEquals(0, ok.frame.payload.size)
    }

    @Test
    fun `feed decodes a frame with payload`() {
        val payload = "hello".toByteArray(Charsets.UTF_8)
        val parser = BitKeyFrameParser()
        val results = parser.feed(
            sampleFrame(
                flags = BitKeyFlags.FLAG_ACK,
                cmd = BitKeyCommands.CMD_TYPE_TEXT,
                payload = payload,
            ),
        )

        val ok = results.filterIsInstance<BitKeyParseResult.Ok>().single()
        assertEquals(BitKeyCommands.CMD_TYPE_TEXT, ok.frame.cmd)
        assertEquals(BitKeyFlags.FLAG_ACK, ok.frame.flags)
        assertArrayEquals(payload, ok.frame.payload)
    }

    @Test
    fun `feed byte-by-byte reconstructs the original frame`() {
        val encoded = sampleFrame(
            seq = 0xAB.toByte().toInt(),
            flags = BitKeyFlags.FLAG_ACK or BitKeyFlags.FLAG_START,
            cmd = BitKeyCommands.CMD_TYPE_COMBO,
            payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05),
        )

        val parser = BitKeyFrameParser()
        var decoded: BitKeyFrame? = null
        encoded.forEach { byte ->
            val results = parser.feed(byteArrayOf(byte))
            results.forEach { result ->
                if (result is BitKeyParseResult.Ok) {
                    decoded = result.frame
                }
            }
        }

        assertTrue(decoded != null, "frame should be decoded after byte-by-byte input")
        decoded?.let {
            assertEquals(0xAB.toByte().toInt(), it.seq)
            assertEquals(BitKeyFlags.FLAG_ACK or BitKeyFlags.FLAG_START, it.flags)
            assertEquals(BitKeyCommands.CMD_TYPE_COMBO, it.cmd)
            assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05), it.payload)
        }
    }

    @Test
    fun `feed handles multi-frame notifications in a single call`() {
        val frame1 = sampleFrame(seq = 1, cmd = BitKeyCommands.CMD_PING)
        val frame2 = sampleFrame(seq = 2, cmd = BitKeyCommands.CMD_SESSION_START)
        val combined = frame1 + frame2

        val parser = BitKeyFrameParser()
        val results = parser.feed(combined)
        val okFrames = results.filterIsInstance<BitKeyParseResult.Ok>().map { it.frame }

        assertEquals(2, okFrames.size)
        assertEquals(1, okFrames[0].seq)
        assertEquals(BitKeyCommands.CMD_PING, okFrames[0].cmd)
        assertEquals(2, okFrames[1].seq)
        assertEquals(BitKeyCommands.CMD_SESSION_START, okFrames[1].cmd)
    }

    @Test
    fun `feed discards garbage bytes until a MAGIC is seen`() {
        val parser = BitKeyFrameParser()
        val garbage = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9A.toByte())
        val frame = sampleFrame()

        val results = (garbage + frame).let { parser.feed(it) }
        val ok = results.filterIsInstance<BitKeyParseResult.Ok>().singleOrNull()

        assertEquals(5, parser.discardedBytes)
        assertTrue(ok != null, "frame should be parsed after garbage prefix")
    }

    @Test
    fun `feed reports BadMagic when the first accumulated byte is not MAGIC`() {
        val parser = BitKeyFrameParser()
        // Prime the parser to advance out of the discard phase.
        parser.feed(byteArrayOf(0x00, 0xA5.toByte()))
        // Now feed a frame whose first byte is NOT 0xA5; the parser should
        // report a Malformed result with reason BadMagic instead of throwing.
        val tampered = sampleFrame().also { it[0] = 0x55 }
        val results = parser.feed(tampered)

        val malformed = results.filterIsInstance<BitKeyParseResult.Malformed>().firstOrNull()
        assertTrue(malformed != null, "expected a Malformed event")
        assertEquals(
            BitKeyParseResult.Malformed.Reason.BadMagic,
            malformed?.reason,
        )
    }

    @Test
    fun `feed reports BadVersion when VERSION byte does not match`() {
        val parser = BitKeyFrameParser()
        val tampered = sampleFrame().also { it[1] = 0x99.toByte() }
        val results = parser.feed(tampered)

        val malformed = results.filterIsInstance<BitKeyParseResult.Malformed>().firstOrNull()
        assertTrue(malformed != null, "expected a Malformed event")
        assertEquals(
            BitKeyParseResult.Malformed.Reason.BadVersion,
            malformed?.reason,
        )
    }

    @Test
    fun `feed reports BadLength when the declared length exceeds MAX_PAYLOAD`() {
        val parser = BitKeyFrameParser()
        val tampered = sampleFrame().also {
            it[5] = 0x10
            it[6] = 0x00 // length = 0x1000, well over MAX_PAYLOAD
        }
        val results = parser.feed(tampered)

        val malformed = results.filterIsInstance<BitKeyParseResult.Malformed>().firstOrNull()
        assertTrue(malformed != null, "expected a Malformed event")
        assertEquals(
            BitKeyParseResult.Malformed.Reason.BadLength,
            malformed?.reason,
        )
    }

    @Test
    fun `feed reports BadCrc when the trailing CRC byte is wrong`() {
        val parser = BitKeyFrameParser()
        val tampered = sampleFrame().also {
            val last = it.size - 1
            it[last] = (it[last].toInt() xor 0xFF).toByte()
        }
        val results = parser.feed(tampered)

        val malformed = results.filterIsInstance<BitKeyParseResult.Malformed>().firstOrNull()
        assertTrue(malformed != null, "expected a Malformed event")
        assertEquals(
            BitKeyParseResult.Malformed.Reason.BadCrc,
            malformed?.reason,
        )
    }

    @Test
    fun `feed continues parsing after a Malformed event`() {
        val parser = BitKeyFrameParser()
        val tampered = sampleFrame().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0xFF).toByte() }
        val goodFrame = sampleFrame(seq = 7, cmd = BitKeyCommands.CMD_PING)

        val results = parser.feed(tampered + goodFrame)
        val okFrames = results.filterIsInstance<BitKeyParseResult.Ok>().map { it.frame }

        assertEquals(1, okFrames.size, "valid frame following a malformed one must still be decoded")
        assertEquals(7, okFrames.single().seq)
    }

    @Test
    fun `feed of zero-length input returns NeedMore without consuming state`() {
        val parser = BitKeyFrameParser()
        val initial = parser.feed(byteArrayOf(0xA5.toByte(), 0x01))
        val empty = parser.feed(byteArrayOf())

        assertEquals(BitKeyParseResult.NeedMore, initial.last())
        assertEquals(1, empty.size)
        assertEquals(BitKeyParseResult.NeedMore, empty.single())
    }

    @Test
    fun `feed with offset and length only consumes the requested slice`() {
        val encoded = sampleFrame()
        val wrapped = ByteArray(encoded.size + 4)
        // Prefix with two garbage bytes and suffix with two more.
        wrapped[0] = 0x11
        wrapped[1] = 0x22
        encoded.copyInto(wrapped, destinationOffset = 2)
        wrapped[wrapped.size - 2] = 0x33
        wrapped[wrapped.size - 1] = 0x44

        val parser = BitKeyFrameParser()
        val results = parser.feed(wrapped, offset = 2, length = encoded.size)

        val ok = results.filterIsInstance<BitKeyParseResult.Ok>().singleOrNull()
        assertTrue(ok != null, "frame within slice should be decoded")
        assertEquals(BitKeyCommands.CMD_PING, ok?.frame?.cmd)
    }

    @Test
    fun `reset clears parser state so a fresh frame can be decoded`() {
        val parser = BitKeyFrameParser()
        // Push a partial header.
        parser.feed(byteArrayOf(0xA5.toByte(), 0x01))
        parser.reset()
        val results = parser.feed(sampleFrame(cmd = BitKeyCommands.CMD_SESSION_START))

        val ok = results.filterIsInstance<BitKeyParseResult.Ok>().single()
        assertEquals(BitKeyCommands.CMD_SESSION_START, ok.frame.cmd)
    }
}