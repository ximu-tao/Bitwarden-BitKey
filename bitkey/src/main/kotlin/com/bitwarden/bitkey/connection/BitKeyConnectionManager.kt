package com.bitwarden.bitkey.connection

import com.bitwarden.bitkey.model.BitKeyAck
import com.bitwarden.bitkey.model.BitKeyConnectionState
import com.bitwarden.bitkey.model.BitKeyDiscoveredDevice
import com.bitwarden.bitkey.protocol.BitKeyFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level facade around the BitKey BLE GATT transport.
 *
 * The interface is the single contract the rest of the app depends on. The default
 * production implementation lives alongside it (`BluetoothGattBitKeyConnectionManager`)
 * and uses the platform's Bluetooth stack directly. Tests and previews may substitute
 * a fake.
 *
 * All suspending functions return [Result] so callers do not have to wrap call sites in
 * `try`/`catch` — the data layer rule against throwing is enforced here.
 */
interface BitKeyConnectionManager {

    /**
     * The current connection lifecycle state. Always emits a value on collection.
     * Implementations guarantee that the latest value is retained so a late collector
     * sees the current state immediately.
     */
    val connectionState: StateFlow<BitKeyConnectionState>

    /**
     * Stream of frames received from the device while the manager is connected.
     *
     * Frames are emitted in the order they were received over the air. ACK frames
     * (`CMD_ACK`) are surfaced here AND propagated to any in-flight
     * [send] call waiting on a matching sequence number; consumers can ignore them if
     * they only care about unsolicited traffic.
     */
    val incomingFrames: Flow<BitKeyFrame>

    /**
     * Begins a scan for nearby BitKey peripherals. The returned [Flow] completes when
     * [stopScan] is called or the calling coroutine is cancelled. The manager does not
     * gate concurrent scans; calling this while a scan is already active will replace
     * the previous scan.
     */
    fun scan(): Flow<BitKeyDiscoveredDevice>

    /**
     * Stops an in-progress scan, if any. Safe to call when no scan is active.
     */
    fun stopScan()

    /**
     * Initiates a GATT connection to the peripheral with the given MAC [deviceAddress].
     *
     * The function only awaits the initial connect call. Subsequent state transitions
     * (bonding, MTU negotiation, service discovery, subscribe) stream via
     * [connectionState]. Use [awaitConnected] if you want to block until those stages
     * have finished.
     *
     * @return `Result.success(Unit)` when the connect call was accepted by the radio,
     * regardless of whether the link eventually comes up; `Result.failure` when the
     * connect call itself could not be issued.
     */
    suspend fun connect(deviceAddress: String): Result<Unit>

    /**
     * Suspends until [connectionState] is [BitKeyConnectionState.Connected] or an error
     * terminal state is reached. Returns the connected state when successful, or a
     * failure when the negotiation timed out or aborted.
     */
    suspend fun awaitConnected(timeoutMillis: Long): Result<BitKeyConnectionState.Connected>

    /**
     * Tears down the active link (if any) and releases the GATT callback. Subsequent
     * [send] calls return `Result.failure` until a new [connect] is issued.
     */
    suspend fun disconnect(): Result<Unit>

    /**
     * Sends [frame] to the device.
     *
     * When [expectAck] is `true`, the function also waits for an ACK frame whose
     * `seq` matches the frame's `seq` and returns the decoded [BitKeyAck] inside the
     * `Result`. When [expectAck] is `false`, the function returns once the bytes have
     * been handed to the radio and reports success/failure of that step.
     *
     * Payload bytes larger than the negotiated MTU are automatically split into
     * multiple writes — fragmentation is invisible to the caller.
     */
    suspend fun send(frame: BitKeyFrame, expectAck: Boolean = false): Result<BitKeyAck>

    /**
     * Releases any internal resources held by this manager. After this call, every
     * other method returns `Result.failure` with [IllegalStateException].
     */
    fun release()
}
