package com.bitwarden.bitkey.protocol

/**
 * Streaming decoder that consumes incoming bytes and emits parsed [BitKeyFrame]s.
 *
 * The parser is a faithful port of the firmware's `FrameDecoder`
 * (`BitKey/src/protocol/bitkey_protocol.cpp`):
 *  - It silently discards bytes received before a [BitKeyConstants.MAGIC] byte, so a
 *    garbled stream re-syncs on its own.
 *  - It validates [BitKeyConstants.VERSION] and the declared payload length before
 *    accumulating payload bytes.
 *  - It validates the trailing CRC8 against every preceding byte of the frame.
 *  - It never throws; malformed inputs surface as [BitKeyParseResult.Malformed] so
 *    callers can decide how to react.
 *
 * Each call to [feed] consumes as many bytes from the provided range as it can and
 * returns every event produced in that pass as a list. The list contains zero or
 * more [BitKeyParseResult.Ok] / [BitKeyParseResult.Malformed] results followed by
 * exactly one terminator (normally [BitKeyParseResult.NeedMore]) that indicates
 * whether the parser is still waiting for additional bytes.
 *
 * The parser is NOT thread-safe; use one parser per receive stream.
 */
class BitKeyFrameParser {

    private enum class Phase { Header, Payload, Crc }

    private val buffer = ByteArray(BitKeyConstants.MAX_FRAME_SIZE)
    private var phase: Phase = Phase.Header
    private var needed: Int = BitKeyConstants.HEADER_SIZE
    private var position: Int = 0
    private var payloadLength: Int = 0

    //region Diagnostics

    /**
     * Count of bytes that have been discarded so far while re-syncing to a new MAGIC.
     * Useful for logging keep-alive traffic quality without exposing each lost byte.
     */
    var discardedBytes: Int = 0
        private set

    //endregion

    /**
     * Resets the parser back to the header phase with no bytes buffered.
     */
    fun reset() {
        phase = Phase.Header
        needed = BitKeyConstants.HEADER_SIZE
        position = 0
        payloadLength = 0
    }

    /**
     * Convenience overload that feeds the entire [data] array.
     */
    fun feed(data: ByteArray): List<BitKeyParseResult> = feed(data, offset = 0, length = data.size)

    /**
     * Feeds the bytes in `data[offset until offset + length]` into the parser and
     * returns every event produced during this pass.
     *
     * Returned list semantics:
     *  - If any complete frame or malformed event was produced, those entries appear in
     *    the list in the order they completed.
     *  - The list ends with [BitKeyParseResult.NeedMore] whenever more bytes are
     *    required (or when [length] is `0` and the parser was already waiting).
     *  - If the input runs out exactly between events, the list ends with
     *    [BitKeyParseResult.NeedMore].
     */
    fun feed(
        data: ByteArray,
        offset: Int,
        length: Int,
    ): List<BitKeyParseResult> {
        require(offset >= 0) { "offset must be non-negative" }
        require(length >= 0) { "length must be non-negative" }
        require(offset + length <= data.size) { "offset + length exceeds data.size" }

        val results = mutableListOf<BitKeyParseResult>()
        var cursor = offset
        var remaining = length
        while (remaining > 0) {
            if (phase == Phase.Header && position == 0 && data[cursor] != MAGIC_BYTE) {
                cursor++
                remaining--
                discardedBytes++
                continue
            }
            val take = if (remaining < needed) remaining else needed
            data.copyInto(
                destination = buffer,
                destinationOffset = position,
                startIndex = cursor,
                endIndex = cursor + take,
            )
            position += take
            cursor += take
            remaining -= take
            needed -= take
            if (needed > 0) {
                results += BitKeyParseResult.NeedMore
                return results
            }
            when (phase) {
                Phase.Header -> {
                    if (buffer[0] != MAGIC_BYTE) {
                        reset()
                        results += BitKeyParseResult.Malformed(
                            BitKeyParseResult.Malformed.Reason.BadMagic,
                        )
                        continue
                    }
                    if (buffer[1] != VERSION_BYTE) {
                        reset()
                        results += BitKeyParseResult.Malformed(
                            BitKeyParseResult.Malformed.Reason.BadVersion,
                        )
                        continue
                    }
                    payloadLength = ((buffer[5].toInt() and 0xFF) shl 8) or
                        (buffer[6].toInt() and 0xFF)
                    if (payloadLength > BitKeyConstants.MAX_PAYLOAD) {
                        reset()
                        results += BitKeyParseResult.Malformed(
                            BitKeyParseResult.Malformed.Reason.BadLength,
                        )
                        continue
                    }
                    phase = if (payloadLength == 0) Phase.Crc else Phase.Payload
                    needed = if (payloadLength == 0) BitKeyConstants.CRC_SIZE else payloadLength
                }
                Phase.Payload -> {
                    phase = Phase.Crc
                    needed = BitKeyConstants.CRC_SIZE
                }
                Phase.Crc -> {
                    val expected = buffer[position - 1].toInt() and 0xFF
                    val actual = Crc8.compute(buffer, offset = 0, length = position - 1)
                    if (expected != actual) {
                        reset()
                        results += BitKeyParseResult.Malformed(
                            BitKeyParseResult.Malformed.Reason.BadCrc,
                        )
                        continue
                    }
                    val payloadCopy = if (payloadLength == 0) {
                        EMPTY_PAYLOAD
                    } else {
                        buffer.copyOfRange(
                            BitKeyConstants.HEADER_SIZE,
                            BitKeyConstants.HEADER_SIZE + payloadLength,
                        )
                    }
                    val frame = BitKeyFrame(
                        seq = buffer[2].toInt() and 0xFF,
                        flags = buffer[3].toInt() and 0xFF,
                        cmd = buffer[4].toInt() and 0xFF,
                        payload = payloadCopy,
                    )
                    reset()
                    results += BitKeyParseResult.Ok(frame)
                }
            }
        }
        // Reached the end of input without finishing a frame.
        results += BitKeyParseResult.NeedMore
        return results
    }

    private companion object {
        private const val MAGIC_BYTE: Byte = BitKeyConstants.MAGIC.toByte()
        private const val VERSION_BYTE: Byte = BitKeyConstants.VERSION.toByte()
        private val EMPTY_PAYLOAD: ByteArray = ByteArray(0)
    }
}
