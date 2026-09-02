package com.bitwarden.bitkey.protocol

/**
 * Pure, allocation-conscious helpers for encoding BitKey frames.
 *
 * All functions here are side-effect free and safe to call from any thread. They are
 * deliberately kept separate from the BLE layer so the same encoding logic is shared
 * between production code, tests, and any future transports.
 */
object BitKeyFrameEncoder {

    /**
     * Encodes [frame] into the wire format described in `BitKey/docs/PROTOCOL.md` § 1.
     *
     * The returned array is freshly allocated; callers take ownership of it. The function
     * performs no I/O and never throws for protocol-conformant input; validation is
     * delegated to the [BitKeyFrame] constructor.
     */
    fun encode(frame: BitKeyFrame): ByteArray {
        val payload = frame.payload
        val totalLength = BitKeyConstants.HEADER_SIZE + payload.size + BitKeyConstants.CRC_SIZE
        val output = ByteArray(totalLength)
        output[0] = BitKeyConstants.MAGIC.toByte()
        output[1] = BitKeyConstants.VERSION.toByte()
        output[2] = frame.seq.toByte()
        output[3] = frame.flags.toByte()
        output[4] = frame.cmd.toByte()
        output[5] = ((payload.size shr 8) and 0xFF).toByte()
        output[6] = (payload.size and 0xFF).toByte()
        if (payload.isNotEmpty()) {
            payload.copyInto(output, destinationOffset = BitKeyConstants.HEADER_SIZE)
        }
        val crcOffset = totalLength - BitKeyConstants.CRC_SIZE
        output[crcOffset] = Crc8.compute(output, offset = 0, length = crcOffset).toByte()
        return output
    }
}
