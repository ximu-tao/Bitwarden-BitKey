package com.bitwarden.bitkey.protocol

/**
 * The result of feeding bytes into a [BitKeyFrameParser].
 *
 * Modeled as a sealed hierarchy so callers can exhaustively handle each case without
 * string matching.
 */
sealed class BitKeyParseResult {

    /** The parser needs more bytes before a complete frame can be emitted. */
    data object NeedMore : BitKeyParseResult()

    /** A complete, valid frame has been decoded. */
    data class Ok(val frame: BitKeyFrame) : BitKeyParseResult()

    /**
     * The parser determined the bytes cannot form a valid frame. The decoder is reset
     * after returning this result; the caller may continue feeding bytes.
     */
    data class Malformed(val reason: Reason) : BitKeyParseResult() {

        /**
         * Specific failure categories. Values mirror the wire-protocol error codes where
         * a direct correspondence exists; [Resynced] covers garbage bytes that were
         * skipped while searching for the next MAGIC.
         */
        enum class Reason {
            /** First received byte was not MAGIC. */
            BadMagic,

            /** Version byte did not match [BitKeyConstants.VERSION]. */
            BadVersion,

            /** Declared payload length exceeded [BitKeyConstants.MAX_PAYLOAD]. */
            BadLength,

            /** Trailing CRC byte did not match the CRC computed over the rest of the frame. */
            BadCrc,

            /** Bytes were discarded while waiting for a new MAGIC. */
            Resynced,
        }
    }
}
