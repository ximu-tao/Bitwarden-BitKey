package com.bitwarden.bitkey.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BitKeyProtocolTest {

    @Test
    fun `ping builds a no-payload CMD_PING frame with FLAG_ACK`() {
        val frame = BitKeyProtocol.ping(seq = 7)

        assertEquals(7, frame.seq)
        assertEquals(BitKeyCommands.CMD_PING, frame.cmd)
        assertEquals(BitKeyFlags.FLAG_ACK, frame.flags)
        assertEquals(0, frame.payload.size)
        assertTrue(frame.requestsAck)
    }

    @Test
    fun `sessionStart and sessionEnd build the corresponding no-payload frames`() {
        val start = BitKeyProtocol.sessionStart(seq = 1)
        val end = BitKeyProtocol.sessionEnd(seq = 2)

        assertEquals(BitKeyCommands.CMD_SESSION_START, start.cmd)
        assertEquals(BitKeyCommands.CMD_SESSION_END, end.cmd)
        assertEquals(BitKeyFlags.FLAG_ACK, start.flags)
        assertEquals(BitKeyFlags.FLAG_ACK, end.flags)
        assertEquals(0, start.payload.size)
        assertEquals(0, end.payload.size)
    }

    @Test
    fun `typeText packs the UTF-8 representation of the string`() {
        val frame = BitKeyProtocol.typeText(seq = 3, text = "Hello, 世界!")

        assertEquals(BitKeyCommands.CMD_TYPE_TEXT, frame.cmd)
        assertArrayEquals("Hello, 世界!".toByteArray(Charsets.UTF_8), frame.payload)
        assertTrue(frame.requestsAck)
    }

    @Test
    fun `typeText accepts an empty payload but the firmware will reject it`() {
        val frame = BitKeyProtocol.typeText(seq = 0, text = "")

        assertEquals(0, frame.payload.size)
        assertEquals(BitKeyCommands.CMD_TYPE_TEXT, frame.cmd)
    }

    @Test
    fun `typeKey encodes keycode and modifier byte in the payload`() {
        val frame = BitKeyProtocol.typeKey(
            seq = 9,
            keycode = 0x28, // Enter
            modifiers = 0x02, // Left Shift
        )

        assertEquals(BitKeyCommands.CMD_TYPE_KEY, frame.cmd)
        assertEquals(2, frame.payload.size)
        assertEquals(0x28.toByte(), frame.payload[0])
        assertEquals(0x02.toByte(), frame.payload[1])
    }

    @Test
    fun `typeCombo encodes modifier count and keycodes in the payload`() {
        val frame = BitKeyProtocol.typeCombo(
            seq = 11,
            modifiers = 0x01, // Left Ctrl
            keycodes = listOf(0x06, 0x19), // C + V
        )

        assertEquals(BitKeyCommands.CMD_TYPE_COMBO, frame.cmd)
        assertEquals(4, frame.payload.size)
        assertEquals(0x01.toByte(), frame.payload[0])
        assertEquals(2.toByte(), frame.payload[1])
        assertEquals(0x06.toByte(), frame.payload[2])
        assertEquals(0x19.toByte(), frame.payload[3])
    }

    @Test
    fun `typeCombo rejects an empty keycode list`() {
        assertThrows(IllegalArgumentException::class.java) {
            BitKeyProtocol.typeCombo(seq = 0, modifiers = 0, keycodes = emptyList())
        }
    }

    @Test
    fun `typeCombo rejects more than 16 keycodes`() {
        assertThrows(IllegalArgumentException::class.java) {
            BitKeyProtocol.typeCombo(
                seq = 0,
                modifiers = 0,
                keycodes = List(17) { it },
            )
        }
    }

    @Test
    fun `getStatus and pairRequest build the corresponding frames`() {
        val status = BitKeyProtocol.getStatus(seq = 4)
        val pair = BitKeyProtocol.pairRequest(seq = 5)

        assertEquals(BitKeyCommands.CMD_GET_STATUS, status.cmd)
        assertEquals(BitKeyCommands.CMD_PAIR_REQUEST, pair.cmd)
        assertEquals(0, status.payload.size)
        assertEquals(0, pair.payload.size)
    }

    @Test
    fun `isAsciiPrintable returns false for empty text`() {
        assertFalse(BitKeyProtocol.isAsciiPrintable(""))
    }

    @Test
    fun `isAsciiPrintable accepts typical password characters`() {
        assertTrue(BitKeyProtocol.isAsciiPrintable("P@ssw0rd!"))
        assertTrue(BitKeyProtocol.isAsciiPrintable("~-_=+[]{}|;:,.<>?"))
        assertTrue(BitKeyProtocol.isAsciiPrintable("0123456789"))
    }

    @Test
    fun `isAsciiPrintable rejects newline tab and control characters`() {
        assertFalse(BitKeyProtocol.isAsciiPrintable("Hello\nWorld"))
        assertFalse(BitKeyProtocol.isAsciiPrintable("Tab\there"))
        assertFalse(BitKeyProtocol.isAsciiPrintable("\u0001"))
        assertFalse(BitKeyProtocol.isAsciiPrintable("Hello\u007F"))
    }

    @Test
    fun `isAsciiPrintable rejects non-ASCII characters`() {
        assertFalse(BitKeyProtocol.isAsciiPrintable("héllo"))
        assertFalse(BitKeyProtocol.isAsciiPrintable("密码"))
        assertFalse(BitKeyProtocol.isAsciiPrintable("emoji😀"))
    }

    @Test
    fun `typeText produces a frame whose payload length matches the encoded size`() {
        val text = "Hello, World!"
        val frame = BitKeyProtocol.typeText(seq = 0, text = text)

        assertEquals(text.toByteArray(Charsets.UTF_8).size, frame.payload.size)
    }
}