package com.bitwarden.bitkey.protocol

/**
 * Splits large payloads into multi-frame chunks using the protocol's START/CONT/END flags.
 *
 * The current firmware processes payloads as a single frame and rejects anything larger than
 * [BitKeyConstants.MAX_PAYLOAD]. [fragment] returns one or more frames where:
 *
 *  - the first frame carries [BitKeyFlags.FLAG_START] and `FLAG_ACK` (when [expectAck] is true);
 *  - intermediate frames carry `FLAG_CONT` (+ `FLAG_ACK` on the last chunk so the caller still
 *    gets a confirmation);
 *  - the final frame carries `FLAG_END` (and `FLAG_ACK` if requested).
 *
 * Even though the firmware today only consumes the final frame, the same function is used by
 * tests to verify that the host never assembles a payload larger than [BitKeyConstants.MAX_PAYLOAD].
 *
 * @param cmd command code shared by every emitted frame.
 * @param payload full payload that must be sent to the device.
 * @param startSeq sequence number assigned to the first emitted frame; subsequent frames use
 * `startSeq + 1`, `startSeq + 2`, … wrapping at 256.
 * @param expectAck when `true`, only the first and last frames set `FLAG_ACK`. Intermediate
 * frames set no flags so the device does not produce an ACK for each fragment.
 */
object BitKeyFragmenter {

    /**
     * Maximum payload size supported by a single frame.
     */
    const val MAX_CHUNK: Int = BitKeyConstants.MAX_PAYLOAD

    /**
     * Slices [payload] into individual [BitKeyFrame] chunks no larger than [MAX_CHUNK].
     *
     * Returns an empty list when [payload] is empty.
     */
    fun fragment(
        cmd: Int,
        payload: ByteArray,
        startSeq: Int,
        expectAck: Boolean = true,
    ): List<BitKeyFrame> {
        require(startSeq in 0..0xFF) { "startSeq must be in 0..255" }
        if (payload.isEmpty()) return emptyList()
        val chunks = payload.size / MAX_CHUNK +
            (if (payload.size % MAX_CHUNK != 0) 1 else 0)
        val frames = ArrayList<BitKeyFrame>(chunks)
        var offset = 0
        var index = 0
        while (offset < payload.size) {
            val end = (offset + MAX_CHUNK).coerceAtMost(payload.size)
            val chunk = payload.copyOfRange(offset, end)
            val isFirst = index == 0
            val isLast = end == payload.size
            val flags = buildFlags(isFirst, isLast, expectAck)
            val frame = BitKeyFrame(
                seq = ((startSeq + index) and 0xFF),
                flags = flags,
                cmd = cmd,
                payload = chunk,
            )
            frames += frame
            offset = end
            index += 1
        }
        return frames
    }

    private fun buildFlags(isFirst: Boolean, isLast: Boolean, expectAck: Boolean): Int {
        var flags = 0
        if (isFirst) flags = flags or BitKeyFlags.FLAG_START
        if (!isFirst && !isLast) flags = flags or BitKeyFlags.FLAG_CONT
        if (isLast) flags = flags or BitKeyFlags.FLAG_END
        if (expectAck && (isFirst || isLast)) flags = flags or BitKeyFlags.FLAG_ACK
        return flags
    }
}