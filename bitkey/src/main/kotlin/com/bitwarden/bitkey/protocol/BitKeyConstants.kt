package com.bitwarden.bitkey.protocol

import java.util.UUID

/**
 * BitKey protocol constants shared between the encoder, decoder, and connection layer.
 *
 * The constants and identifiers defined here MUST mirror the firmware implementation under
 * `BitKey/src/config.h` and `BitKey/docs/PROTOCOL.md`. Any change to one side must be
 * mirrored on the other.
 *
 * All values here are part of the protocol surface and are intentionally public so that
 * higher layers (UI, tests) can refer to command and flag identifiers without
 * re-declaring them.
 */
object BitKeyConstants {

    //region Header / framing

    /** First byte of every frame; used by the parser to re-sync to a frame boundary. */
    const val MAGIC: Int = 0xA5

    /** Protocol version implemented by both the host and the firmware. */
    const val VERSION: Int = 0x01

    /** Number of header bytes preceding the payload: MAGIC, VERSION, SEQ, FLAGS, CMD, LEN_HI, LEN_LO. */
    const val HEADER_SIZE: Int = 7

    /** Number of trailing CRC bytes (always 1). */
    const val CRC_SIZE: Int = 1

    /** Maximum total frame length (header + payload + CRC). Mirrors `kMaxFrameSize`. */
    const val MAX_FRAME_SIZE: Int = 512

    /** Maximum single-frame payload length. Derived as `MAX_FRAME_SIZE - HEADER_SIZE - CRC_SIZE`. */
    const val MAX_PAYLOAD: Int = MAX_FRAME_SIZE - HEADER_SIZE - CRC_SIZE

    //endregion

    //region BLE identifiers

    /**
     * GATT service UUID advertised by the BitKey peripheral:
     * `0000BBBB-0000-1000-8000-00805F9B34FB`.
     */
    val SERVICE_UUID: UUID = UUID.fromString("0000BBBB-0000-1000-8000-00805F9B34FB")

    /** RX characteristic: host -> device (write). */
    val RX_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000BB01-0000-1000-8000-00805F9B34FB")

    /** TX characteristic: device -> host (notify). */
    val TX_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000BB02-0000-1000-8000-00805F9B34FB")

    //endregion

    //region Defaults

    /** Default per-write ACK timeout used by the connection manager. */
    const val DEFAULT_ACK_TIMEOUT_MS: Long = 500L

    /**
     * Preferred ATT MTU. The firmware is built around 512-byte frames, so the host should
     * attempt to negotiate as high as possible; 247 is the largest value accepted by all
     * Bluetooth 4.2+ controllers without DLE.
     */
    const val PREFERRED_MTU: Int = 247

    //endregion
}
