package com.bitwarden.bitkey.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BitKeyFrameCodecTest {

    @Test
    fun `encode writes the magic version header for empty payload frames`() {
        val frame = BitKeyFrame(
            seq = 0,
            flags = BitKeyFlags.FLAG_ACK,
            cmd = BitKeyCommands.CMD_PING,
            payload = ByteArray(0),
        )
        val bytes = BitKeyFrameEncoder.encode(frame)

        // header(7) + payload(0) + crc(1)
        assertEquals(BitKeyConstants.HEADER_SIZE + BitKeyConstants.CRC_SIZE, bytes.size)
        assertEquals(BitKeyConstants.MAGIC.toByte(), bytes[0])
        assertEquals(BitKeyConstants.VERSION.toByte(), bytes[1])
        assertEquals(0x00.toByte(), bytes[2]) // seq
        assertEquals(BitKeyFlags.FLAG_ACK.toByte(), bytes[3]) // flags
        assertEquals(BitKeyCommands.CMD_PING.toByte(), bytes[4]) // cmd
        assertEquals(0x00.toByte(), bytes[5]) // len_hi
        assertEquals(0x00.toByte(), bytes[6]) // len_lo
    }

    @Test
    fun `encode encodes payload length as big-endian`() {
        val payload = ByteArray(300) { it.toByte() }
        val frame = BitKeyFrame(
            seq = 5,
            flags = 0,
            cmd = BitKeyCommands.CMD_TYPE_TEXT,
            payload = payload,
        )
        val bytes = BitKeyFrameEncoder.encode(frame)

        val lenHi = (payload.size shr 8) and 0xFF
        val lenLo = payload.size and 0xFF
        assertEquals(lenHi.toByte(), bytes[5])
        assertEquals(lenLo.toByte(), bytes[6])
    }

    @Test
    fun `encode payload bytes match the input`() {
        val payload = "hello".toByteArray(Charsets.UTF_8)
        val frame = BitKeyFrame(
            seq = 0x42,
            flags = BitKeyFlags.FLAG_ACK,
            cmd = BitKeyCommands.CMD_TYPE_TEXT,
            payload = payload,
        )
        val bytes = BitKeyFrameEncoder.encode(frame)

        val payloadRange = BitKeyConstants.HEADER_SIZE until
            BitKeyConstants.HEADER_SIZE + payload.size
        assertArrayEquals(payload, bytes.copyOfRange(payloadRange.first, payloadRange.last + 1))
    }

    @Test
    fun `encode appends CRC over header plus payload`() {
        val payload = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val frame = BitKeyFrame(
            seq = 7,
            flags = 0,
            cmd = BitKeyCommands.CMD_GET_STATUS,
            payload = payload,
        )
        val bytes = BitKeyFrameEncoder.encode(frame)

        val crcOffset = bytes.size - BitKeyConstants.CRC_SIZE
        val expectedCrc = Crc8.compute(bytes, offset = 0, length = crcOffset)
        assertEquals(expectedCrc.toByte(), bytes[crcOffset])
    }

    @Test
    fun `encode round-trips through the parser`() {
        val frame = BitKeyFrame(
            seq = 0xAB.toByte().toInt(),
            flags = BitKeyFlags.FLAG_ACK or BitKeyFlags.FLAG_START,
            cmd = BitKeyCommands.CMD_TYPE_COMBO,
            payload = byteArrayOf(0x02, 0x03, 0x04, 0x05, 0x06),
        )
        val encoded = BitKeyFrameEncoder.encode(frame)

        val parser = BitKeyFrameParser()
        val results = parser.feed(encoded)
        val ok = results.filterIsInstance<BitKeyParseResult.Ok>().single().frame

        assertEquals(frame.seq, ok.seq)
        assertEquals(frame.flags, ok.flags)
        assertEquals(frame.cmd, ok.cmd)
        assertArrayEquals(frame.payload, ok.payload)
    }

    @Test
    fun `frame requestsAck mirrors FLAG_ACK`() {
        val withoutAck = BitKeyFrame(0, 0, BitKeyCommands.CMD_PING, ByteArray(0))
        val withAck = withoutAck.copy(flags = BitKeyFlags.FLAG_ACK)

        assertFalse(withoutAck.requestsAck)
        assertTrue(withAck.requestsAck)
    }

    @Test
    fun `frame isErrorAck is only true when CMD_ACK and FLAG_ERROR are both set`() {
        val ack = BitKeyFrame(
            seq = 0,
            flags = BitKeyFlags.FLAG_ACK,
            cmd = BitKeyCommands.CMD_ACK,
            payload = byteArrayOf(BitKeyError.None.code.toByte()),
        )
        val errorAck = ack.copy(flags = BitKeyFlags.FLAG_ACK or BitKeyFlags.FLAG_ERROR)
        val ackWithoutErrorFlag = BitKeyFrame(
            seq = 0,
            flags = BitKeyFlags.FLAG_ERROR,
            cmd = BitKeyCommands.CMD_PING,
            payload = ByteArray(0),
        )

        assertFalse(ack.isErrorAck)
        assertTrue(errorAck.isErrorAck)
        assertFalse(ackWithoutErrorFlag.isErrorAck)
    }

    @Test
    fun `ackErrorCode returns the first byte of payload for ACK frames`() {
        val ack = BitKeyFrame(
            seq = 0,
            flags = BitKeyFlags.FLAG_ACK or BitKeyFlags.FLAG_ERROR,
            cmd = BitKeyCommands.CMD_ACK,
            payload = byteArrayOf(0x05, 0xAA.toByte(), 0xBB.toByte()),
        )
        assertEquals(0x05, ack.ackErrorCode())
    }

    @Test
    fun `ackErrorCode returns null for non-ACK frames`() {
        val ping = BitKeyFrame(0, 0, BitKeyCommands.CMD_PING, byteArrayOf(0x05))
        assertNull(ping.ackErrorCode())
    }

    @Test
    fun `ackErrorCode returns null for ACK frames with empty payload`() {
        val ack = BitKeyFrame(0, BitKeyFlags.FLAG_ACK, BitKeyCommands.CMD_ACK, ByteArray(0))
        assertNull(ack.ackErrorCode())
    }

    @Test
    fun `frame constructor rejects payloads larger than MAX_PAYLOAD`() {
        val oversized = ByteArray(BitKeyConstants.MAX_PAYLOAD + 1)
        assertThrows(IllegalArgumentException::class.java) {
            BitKeyFrame(0, 0, BitKeyCommands.CMD_TYPE_TEXT, oversized)
        }
    }

    @Test
    fun `frame constructor rejects out of range byte fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            BitKeyFrame(seq = -1, flags = 0, cmd = 0, payload = ByteArray(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BitKeyFrame(seq = 0, flags = 0x1FF, cmd = 0, payload = ByteArray(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BitKeyFrame(seq = 0, flags = 0, cmd = 0x100, payload = ByteArray(0))
        }
    }

    @Test
    fun `frame equality compares payload content not identity`() {
        val a = BitKeyFrame(1, 0, BitKeyCommands.CMD_PING, byteArrayOf(0x10, 0x20))
        val b = BitKeyFrame(1, 0, BitKeyCommands.CMD_PING, byteArrayOf(0x10, 0x20))
        val c = a.copy(payload = byteArrayOf(0x10, 0x21))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == c)
    }

    @Test
    fun `BitKeyError fromCode maps known and unknown values`() {
        assertEquals(BitKeyError.None, BitKeyError.fromCode(0x00))
        assertEquals(BitKeyError.BufferFull, BitKeyError.fromCode(0x08))
        assertEquals(BitKeyError.Unknown, BitKeyError.fromCode(0xFE))
    }
}