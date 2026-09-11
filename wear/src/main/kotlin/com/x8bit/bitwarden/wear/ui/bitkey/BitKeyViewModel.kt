package com.x8bit.bitwarden.wear.ui.bitkey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitwarden.bitkey.connection.BitKeyConnectionManager
import com.bitwarden.bitkey.model.BitKeyConnectionState
import com.bitwarden.bitkey.model.BitKeyDiscoveredDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View model for the Wear OS BitKey control surface.
 *
 * Reuses the complete BLE stack from `:bitkey` ([BitKeyConnectionManager])
 * without any modification, exactly as the phone app does.
 */
@HiltViewModel
class BitKeyViewModel @Inject constructor(
    private val connectionManager: BitKeyConnectionManager,
) : ViewModel() {

    /**
     * The current BitKey control state.
     */
    var uiState by mutableStateOf(BitKeyUiState())
        private set

    /**
     * The UI state of the BitKey surface.
     */
    data class BitKeyUiState(
        val isScanning: Boolean = false,
        val devices: List<BitKeyDiscoveredDevice> = emptyList(),
        val connectionState: BitKeyConnectionState = BitKeyConnectionState.Idle,
        val errorMessage: String? = null,
    )

    init {
        viewModelScope.launch {
            connectionManager.connectionState.collect { state ->
                uiState = uiState.copy(connectionState = state)
            }
        }
    }

    /**
     * Starts scanning for nearby BitKey peripherals; stops automatically after
     * [SCAN_TIMEOUT_MILLIS].
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
     * Connects to the device at [deviceAddress].
     */
    fun connect(deviceAddress: String) {
        viewModelScope.launch {
            uiState = uiState.copy(errorMessage = null)
            val result = connectionManager.connect(deviceAddress)
            if (result.isFailure) {
                uiState = uiState.copy(
                    errorMessage = "连接失败：${result.exceptionOrNull()?.message.orEmpty()}",
                )
            }
        }
    }

    /**
     * Disconnects the active BitKey link.
     */
    fun disconnect() {
        viewModelScope.launch {
            connectionManager.disconnect()
        }
    }

    private companion object {
        const val SCAN_TIMEOUT_MILLIS: Long = 20_000
        const val BLUETOOTH_PERMISSION_DENIED_MESSAGE: String =
            "没有蓝牙权限，请在系统设置中授予"
    }
}