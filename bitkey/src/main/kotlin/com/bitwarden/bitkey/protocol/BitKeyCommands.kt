package com.bitwarden.bitkey.protocol

/**
 * BitKey command codes (`CMD` byte) carried by every frame.
 *
 * The numeric values mirror the firmware's `kCmdXxx` constants in `BitKey/src/config.h`.
 * Values that are not listed here can still be encountered on the wire; treat unknown
 * commands as opaque rather than throwing.
 */
object BitKeyCommands {

    //region Host → device commands

    /** Ping. Response payload: `[PROTOCOL_VERSION, VID_LO, PID_LO]`. */
    const val CMD_PING: Int = 0x01

    /** Request an active session; required before any `TYPE_*` command. */
    const val CMD_SESSION_START: Int = 0x02

    /** End the active session and release any held keys. */
    const val CMD_SESSION_END: Int = 0x03

    /** Type a UTF-8 / ASCII text payload. */
    const val CMD_TYPE_TEXT: Int = 0x10

    /** Press and release a single key. Payload: `[keycode, modifier]`. */
    const val CMD_TYPE_KEY: Int = 0x11

    /** Press and release a key combo. Payload: `[modifier, count, keycode0..keycodeN-1]`. */
    const val CMD_TYPE_COMBO: Int = 0x12

    /** Request the device's 8-byte status payload. */
    const val CMD_GET_STATUS: Int = 0x80

    /** Request BLE bonding (reserved for future Secure Connections flow). */
    const val CMD_PAIR_REQUEST: Int = 0xF0

    //endregion

    //region Device → host commands

    /** Acknowledgement. Payload: `[errcode, ...extra]`. */
    const val CMD_ACK: Int = 0x81

    //endregion
}

/**
 * Recognised BitKey error codes carried in the first byte of every ACK payload.
 *
 * The firmware uses additional, undocumented codes for internal conditions. When the
 * first byte does not match any of the values defined here the connection layer maps
 * the response to [BitKeyError.Unknown].
 */
enum class BitKeyError(val code: Int) {
    /** No error; the operation succeeded. */
    None(0x00),

    /** Catch-all for errors the firmware did not classify. */
    Unknown(0x01),

    /** MAGIC byte did not match. */
    BadMagic(0x02),

    /** Protocol version did not match. */
    BadVersion(0x03),

    /** Payload length was outside the allowed range. */
    BadLength(0x04),

    /** CRC8 check on the incoming frame failed. */
    BadCrc(0x05),

    /** Command code or its parameters were not accepted. */
    BadCmd(0x06),

    /** The requested operation is not allowed in the current session state. */
    BadSession(0x07),

    /** The device's outgoing buffer is full; retry later. */
    BufferFull(0x08);

    companion object {
        private val byCode: Map<Int, BitKeyError> = entries.associateBy { it.code }

        /**
         * Resolves a wire-level error code into a [BitKeyError] value. Returns
         * [Unknown] for codes that are not explicitly recognised.
         */
        fun fromCode(code: Int): BitKeyError = byCode[code] ?: Unknown
    }
}
