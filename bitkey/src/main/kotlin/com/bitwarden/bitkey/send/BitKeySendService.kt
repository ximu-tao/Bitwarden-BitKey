package com.bitwarden.bitkey.send

import com.bitwarden.bitkey.connection.BitKeyConnectionManager
import com.bitwarden.bitkey.protocol.BitKeyError
import com.bitwarden.bitkey.protocol.BitKeyFragmenter
import com.bitwarden.bitkey.protocol.BitKeyProtocol
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of attempting to type a password on a BitKey device.
 */
sealed class BitKeySendResult {

    /** The device acknowledged the final frame and reported no error. */
    data object Success : BitKeySendResult()

    /** The supplied text contained characters the firmware cannot type. */
    data object UnsupportedCharacters : BitKeySendResult()

    /** The supplied text was empty; nothing was sent. */
    data object EmptyText : BitKeySendResult()

    /** The connection could not be established (off adapter, permission denied, MTU failure). */
    data class ConnectionFailed(val cause: Throwable) : BitKeySendResult()

    /** The session_start or type_text frame was rejected by the device. */
    data class DeviceError(val error: BitKeyError) : BitKeySendResult()

    /** A timeout or cancellation interrupted the send before the device acknowledged. */
    data object TimedOut : BitKeySendResult()
}

/**
 * Configuration for the [BitKeySendService]. All fields are optional and default to
 * conservative values that match the protocol spec.
 */
data class BitKeySendConfig(
    /** Sequence number used for the SESSION_START frame. */
    val sessionStartSeq: Int = 0x10,
    /** Sequence number used for the SESSION_END frame. */
    val sessionEndSeq: Int = 0x11,
)

/**
 * High-level orchestration of "type this string on the BitKey device".
 *
 * The service wraps a [BitKeyConnectionManager] and performs the steps required to
 * send a typed password:
 *  1. Open a session with `SESSION_START`.
 *  2. Stream the text in `TYPE_TEXT` frames, fragmenting across MTU boundaries if
 *     necessary using [BitKeyFragmenter].
 *  3. Close the session with `SESSION_END`.
 *
 * The service never exposes passwords or fragments in any error paths; the failure
 * modes are returned as [BitKeySendResult] variants.
 */
@Singleton
class BitKeySendService @Inject constructor(
    private val connectionManager: BitKeyConnectionManager,
) {

    /**
     * Sends [password] to the device at [deviceAddress]. The function returns once the
     * device has acknowledged the final `SESSION_END` frame or after the first failure.
     */
    suspend fun sendPassword(
        deviceAddress: String,
        password: String,
        config: BitKeySendConfig = BitKeySendConfig(),
    ): BitKeySendResult {
        if (password.isEmpty()) return BitKeySendResult.EmptyText
        if (!BitKeyProtocol.isAsciiPrintable(password)) {
            return BitKeySendResult.UnsupportedCharacters
        }

        val connectResult = connectionManager.connect(deviceAddress)
        if (connectResult.isFailure) {
            return BitKeySendResult.ConnectionFailed(
                connectResult.exceptionOrNull()
                    ?: IllegalStateException("Unknown connect failure"),
            )
        }
        val awaitResult = connectionManager.awaitConnected(timeoutMillis = 10_000L)
        if (awaitResult.isFailure) {
            connectionManager.disconnect()
            return BitKeySendResult.ConnectionFailed(
                awaitResult.exceptionOrNull()
                    ?: IllegalStateException("Unknown handshake failure"),
            )
        }

        return try {
            performSend(password, config)
        } finally {
            connectionManager.disconnect()
        }
    }

    private suspend fun performSend(
        password: String,
        config: BitKeySendConfig,
    ): BitKeySendResult {
        val startResult = connectionManager.send(
            BitKeyProtocol.sessionStart(config.sessionStartSeq),
            expectAck = true,
        )
        if (startResult.isFailure) {
            return mapSendFailure(startResult)
        }
        val startAck = startResult.getOrNull() ?: return BitKeySendResult.TimedOut
        if (startAck.error != BitKeyError.None) {
            return BitKeySendResult.DeviceError(startAck.error)
        }

        val payload = password.toByteArray(Charsets.UTF_8)
        val frames = BitKeyFragmenter.fragment(
            cmd = com.bitwarden.bitkey.protocol.BitKeyCommands.CMD_TYPE_TEXT,
            payload = payload,
            startSeq = (config.sessionStartSeq + 1) and 0xFF,
            expectAck = true,
        )
        for (frame in frames) {
            val result = connectionManager.send(frame, expectAck = true)
            if (result.isFailure) {
                return mapSendFailure(result)
            }
            val ack = result.getOrNull() ?: return BitKeySendResult.TimedOut
            if (ack.error != BitKeyError.None) {
                return BitKeySendResult.DeviceError(ack.error)
            }
        }

        val endResult = connectionManager.send(
            BitKeyProtocol.sessionEnd(config.sessionEndSeq),
            expectAck = true,
        )
        if (endResult.isFailure) {
            return mapSendFailure(endResult)
        }
        val endAck = endResult.getOrNull() ?: return BitKeySendResult.TimedOut
        return if (endAck.error == BitKeyError.None) {
            BitKeySendResult.Success
        } else {
            BitKeySendResult.DeviceError(endAck.error)
        }
    }

    private fun mapSendFailure(
        result: Result<*>,
    ): BitKeySendResult {
        val cause = result.exceptionOrNull()
        return when {
            cause is kotlinx.coroutines.TimeoutCancellationException ->
                BitKeySendResult.TimedOut

            cause is kotlinx.coroutines.CancellationException ->
                BitKeySendResult.TimedOut

            cause is IllegalStateException &&
                cause.message?.contains("disconnected", ignoreCase = true) == true ->
                BitKeySendResult.TimedOut

            else -> BitKeySendResult.ConnectionFailed(
                cause ?: IllegalStateException("Unknown send failure"),
            )
        }
    }
}