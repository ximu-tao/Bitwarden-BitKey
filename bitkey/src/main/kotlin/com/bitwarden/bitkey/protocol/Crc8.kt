package com.bitwarden.bitkey.protocol

/**
 * Computes the CRC8 used by the BitKey wire protocol.
 *
 * The firmware's reference implementation (see `BitKey/docs/PROTOCOL.md` § 9) uses:
 *  - polynomial: `0x07`
 *  - initial value: `0x00`
 *  - no reflected input/output
 *  - no final XOR
 *
 * The output is the same byte the firmware appends to every outgoing frame and the
 * byte it compares against when receiving one.
 */
object Crc8 {

    private const val POLYNOMIAL: Int = 0x07

    /**
     * Computes the CRC8 over [data]. The result is always a byte in the range `0..255`.
     *
     * This function is allocation-free and safe to use in hot paths.
     */
    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        require(offset >= 0) { "offset must be non-negative" }
        require(length >= 0) { "length must be non-negative" }
        require(offset + length <= data.size) { "offset + length exceeds data.size" }
        var crc = 0
        for (i in 0 until length) {
            crc = crc xor (data[offset + i].toInt() and 0xFF)
            for (bit in 0 until 8) {
                val mask = if ((crc and 0x80) != 0) POLYNOMIAL else 0
                crc = ((crc shl 1) and 0xFF) xor mask
            }
        }
        return crc and 0xFF
    }
}
