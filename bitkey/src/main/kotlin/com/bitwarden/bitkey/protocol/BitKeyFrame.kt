package com.bitwarden.bitkey.protocol

/**
 * A decoded BitKey frame.
 *
 * Instances are intended to be immutable and free to share across threads. The
 * payload bytes are copied by the parser, so callers may freely retain or discard
 * frames after they consume the data.
 *
 * @property seq Sequence number (0..255). The host increments this for every frame
 * it sends; the device echoes the value in its ACK reply.
 * @property flags OR-combination of [BitKeyFlags] values.
 * @property cmd Command code; see [BitKeyCommands].
 * @property payload Frame payload; never `null` but may be empty. Length is always
 * less than or equal to [BitKeyConstants.MAX_PAYLOAD].
 */
data class BitKeyFrame(
    val seq: Int,
    val flags: Int,
    val cmd: Int,
    val payload: ByteArray,
) {

    init {
        require(seq in 0..0xFF) { "seq must be in 0..255" }
        require(flags in 0..0xFF) { "flags must be in 0..255" }
        require(cmd in 0..0xFF) { "cmd must be in 0..255" }
        require(payload.size <= BitKeyConstants.MAX_PAYLOAD) {
            "payload exceeds MAX_PAYLOAD (${payload.size} > ${BitKeyConstants.MAX_PAYLOAD})"
        }
    }

    /** Returns `true` if this frame carries the [BitKeyFlags.FLAG_ACK] bit. */
    val requestsAck: Boolean get() = BitKeyFlags.has(flags, BitKeyFlags.FLAG_ACK)

    /** Returns `true` if this frame is an ERROR ACK (ACK + ERROR bits set). */
    val isErrorAck: Boolean get() =
        cmd == BitKeyCommands.CMD_ACK && BitKeyFlags.has(flags, BitKeyFlags.FLAG_ERROR)

    /**
     * Interprets this frame as an ACK and extracts the error code at the head of the
     * payload. Returns `null` when the frame is not a CMD_ACK frame.
     */
    fun ackErrorCode(): Int? = if (cmd == BitKeyCommands.CMD_ACK && payload.isNotEmpty()) {
        payload[0].toInt() and 0xFF
    } else {
        null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BitKeyFrame) return false
        return seq == other.seq &&
            flags == other.flags &&
            cmd == other.cmd &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = seq
        result = 31 * result + flags
        result = 31 * result + cmd
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
