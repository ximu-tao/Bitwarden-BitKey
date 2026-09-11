package com.x8bit.bitwarden.wear.ui.bitkey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitwarden.bitkey.connection.BitKeyConnectionManager
import com.bitwarden.bitkey.model.BitKeyDiscoveredDevice
import com.bitwarden.bitkey.send.BitKeySendResult
import com.bitwarden.bitkey.send.BitKeySendService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * In-memory stash for the text the user wants to send to a BitKey device.
 *
 * The detail screen stores the field value here before navigating to the
 * device picker, mirroring the phone app's pending-field pattern. Values are
 * cleared once consumed; the object never persists across processes.
 */
object PendingBitKeySend {
    var text: String? = null
}

/**
 * View model for the Wear OS BitKey device picker.
 *
 * Scans for nearby BitKey peripherals and, once a device is chosen, sends the
 * pending text via [BitKeySendService]. The whole connect → session → type →
 * ack flow lives in `:bitkey` and is reused verbatim.
 */
@HiltViewModel
class BitKeyPickerViewModel @Inject constructor(
    private val connectionManager: BitKeyConnectionManager,
    private val sendService: BitKeySendService,
) : ViewModel() {

    /**
     * The current picker state.
     */
    var uiState by mutableStateOf(BitKeyPickerUiState())
        private set

    /**
     * The UI state of the device picker.
     */
    data class BitKeyPickerUiState(
        val isScanning: Boolean = false,
        val devices: List<BitKeyDiscoveredDevice> = emptyList(),
        val sendingAddress: String? = null,
        val sendResultMessage: String? = null,
        val errorMessage: String? = null,
    )

    init {
        scan()
    }

    /**
     * Starts scanning for nearby BitKey peripherals.
     */
    fun scan() {
        if (uiState.isScanning) return
        uiState = uiState.copy(isScanning = true, errorMessage = null)
        viewModelScope.launch {
            val devices = mutableListOf<BitKeyDiscoveredDevice>()
            try {
                connectionManager.scan().collect { device ->
                    devices.removeAll { it.address == device.address }
                    devices.add(device)
                    uiState = uiState.copy(devices = devices.toList())
                }
            } catch (_: SecurityException) {
                uiState = uiState.copy(
                    errorMessage = BLUETOOTH_PERMISSION_DENIED_MESSAGE,
                )
            } finally {
                uiState = uiState.copy(isScanning = false)
            }
        }
        viewModelScope.launch {
            delay(SCAN_TIMEOUT_MILLIS)
            connectionManager.stopScan()
        }
    }

    /**
     * Sends the pending text to the device at [deviceAddress].
     */
    fun sendTo(deviceAddress: String) {
        val text = PendingBitKeySend.text
        if (text == null) {
            uiState = uiState.copy(errorMessage = "没有待发送的内容")
            return
        }
        if (uiState.sendingAddress != null) return
        viewModelScope.launch {
            uiState = uiState.copy(
                sendingAddress = deviceAddress,
                sendResultMessage = null,
                errorMessage = null,
            )
            val result = sendService.sendPassword(
                deviceAddress = deviceAddress,
                password = text,
            )
            PendingBitKeySend.text = null
            uiState = uiState.copy(
                sendingAddress = null,
                sendResultMessage = result.toUserMessage(),
            )
        }
    }

    private fun BitKeySendResult.toUserMessage(): String = when (this) {
        BitKeySendResult.Success -> "已发送到设备"
        BitKeySendResult.UnsupportedCharacters -> "包含设备无法输入的字符"
        BitKeySendResult.EmptyText -> "没有可发送的内容"
        is BitKeySendResult.ConnectionFailed -> "连接失败，请确认设备已开机且在附近"
        is BitKeySendResult.DeviceError -> "设备拒绝了请求（错误 ${error.name}）"
        BitKeySendResult.TimedOut -> "发送超时，请重试"
    }

    private companion object {
        const val SCAN_TIMEOUT_MILLIS: Long = 20_000
        const val BLUETOOTH_PERMISSION_DENIED_MESSAGE: String =
            "没有蓝牙权限，请在系统设置中授予"
    }
}