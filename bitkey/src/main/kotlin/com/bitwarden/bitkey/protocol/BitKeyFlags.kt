package com.bitwarden.bitkey.protocol

/**
 * Bits carried in the `FLAGS` byte of a BitKey frame.
 *
 * Multiple flags may be combined into a single byte. The numeric values mirror the
 * firmware's `kFlagXxx` constants in `BitKey/src/config.h`.
 *
 * Examples:
 * ```
 * val tx = FLAG_ACK or FLAG_START
 * val isError = receivedFlags and FLAG_ERROR != 0
 * ```
 */
object BitKeyFlags {

    /** First frame of a multi-packet payload (reserved, current firmware is single-packet). */
    const val FLAG_START: Int = 0x01

    /** Continuation frame of a multi-packet payload (reserved). */
    const val FLAG_CONT: Int = 0x02

    /** Final frame of a multi-packet payload (reserved). */
    const val FLAG_END: Int = 0x04

    /** The sender expects the peer to respond with an ACK frame. */
    const val FLAG_ACK: Int = 0x08

    /** Only valid on ACK frames; indicates an error response rather than success. */
    const val FLAG_ERROR: Int = 0x10

    /**
     * Returns `true` when all bits in [mask] are also present in [flags].
     */
    fun has(flags: Int, mask: Int): Boolean = (flags and mask) == mask
}
