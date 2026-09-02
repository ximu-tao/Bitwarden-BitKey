package com.bitwarden.bitkey.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.bitwarden.bitkey.model.BitKeyAck
import com.bitwarden.bitkey.model.BitKeyConnectionState
import com.bitwarden.bitkey.model.BitKeyDiscoveredDevice
import com.bitwarden.bitkey.protocol.BitKeyConstants
import com.bitwarden.bitkey.protocol.BitKeyError
import com.bitwarden.bitkey.protocol.BitKeyFrame
import com.bitwarden.bitkey.protocol.BitKeyFrameEncoder
import com.bitwarden.bitkey.protocol.BitKeyFrameParser
import com.bitwarden.bitkey.protocol.BitKeyParseResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Default [BitKeyConnectionManager] implementation backed by the platform
 * `android.bluetooth` stack.
 *
 * Responsibilities covered here:
 *  - LE scanning (filtered by [BitKeyConstants.SERVICE_UUID]).
 *  - GATT connection, MTU negotiation, and service discovery.
 *  - Bonding with reactive observation of the bond state.
 *  - TX (notify) subscription and RX (write) chunking.
 *  - ACK correlation by sequence number with timeouts.
 *  - Disconnection cleanup with cancellation of outstanding waiters.
 *  - [BitKeyConnectionState] stream with no raw payload logging.
 *
 * Threading: every public entry point is safe to call from any coroutine. Public
 * calls are funnelled through [mutex]; the underlying [BluetoothGattCallback] is
 * driven by the Bluetooth subsystem and updates state via small completables.
 */
@SuppressLint("MissingPermission")
@Singleton
class BluetoothGattBitKeyConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : BitKeyConnectionManager {

    //region State holders

    private val ioDispatcher = Dispatchers.IO
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex: Mutex = Mutex()

    private val _connectionState: MutableStateFlow<BitKeyConnectionState> =
        MutableStateFlow(BitKeyConnectionState.Idle)

    override val connectionState: StateFlow<BitKeyConnectionState>
        get() = _connectionState.asStateFlow()

    private val _incomingFrames: MutableSharedFlow<BitKeyFrame> =
        MutableSharedFlow(extraBufferCapacity = 64)

    override val incomingFrames: Flow<BitKeyFrame>
        get() = _incomingFrames.asSharedFlow()

    private val parser: BitKeyFrameParser = BitKeyFrameParser()
    private val scanCallbackHolder: ScanCallbackHolder = ScanCallbackHolder()

    // One dedicated CompletableDeferred per GATT event. Type-safe access avoids the
    // pitfalls of a shared CompletableDeferred<*> registry.
    private val bondWaiter: SingleShotWaiter<Int> = SingleShotWaiter()
    private val mtuWaiter: SingleShotWaiter<Pair<Int, Int>> = SingleShotWaiter()
    private val servicesWaiter: SingleShotWaiter<Int> = SingleShotWaiter()
    private val writeWaiter: SingleShotWaiter<Int> = SingleShotWaiter()
    private val subscribeWaiter: SingleShotWaiter<Int> = SingleShotWaiter()
    private val ackWaiters: MutableMap<Int, CompletableDeferred<BitKeyFrame>> = HashMap()

    private val disconnectCause: Throwable =
        IllegalStateException("BitKey link disconnected")

    @Volatile private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var released: Boolean = false

    //endregion

    //region Scan

    override fun scan(): Flow<BitKeyDiscoveredDevice> = callbackFlow {
        val adapter = requireAdapter()
        val scanner = requireScanner(adapter)
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val startedAt = currentTimeMillis()
        val callback = scanCallbackHolder.acquire { address, rssi, name ->
            trySend(
                BitKeyDiscoveredDevice(
                    address = address,
                    rssi = rssi,
                    advertisedName = name,
                    firstSeenMillis = startedAt,
                ),
            )
        }
        try {
            scanner.startScan(null, settings, callback)
        } catch (t: Throwable) {
            close(t)
            return@callbackFlow
        }
        awaitClose {
            runCatching { scanner.stopScan(callback) }
            scanCallbackHolder.release(callback)
        }
    }.flowOn(ioDispatcher)

    override fun stopScan() {
        val adapter = bluetoothAdapterOrNull() ?: return
        val scanner = runCatching { adapter.bluetoothLeScanner }.getOrNull() ?: return
        scanCallbackHolder.forEachActive { callback ->
            runCatching { scanner.stopScan(callback) }
        }
    }

    //endregion

    //region Connect / disconnect

    override suspend fun connect(deviceAddress: String): Result<Unit> = resultOf {
        checkReleased()
        mutex.withLock {
            if (_connectionState.value is BitKeyConnectionState.Connected) {
                error("Already connected; call disconnect() first.")
            }
            val adapter = requireAdapter()
            val remote = adapter.getRemoteDevice(deviceAddress)
            _connectionState.value = BitKeyConnectionState.Connecting(
                deviceAddress = deviceAddress,
                stage = BitKeyConnectionState.Connecting.Stage.Bonding,
            )
            gatt = remote.connectGatt(
                /* context = */ context,
                /* autoConnect = */ false,
                /* callback = */ gattCallback,
            )
        }
    }

    override suspend fun awaitConnected(
        timeoutMillis: Long,
    ): Result<BitKeyConnectionState.Connected> = resultOf {
        checkReleased()
        withTimeoutOrNull(timeoutMillis) {
            connectionState.first { it is BitKeyConnectionState.Connected }
                as BitKeyConnectionState.Connected
        } ?: error("Connection did not reach Connected within ${timeoutMillis}ms")
    }

    override suspend fun disconnect(): Result<Unit> = resultOf {
        mutex.withLock {
            when (_connectionState.value) {
                is BitKeyConnectionState.Connected,
                is BitKeyConnectionState.Connecting,
                is BitKeyConnectionState.Scanning,
                -> {
                    gatt?.let { runCatching { it.disconnect() } }
                }
                is BitKeyConnectionState.Idle,
                is BitKeyConnectionState.Disconnected,
                -> Unit
            }
        }
        withTimeoutOrNull(BitKeyConstants.DEFAULT_ACK_TIMEOUT_MS) {
            connectionState.first { it is BitKeyConnectionState.Disconnected }
        }
        cleanupAfterDisconnect(BitKeyConnectionState.Disconnected.Reason.UserRequested)
    }

    //endregion

    //region Send

    override suspend fun send(frame: BitKeyFrame, expectAck: Boolean): Result<BitKeyAck> =
        resultOf {
            checkReleased()
            if (expectAck && !frame.requestsAck) {
                error("expectAck=true but the frame did not set FLAG_ACK")
            }
            val activeGatt = gatt ?: error("Not connected; call connect() first.")
            val rx = rxCharacteristic ?: error("RX characteristic is not yet available.")
            val ackDeferred = if (expectAck) {
                val deferred = CompletableDeferred<BitKeyFrame>()
                synchronized(ackWaiters) { ackWaiters[frame.seq] = deferred }
                deferred
            } else {
                null
            }
            try {
                writeInChunks(activeGatt, rx, BitKeyFrameEncoder.encode(frame))
                if (ackDeferred != null) {
                    val ackFrame = withTimeout(BitKeyConstants.DEFAULT_ACK_TIMEOUT_MS) {
                        ackDeferred.await()
                    }
                    decodeAck(ackFrame)
                } else {
                    BitKeyAck(
                        seq = frame.seq,
                        error = BitKeyError.None,
                        extra = ByteArray(0),
                    )
                }
            } finally {
                if (ackDeferred != null) {
                    synchronized(ackWaiters) { ackWaiters.remove(frame.seq) }
                }
            }
        }

    //endregion

    //region Release

    override fun release() {
        if (released) return
        released = true
        scope.launch(ioDispatcher) {
            mutex.withLock {
                gatt?.let { runCatching { it.disconnect() } }
            }
            cleanupAfterDisconnect(BitKeyConnectionState.Disconnected.Reason.UserRequested)
            scope.coroutineContext[Job]?.cancel()
        }
    }

    //endregion

    //region Callback plumbing

    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Timber.tag(TAG).d("onConnectionStateChange status=%d newState=%d", status, newState)
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> handleConnected(g)
                BluetoothProfile.STATE_DISCONNECTED -> handleDisconnected(status)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Timber.tag(TAG).d("onServicesDiscovered status=%d", status)
            servicesWaiter.complete(status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                scope.launch(ioDispatcher) { completeHandshake(g) }
            } else {
                transitionToDisconnected(
                    BitKeyConnectionState.Disconnected.Reason.ServiceDiscoveryFailed,
                )
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Timber.tag(TAG).d("onMtuChanged mtu=%d status=%d", mtu, status)
            mtuWaiter.complete(status to mtu)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val bytes = characteristic.value ?: return
            // Note: payload bytes are intentionally excluded from log messages.
            Timber.tag(TAG).v("notification bytes=%d", bytes.size)
            val results = parser.feed(bytes)
            results.forEach { result ->
                when (result) {
                    is BitKeyParseResult.Ok -> handleParsedFrame(result.frame)
                    is BitKeyParseResult.Malformed ->
                        Timber.tag(TAG).w("malformed frame reason=%s", result.reason)
                    is BitKeyParseResult.NeedMore -> Unit
                }
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Timber.tag(TAG).d("onCharacteristicWrite status=%d", status)
            writeWaiter.complete(status)
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            Timber.tag(TAG).d("onDescriptorWrite status=%d", status)
            subscribeWaiter.complete(status)
        }
    }

    private suspend fun completeHandshake(g: BluetoothGatt) {
        transitioningTo(g.device.address, BitKeyConnectionState.Connecting.Stage.NegotiatingMtu)
        val (status, _) = awaitMtu(g)
        check(status == BluetoothGatt.GATT_SUCCESS) {
            "MTU negotiation failed with status=$status"
        }
        bondedOrSkip(g.device)
        transitioningTo(g.device.address, BitKeyConnectionState.Connecting.Stage.DiscoveringServices)
        val servicesStatus = awaitServicesDiscovery(g)
        check(servicesStatus == BluetoothGatt.GATT_SUCCESS) {
            "Service discovery failed with status=$servicesStatus"
        }
        val services = g.services.orEmpty()
        val service = services.firstOrNull { it.uuid == BitKeyConstants.SERVICE_UUID }
            ?: error("BitKey service not found in discovery result")
        val rx = service.characteristics
            .firstOrNull { it.uuid == BitKeyConstants.RX_CHARACTERISTIC_UUID }
            ?: error("RX characteristic missing")
        val tx = service.characteristics
            .firstOrNull { it.uuid == BitKeyConstants.TX_CHARACTERISTIC_UUID }
            ?: error("TX characteristic missing")
        mutex.withLock {
            rxCharacteristic = rx
            txCharacteristic = tx
            _connectionState.value = BitKeyConnectionState.Connecting(
                deviceAddress = g.device.address,
                stage = BitKeyConnectionState.Connecting.Stage.Subscribing,
            )
        }
        awaitSubscribe(g, tx)
        mutex.withLock {
            _connectionState.value = BitKeyConnectionState.Connected(
                deviceAddress = g.device.address,
                mtu = mtuEffectiveSize(),
            )
        }
    }

    private suspend fun transitioningTo(
        deviceAddress: String,
        stage: BitKeyConnectionState.Connecting.Stage,
    ) {
        mutex.withLock {
            _connectionState.value = BitKeyConnectionState.Connecting(
                deviceAddress = deviceAddress,
                stage = stage,
            )
        }
    }

    private fun handleConnected(g: BluetoothGatt) {
        scope.launch(ioDispatcher) {
            runCatching { completeHandshake(g) }
                .onFailure { Timber.tag(TAG).w(it, "Handshake failed") }
        }
    }

    private fun handleDisconnected(status: Int) {
        val reason = if (status == BluetoothGatt.GATT_SUCCESS) {
            BitKeyConnectionState.Disconnected.Reason.UserRequested
        } else {
            BitKeyConnectionState.Disconnected.Reason.LinkLost
        }
        transitionToDisconnected(reason)
    }

    private fun handleParsedFrame(frame: BitKeyFrame) {
        // Surface the frame first so consumers can audit ACK responses in order.
        _incomingFrames.tryEmit(frame)
        val waiter = synchronized(ackWaiters) { ackWaiters.remove(frame.seq) }
        waiter?.complete(frame)
    }

    //endregion

    //region Awaiters

    private suspend fun awaitMtu(g: BluetoothGatt): Pair<Int, Int> {
        val deferred = mtuWaiter.deferred()
        check(g.requestMtu(BitKeyConstants.PREFERRED_MTU)) {
            "requestMtu returned false"
        }
        return withTimeout(MTU_TIMEOUT_MS) { deferred.await() }
    }

    private suspend fun awaitServicesDiscovery(g: BluetoothGatt): Int {
        val deferred = servicesWaiter.deferred()
        check(g.discoverServices()) { "discoverServices returned false" }
        return withTimeout(SERVICES_TIMEOUT_MS) { deferred.await() }
    }

    private suspend fun awaitSubscribe(g: BluetoothGatt, tx: BluetoothGattCharacteristic) {
        val deferred = subscribeWaiter.deferred()
        check(g.setCharacteristicNotification(tx, true)) {
            "setCharacteristicNotification returned false"
        }
        val descriptor = tx.getDescriptor(CCCD_UUID)
            ?: error("CCCD descriptor missing on TX characteristic")
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        check(g.writeDescriptor(descriptor)) { "writeDescriptor returned false" }
        withTimeout(SUBSCRIBE_TIMEOUT_MS) { deferred.await() }
    }

    private suspend fun bondedOrSkip(device: BluetoothDevice) {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return
        val deferred = bondWaiter.deferred()
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                val action = intent?.action ?: return
                if (action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                val rawDevice: BluetoothDevice? =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                if (rawDevice == null || rawDevice.address != device.address) return
                val newState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                Timber.tag(TAG).d("bond broadcast newState=%d", newState)
                deferred.complete(newState)
            }
        }
        val filter = android.content.IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        val flag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.content.Context.RECEIVER_EXPORTED
        } else {
            0
        }
        try {
            if (flag != 0) {
                context.registerReceiver(receiver, filter, flag)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "Failed to register bond-state receiver")
            return
        }
        try {
            check(device.createBond()) { "createBond returned false" }
            val state = withTimeoutOrNull(BOND_TIMEOUT_MS) { deferred.await() } ?: run {
                Timber.tag(TAG).w("Bonding did not complete; continuing best-effort")
                return
            }
            check(state == BluetoothDevice.BOND_BONDED) {
                "Bonding ended in non-BONDED state=$state"
            }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    //endregion

    //region Write path

    private suspend fun writeInChunks(
        g: BluetoothGatt,
        rx: BluetoothGattCharacteristic,
        encoded: ByteArray,
    ) {
        val mtu = (connectionState.value as? BitKeyConnectionState.Connected)?.mtu
            ?: BitKeyConstants.PREFERRED_MTU
        val chunkSize = (mtu - ATT_HEADER_OVERHEAD).coerceAtLeast(MIN_CHUNK_BYTES)
        var offset = 0
        while (offset < encoded.size) {
            val end = (offset + chunkSize).coerceAtMost(encoded.size)
            val chunk = encoded.copyOfRange(offset, end)
            rx.value = chunk
            val deferred = writeWaiter.deferred()
            check(g.writeCharacteristic(rx)) { "writeCharacteristic returned false" }
            withTimeout(WRITE_TIMEOUT_MS) { deferred.await() }
            offset = end
        }
    }

    private fun decodeAck(frame: BitKeyFrame): BitKeyAck {
        val errorCode = frame.ackErrorCode() ?: BitKeyError.None.code
        val extra = if (frame.payload.size > 1) {
            frame.payload.copyOfRange(1, frame.payload.size)
        } else {
            ByteArray(0)
        }
        return BitKeyAck(
            seq = frame.seq,
            error = BitKeyError.fromCode(errorCode),
            extra = extra,
        )
    }

    private fun mtuEffectiveSize(): Int =
        (connectionState.value as? BitKeyConnectionState.Connected)?.mtu
            ?: BitKeyConstants.PREFERRED_MTU

    //endregion

    //region Cleanup

    private fun transitionToDisconnected(reason: BitKeyConnectionState.Disconnected.Reason) {
        scope.launch(ioDispatcher) { cleanupAfterDisconnect(reason) }
    }

    private suspend fun cleanupAfterDisconnect(reason: BitKeyConnectionState.Disconnected.Reason) {
        mutex.withLock {
            val g = gatt
            if (g != null) {
                runCatching { g.close() }
            }
            gatt = null
            rxCharacteristic = null
            txCharacteristic = null
            _connectionState.value = BitKeyConnectionState.Disconnected(reason)
        }
        cancelAllWaiters()
    }

    private fun cancelAllWaiters() {
        bondWaiter.cancelWith(disconnectCause)
        mtuWaiter.cancelWith(disconnectCause)
        servicesWaiter.cancelWith(disconnectCause)
        writeWaiter.cancelWith(disconnectCause)
        subscribeWaiter.cancelWith(disconnectCause)
        synchronized(ackWaiters) {
            ackWaiters.values.forEach { deferred ->
                deferred.completeExceptionally(disconnectCause)
                deferred.cancel(
                    kotlinx.coroutines.CancellationException(
                        disconnectCause.message ?: "BitKey link disconnected",
                    ),
                )
            }
            ackWaiters.clear()
        }
    }

    //endregion

    //region Helpers

    private fun bluetoothAdapterOrNull(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    private fun requireAdapter(): BluetoothAdapter {
        val adapter = bluetoothAdapterOrNull() ?: error("BluetoothManager not available")
        check(adapter.isEnabled) { "Bluetooth adapter is disabled" }
        return adapter
    }

    private fun requireScanner(adapter: BluetoothAdapter) =
        adapter.bluetoothLeScanner
            ?: error("BluetoothLeScanner unavailable; check BLUETOOTH_SCAN permission")

    private fun currentTimeMillis(): Long = System.currentTimeMillis()

    private fun checkReleased() {
        check(!released) { "BitKeyConnectionManager has been released" }
    }

    //endregion

    /**
     * Wraps a single [CompletableDeferred] that can only be awaited and completed
     * once per "round". After each successful operation, [SingleShotWaiter.deferred]
     * returns a fresh deferred; a previously-completed one is cancelled.
     */
    private class SingleShotWaiter<T> {
        @Volatile private var current: CompletableDeferred<T>? = null

        suspend fun deferred(): CompletableDeferred<T> {
            val fresh = CompletableDeferred<T>()
            current = fresh
            return fresh
        }

        fun complete(value: T) {
            current?.complete(value)
            current = null
        }

        fun cancelWith(cause: Throwable) {
            current?.cancel(
                kotlinx.coroutines.CancellationException(
                    cause.message ?: cause::class.java.simpleName,
                ),
            )
            current = null
        }
    }

    /**
     * ScanCallback has its lifecycle bound to the flow. This holder lets [scan] register
     * callbacks and lets [stopScan] iterate over them when there is no active collector.
     */
    private class ScanCallbackHolder {
        private val active: MutableSet<ScanCallback> = mutableSetOf()

        @Synchronized
        fun acquire(handler: (String, Int, String?) -> Unit): ScanCallback {
            val callback = object : ScanCallback() {
                override fun onScanResult(resultType: Int, result: ScanResult) {
                    val record = result.scanRecord ?: return
                    handler(result.device.address, result.rssi, record.deviceName)
                }
            }
            active.add(callback)
            return callback
        }

        @Synchronized
        fun release(callback: ScanCallback) {
            active.remove(callback)
        }

        @Synchronized
        fun forEachActive(block: (ScanCallback) -> Unit) {
            active.toList().forEach(block)
        }
    }

    private companion object {
        private const val TAG: String = "BitKeyConnection"
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val MIN_CHUNK_BYTES: Int = 20
        private const val ATT_HEADER_OVERHEAD: Int = 3
        private const val BOND_TIMEOUT_MS: Long = 8_000L
        private const val MTU_TIMEOUT_MS: Long = 4_000L
        private const val SERVICES_TIMEOUT_MS: Long = 4_000L
        private const val SUBSCRIBE_TIMEOUT_MS: Long = 4_000L
        private const val WRITE_TIMEOUT_MS: Long = 4_000L
    }
}

//region Coroutine glue

/**
 * Bridges a suspending block to a [Result] while preserving structured cancellation.
 * `CancellationException` is rethrown unchanged so structured cancellation still
 * propagates; every other [Throwable] becomes `Result.failure`.
 */
private suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: kotlinx.coroutines.CancellationException) {
    throw cancellation
} catch (t: Throwable) {
    Result.failure(t)
}

//endregion
