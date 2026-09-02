package com.bitwarden.bitkey.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BitKeyFragmenterTest {

    @Test
    fun `empty payload returns an empty list`() {
        val frames = BitKeyFragmenter.fragment(
            cmd = BitKeyCommands.CMD_TYPE_TEXT,
            payload = ByteArray(0),
            startSeq = 1,
        )
        assertTrue(frames.isEmpty())
    }

    @Test
    fun `single small payload produces exactly one frame with START and END`() {
        val frames = BitKeyFragmenter.fragment(
            cmd = BitKeyCommands.CMD_TYPE_TEXT,
            payload = "hello".toByteArray(Charsets.UTF_8),
            startSeq = 3,
        )

        assertEquals(1, frames.size)
        val frame = frames.single()
        assertEquals(BitKeyCommands.CMD_TYPE_TEXT, frame.cmd)
        assertEquals(3, frame.seq)
        assertEquals(
            BitKeyFlags.FLAG_START or BitKeyFlags.FLAG_END or BitKeyFlags.FLAG_ACK,
            frame.flags,
        )
    }

    @Test
    fun `payloads larger than MAX_CHUNK are split across multiple frames`() {
        val payload = ByteArray(BitKeyFragmenter.MAX_CHUNK * 2 + 17) { (it and 0xFF).toByte() }
        val frames = BitKeyFragmenter.fragment(
            cmd = BitKeyCommands.CMD_TYPE_TEXT,
            payload = payload,
            startSeq = 0,
        )

        assertEquals(3, frames.size)
        // First frame
        assertTrue(BitKeyFlags.has(frames[0].flags, BitKeyFlags.FLAG_START))
        assertFalse(BitKeyFlags.has(frames[0].flags, BitKeyFlags.FLAG_END))
        assertTrue(BitKeyFlags.has(frames[0].flags, BitKeyFlags.FLAG_ACK))
        assertEquals(BitKeyFragmenter.MAX_CHUNK, frames[0].payload.size)

        // Middle frame
        assertFalse(BitKeyFlags.has(frames[1].flags, BitKeyFlags.FLAG_START))
        assertFalse(BitKeyFlags.has(frames[1].flags, BitKeyFlags.FLAG_END))
        assertFalse(BitKeyFlags.has(frames[1].flags, BitKeyFlags.FLAG_ACK))
        assertTrue(BitKeyFlags.has(frames[1].flags, BitKeyFlags.FLAG_CONT))
        assertEquals(BitKeyFragmenter.MAX_CHUNK, frames[1].payload.size)

        // Final frame
        assertFalse(BitKeyFlags.has(frames[2].flags, BitKeyFlags.FLAG_START))
        assertFalse(BitKeyFlags.has(frames[2].flags, BitKeyFlags.FLAG_CONT))
        assertTrue(BitKeyFlags.has(frames[2].flags, BitKeyFlags.FLAG_END))
        assertTrue(BitKeyFlags.has(frames[2].flags, BitKeyFlags.FLAG_ACK))
        assertEquals(17, frames[2].payload.size)
    }

    @Test
    fun `sequence numbers wrap at 256`() {
        val payload = ByteArray(BitKeyFragmenter.MAX_CHUNK * 2) { 0 }
        val frames = BitKeyFragmenter.fragment(
            cmd = BitKeyCommands.CMD_TYPE_TEXT,
            payload = payload,
            startSeq = 0xFF,
        )

        assertEquals(2, frames.size)
        assertEquals(0xFF, frames[0].seq)
        assertEquals(0x00, frames[1].seq)
    }

    @Test
    fun `expectAck=false removes the ACK flag from every frame`() {
        val payload = ByteArray(BitKeyFragmenter.MAX_CHUNK * 2 + 1) { 0 }
        val frames = BitKeyFragmenter.fragment(
            cmd = BitKeyCommands.CMD_TYPE_TEXT,
            payload = payload,
            startSeq = 0,
            expectAck = false,
        )

        assertEquals(3, frames.size)
        frames.forEach { frame ->
            assertFalse(BitKeyFlags.has(frame.flags, BitKeyFlags.FLAG_ACK))
        }
        // First frame must still have START and last must still have END.
        assertTrue(BitKeyFlags.has(frames.first().flags, BitKeyFlags.FLAG_START))
        assertTrue(BitKeyFlags.has(frames.last().flags, BitKeyFlags.FLAG_END))
    }

    @Test
    fun `all fragments reassemble back to the original payload`() {
        val payload = ByteArray(BitKeyFragmenter.MAX_CHUNK * 3 + 5) { (it and 0xFF).toByte() }
        val frames = BitKeyFragmenter.fragment(
            cmd = BitKeyCommands.CMD_TYPE_TEXT,
            payload = payload,
            startSeq = 1,
        )

        val reassembled = ByteArray(payload.size) { i ->
            var index = 0
            var consumed = 0
            for (frame in frames) {
                if (i - consumed < frame.payload.size) {
                    index = i - consumed
                    return@ByteArray frame.payload[index]
                }
                consumed += frame.payload.size
            }
            error("index $i exceeded payload size")
        }

        assertEquals(payload.toList(), reassembled.toList())
    }

    @Test
    fun `frame with startSeq out of range throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            BitKeyFragmenter.fragment(
                cmd = BitKeyCommands.CMD_TYPE_TEXT,
                payload = ByteArray(1),
                startSeq = -1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BitKeyFragmenter.fragment(
                cmd = BitKeyCommands.CMD_TYPE_TEXT,
                payload = ByteArray(1),
                startSeq = 0x100,
            )
        }
    }

    @Test
    fun `single-frame fragmentation does not set CONT`() {
        val frames = BitKeyFragmenter.fragment(
            cmd = BitKeyCommands.CMD_TYPE_TEXT,
            payload = ByteArray(10) { it.toByte() },
            startSeq = 0,
        )
        assertEquals(1, frames.size)
        assertFalse(BitKeyFlags.has(frames.single().flags, BitKeyFlags.FLAG_CONT))
    }
}