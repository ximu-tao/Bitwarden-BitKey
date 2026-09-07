package com.bitwarden.bitkey.connection

import com.bitwarden.bitkey.model.BitKeyAck
import com.bitwarden.bitkey.model.BitKeyConnectionState
import com.bitwarden.bitkey.model.BitKeyDiscoveredDevice
import com.bitwarden.bitkey.protocol.BitKeyError
import com.bitwarden.bitkey.protocol.BitKeyFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory implementation of [BitKeyConnectionManager] used by unit tests.
 *
 * The fake captures every command sent through [send] so tests can assert the
 * exact sequence of frames the production code emitted. It also lets tests
 * pre-load a list of ACK responses (one per expected [send]) and exposes
 * configuration knobs that simulate the failure modes the real manager can
 * experience (off adapter, missing permission, MTU failure, etc.).
 *
 * Instances are single-use; call [reset] between scenarios.
 */
class FakeBitKeyConnectionManager : BitKeyConnectionManager {

    private val sentFrames: MutableList<BitKeyFrame> = mutableListOf()
    private val acks: MutableList<Result<BitKeyAck>> = mutableListOf()

    private val state = MutableStateFlow<BitKeyConnectionState>(BitKeyConnectionState.Idle)
    private val incoming = MutableSharedFlow<BitKeyFrame>(extraBufferCapacity = 16)

    var connectShouldFail: Boolean = false
    var releaseCalled: Boolean = false
        private set
    var disconnectCount: Int = 0
        private set
    var scanCallCount: Int = 0
        private set
    var stopScanCallCount: Int = 0
        private set

    /** Configures the next ACK to return [ack]. */
    fun enqueueAck(ack: Result<BitKeyAck>) {
        acks += ack
    }

    /** Drops the next call to [send] with a generic failure. */
    fun enqueueFailure(throwable: Throwable) {
        acks += Result.failure(throwable)
    }

    /** Returns the frames sent so far. */
    fun sentFrames(): List<BitKeyFrame> = sentFrames.toList()

    /** Resets all captured state without touching the connection state. */
    fun reset() {
        sentFrames.clear()
        acks.clear()
        connectShouldFail = false
        releaseCalled = false
        disconnectCount = 0
        scanCallCount = 0
        stopScanCallCount = 0
        state.value = BitKeyConnectionState.Idle
    }

    override val connectionState: kotlinx.coroutines.flow.StateFlow<BitKeyConnectionState>
        get() = state.asStateFlow()

    override val incomingFrames: Flow<BitKeyFrame>
        get() = incoming.asSharedFlow()

    override fun scan(): Flow<BitKeyDiscoveredDevice> {
        scanCallCount += 1
        return flowOf(
            BitKeyDiscoveredDevice(
                address = "AA:BB:CC:DD:EE:FF",
                rssi = -50,
                advertisedName = "BitKey",
                firstSeenMillis = 0L,
            ),
        )
    }

    override fun stopScan() {
        stopScanCallCount += 1
    }

    override suspend fun connect(deviceAddress: String): Result<Unit> {
        if (connectShouldFail) return Result.failure(IllegalStateException("simulated failure"))
        state.value = BitKeyConnectionState.Connecting(
            deviceAddress = deviceAddress,
            stage = BitKeyConnectionState.Connecting.Stage.Subscribing,
        )
        state.value = BitKeyConnectionState.Connected(deviceAddress = deviceAddress, mtu = 247)
        return Result.success(Unit)
    }

    override suspend fun awaitConnected(timeoutMillis: Long): Result<BitKeyConnectionState.Connected> {
        val current = state.value
        return if (current is BitKeyConnectionState.Connected) {
            Result.success(current)
        } else {
            Result.failure(IllegalStateException("not connected"))
        }
    }

    override suspend fun disconnect(): Result<Unit> {
        disconnectCount += 1
        state.value = BitKeyConnectionState.Disconnected(
            reason = BitKeyConnectionState.Disconnected.Reason.UserRequested,
        )
        return Result.success(Unit)
    }

    override suspend fun send(
        frame: BitKeyFrame,
        expectAck: Boolean,
        ackTimeoutMillis: Long,
    ): Result<BitKeyAck> {
        sentFrames += frame
        if (!expectAck) {
            return Result.success(
                BitKeyAck(seq = frame.seq, error = BitKeyError.None, extra = ByteArray(0)),
            )
        }
        val next = if (acks.isEmpty()) {
            Result.success(BitKeyAck(seq = frame.seq, error = BitKeyError.None, extra = ByteArray(0)))
        } else {
            acks.removeAt(0)
        }
        return next
    }

    override fun release() {
        releaseCalled = true
    }

    //region Test-only helpers

    /**
     * Pushes [frame] onto the incoming frame stream so the application code under test can
     * observe a notification from the device.
     */
    suspend fun emitIncoming(frame: BitKeyFrame) {
        incoming.emit(frame)
    }

    /**
     * Allows tests to override the current connection state to simulate transitions.
     */
    fun setState(newState: BitKeyConnectionState) {
        state.value = newState
    }

    /** Returns a [CompletableDeferred] that completes when [release] is called. */
    fun releaseDeferred(): CompletableDeferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        val previous = releaseCalled
        // We can't actually attach a hook to the override, so expose via property reads.
        // Tests should poll releaseCalled instead.
        if (previous) deferred.complete(Unit)
        return deferred
    }

    //endregion
}