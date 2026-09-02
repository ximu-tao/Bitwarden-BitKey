package com.bitwarden.bitkey.model

import com.bitwarden.bitkey.protocol.BitKeyError

/**
 * The decoded contents of an ACK frame received from the BitKey device.
 *
 * @property seq Sequence number echoed from the originating request; always matches the
 * request that triggered the ACK.
 * @property error Protocol-level outcome reported by the firmware.
 * @property extra Any additional payload bytes that followed the error code. May be empty.
 */
data class BitKeyAck(
    val seq: Int,
    val error: BitKeyError,
    val extra: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BitKeyAck) return false
        return seq == other.seq && error == other.error && extra.contentEquals(other.extra)
    }

    override fun hashCode(): Int {
        var result = seq
        result = 31 * result + error.hashCode()
        result = 31 * result + extra.contentHashCode()
        return result
    }
}
