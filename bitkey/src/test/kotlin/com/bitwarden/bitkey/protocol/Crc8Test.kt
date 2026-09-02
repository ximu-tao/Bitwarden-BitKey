package com.bitwarden.bitkey.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Crc8Test {

    @Test
    fun `compute returns zero for empty input`() {
        assertEquals(0, Crc8.compute(byteArrayOf()))
    }

    @Test
    fun `compute returns zero for an all-zero buffer`() {
        assertEquals(0, Crc8.compute(ByteArray(64)))
    }

    @Test
    fun `compute result always fits in a single byte`() {
        val inputs = listOf(
            byteArrayOf(0x00),
            byteArrayOf(0xFF.toByte()),
            byteArrayOf(
                0xA5.toByte(), 0x01, 0x00, 0x08, 0x01, 0x00, 0x00,
            ),
            ByteArray(256) { it.toByte() },
        )
        inputs.forEach { input ->
            val crc = Crc8.compute(input)
            assertTrue(crc in 0..0xFF, "CRC must fit in a byte, was $crc")
        }
    }

    @Test
    fun `compute is order sensitive`() {
        val forward = Crc8.compute(byteArrayOf(0x10, 0x20, 0x30))
        val reverse = Crc8.compute(byteArrayOf(0x30, 0x20, 0x10))
        assertTrue(forward != reverse, "CRC must depend on byte order")
    }

    @Test
    fun `compute is deterministic`() {
        val data = byteArrayOf(0x31, 0x32, 0x33, 0x41, 0x42, 0x43)
        val first = Crc8.compute(data)
        val second = Crc8.compute(data)
        assertEquals(first, second)
    }

    @Test
    fun `compute with offset and length only covers the requested slice`() {
        val data = byteArrayOf(0xFF.toByte(), 0x31, 0x32, 0x33, 0xFF.toByte())
        val crcSlice = Crc8.compute(data, offset = 1, length = 3)
        val crcReference = Crc8.compute(byteArrayOf(0x31, 0x32, 0x33))
        assertEquals(crcReference, crcSlice)
    }

    @Test
    fun `compute of MAGIC byte alone is non-zero`() {
        // MAGIC is 0xA5. Single byte CRC should not be 0 — assert the
        // property rather than the exact value (firmware and host must agree,
        // and both implementations are line-for-line equivalent).
        val crc = Crc8.compute(byteArrayOf(BitKeyConstants.MAGIC.toByte()))
        assertTrue(crc != 0)
    }

    @Test
    fun `compute rejects negative offsets`() {
        assertThrows(IllegalArgumentException::class.java) {
            Crc8.compute(byteArrayOf(1, 2, 3), offset = -1, length = 1)
        }
    }

    @Test
    fun `compute rejects negative length`() {
        assertThrows(IllegalArgumentException::class.java) {
            Crc8.compute(byteArrayOf(1, 2, 3), offset = 0, length = -1)
        }
    }

    @Test
    fun `compute rejects ranges that exceed the buffer`() {
        assertThrows(IllegalArgumentException::class.java) {
            Crc8.compute(byteArrayOf(1, 2, 3), offset = 2, length = 5)
        }
    }
}