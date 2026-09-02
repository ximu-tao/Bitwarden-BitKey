package com.bitwarden.bitkey.protocol

/**
 * High-level builders for the commands defined in [BitKeyCommands].
 *
 * These helpers translate the typed values used at the call site into the wire-format
 * payload bytes consumed by [BitKeyFrameEncoder]. They never perform I/O themselves;
 * the caller is responsible for handing the resulting frames to
 * [com.bitwarden.bitkey.connection.BitKeyConnectionManager.send].
 *
 * Every builder returns frames whose `FLAGS` byte includes [BitKeyFlags.FLAG_ACK] so the
 * caller can simply forward `expectAck = true` to the connection manager and rely on
 * the device's confirmation.
 */
object BitKeyProtocol {

    /**
     * Builds the `PING` request frame.
     *
     * @param seq sequence number for the frame.
     */
    fun ping(seq: Int): BitKeyFrame = BitKeyFrame(
        seq = seq,
        flags = BitKeyFlags.FLAG_ACK,
        cmd = BitKeyCommands.CMD_PING,
        payload = ByteArray(0),
    )

    /**
     * Builds the `SESSION_START` request frame.
     *
     * @param seq sequence number for the frame.
     */
    fun sessionStart(seq: Int): BitKeyFrame = BitKeyFrame(
        seq = seq,
        flags = BitKeyFlags.FLAG_ACK,
        cmd = BitKeyCommands.CMD_SESSION_START,
        payload = ByteArray(0),
    )

    /**
     * Builds the `SESSION_END` request frame.
     *
     * @param seq sequence number for the frame.
     */
    fun sessionEnd(seq: Int): BitKeyFrame = BitKeyFrame(
        seq = seq,
        flags = BitKeyFlags.FLAG_ACK,
        cmd = BitKeyCommands.CMD_SESSION_END,
        payload = ByteArray(0),
    )

    /**
     * Builds the `TYPE_TEXT` request frame for a UTF-8 string.
     *
     * The payload is the raw UTF-8 representation of [text]. The firmware currently only
     * accepts ASCII characters in the range `0x20..0x7E`; callers that need stricter
     * pre-validation should use [isAsciiPrintable].
     *
     * @param seq sequence number for the frame.
     * @param text text to type on the host machine.
     */
    fun typeText(seq: Int, text: String): BitKeyFrame = BitKeyFrame(
        seq = seq,
        flags = BitKeyFlags.FLAG_ACK,
        cmd = BitKeyCommands.CMD_TYPE_TEXT,
        payload = text.toByteArray(Charsets.UTF_8),
    )

    /**
     * Builds the `TYPE_KEY` request frame.
     *
     * @param seq sequence number for the frame.
     * @param keycode protocol-layer keycode (see `BitKey/docs/PROTOCOL.md` §7).
     * @param modifiers OR-combination of the modifier bits defined by the protocol.
     */
    fun typeKey(seq: Int, keycode: Int, modifiers: Int): BitKeyFrame {
        val payload = ByteArray(2)
        payload[0] = keycode.toByte()
        payload[1] = modifiers.toByte()
        return BitKeyFrame(
            seq = seq,
            flags = BitKeyFlags.FLAG_ACK,
            cmd = BitKeyCommands.CMD_TYPE_KEY,
            payload = payload,
        )
    }

    /**
     * Builds the `TYPE_COMBO` request frame.
     *
     * @param seq sequence number for the frame.
     * @param modifiers OR-combination of the modifier bits defined by the protocol.
     * @param keycodes protocol-layer keycodes to press simultaneously. Up to 16 may
     * be supplied; the firmware caps the supported number at 4 so callers should
     * pre-trim if needed.
     */
    fun typeCombo(seq: Int, modifiers: Int, keycodes: List<Int>): BitKeyFrame {
        require(keycodes.isNotEmpty()) { "typeCombo requires at least one keycode" }
        require(keycodes.size <= 16) { "typeCombo supports at most 16 keycodes" }
        val payload = ByteArray(2 + keycodes.size)
        payload[0] = modifiers.toByte()
        payload[1] = keycodes.size.toByte()
        for ((index, keycode) in keycodes.withIndex()) {
            payload[2 + index] = keycode.toByte()
        }
        return BitKeyFrame(
            seq = seq,
            flags = BitKeyFlags.FLAG_ACK,
            cmd = BitKeyCommands.CMD_TYPE_COMBO,
            payload = payload,
        )
    }

    /**
     * Builds the `GET_STATUS` request frame.
     *
     * @param seq sequence number for the frame.
     */
    fun getStatus(seq: Int): BitKeyFrame = BitKeyFrame(
        seq = seq,
        flags = BitKeyFlags.FLAG_ACK,
        cmd = BitKeyCommands.CMD_GET_STATUS,
        payload = ByteArray(0),
    )

    /**
     * Builds the `PAIR_REQUEST` frame.
     *
     * @param seq sequence number for the frame.
     */
    fun pairRequest(seq: Int): BitKeyFrame = BitKeyFrame(
        seq = seq,
        flags = BitKeyFlags.FLAG_ACK,
        cmd = BitKeyCommands.CMD_PAIR_REQUEST,
        payload = ByteArray(0),
    )

    /**
     * Returns `true` when every byte of [text] can be expressed using the printable
     * ASCII subset the firmware currently accepts (`0x20..0x7E`). Newlines (`0x0A`) and
     * tabs (`0x09`) are explicitly rejected by the firmware and treated as unsupported.
     */
    fun isAsciiPrintable(text: String): Boolean {
        if (text.isEmpty()) return false
        for (ch in text) {
            val b = ch.code
            if (b < 0x20 || b > 0x7E) return false
        }
        return true
    }
}