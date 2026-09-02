package com.x8bit.bitwarden.ui.vault.feature.item.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ripple.ripple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bitwarden.bitkey.connection.BitKeyConnectionManager
import com.bitwarden.bitkey.model.BitKeyDiscoveredDevice
import com.bitwarden.ui.platform.components.dialog.util.maxDialogHeight
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Hilt [EntryPoint] used to retrieve the singleton [BitKeyConnectionManager] from outside a
 * standard DI injection site (e.g. a Compose composable).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BitKeyConnectionManagerEntryPoint {
    fun bitKeyConnectionManager(): BitKeyConnectionManager
}

/**
 * The UI state displayed in the [BitKeyConnectionDialog].
 *
 * @property devices The list of devices discovered so far.
 * @property isScanning Whether the manager is still actively scanning.
 * @property errorMessage An optional, user-facing error string to display at the top of the
 * dialog (e.g. Bluetooth is off, scan failed). When set, the scan is paused.
 */
data class BitKeyDialogState(
    val devices: List<BitKeyDiscoveredDevice> = emptyList(),
    val isScanning: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * A self-contained dialog that lets the user pick a nearby BitKey peripheral and start a
 * send.
 *
 * The dialog owns the [BitKeyConnectionManager] scan lifecycle; when it appears it triggers
 * `scan()` and stops the scan on dismissal. Selecting a device invokes [onDeviceSelected]
 * with the device's MAC address — that callback is the integration point with
 * [com.x8bit.bitwarden.ui.vault.feature.item.VaultItemViewModel].
 */
@Suppress("LongMethod")
@Composable
fun BitKeyConnectionDialog(
    connectionManager: BitKeyConnectionManager,
    onDeviceSelected: (deviceAddress: String) -> Unit,
    onDismissRequest: () -> Unit,
    initialState: BitKeyDialogState = BitKeyDialogState(),
) {
    var state by remember { mutableStateOf(initialState) }
    val scope = rememberCoroutineScope()

    DisposableEffect(connectionManager) {
        val job = scope.launch {
            connectionManager
                .scan()
                .catch { throwable ->
                    state = state.copy(
                        isScanning = false,
                        errorMessage = throwable.message,
                    )
                }
                .onEach { device ->
                    val existing = state.devices
                    val merged = if (existing.any { it.address == device.address }) {
                        existing.map { if (it.address == device.address) device else it }
                    } else {
                        existing + device
                    }
                    state = state.copy(devices = merged)
                }
                .collect()
        }
        onDispose {
            job.cancel()
            connectionManager.stopScan()
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        val configuration = LocalConfiguration.current
        Column(
            modifier = Modifier
                .testTag("BitKeyConnectionDialog")
                .requiredHeightIn(max = configuration.maxDialogHeight)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                modifier = Modifier
                    .testTag("BitKeyDialogTitle")
                    .padding(24.dp)
                    .fillMaxWidth(),
                text = stringResource(id = BitwardenString.bitkey_send),
                color = BitwardenTheme.colorScheme.text.primary,
                style = BitwardenTheme.typography.headlineSmall,
            )
            state.errorMessage?.let { error ->
                Text(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                    text = error,
                    color = BitwardenTheme.colorScheme.status.error,
                    style = BitwardenTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
            }
            DeviceList(
                state = state,
                onDeviceClick = { onDeviceSelected(it) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .testTag("BitKeyDialogScanIndicator")
                            .size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(12.dp))
                }
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.testTag("BitKeyDialogCancel"),
                ) {
                    Text(text = stringResource(id = BitwardenString.cancel))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DeviceList(
    state: BitKeyDialogState,
    onDeviceClick: (String) -> Unit,
) {
    if (state.devices.isEmpty()) {
        Text(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .fillMaxWidth(),
            text = stringResource(id = BitwardenString.bitkey_send_connecting),
            color = BitwardenTheme.colorScheme.text.secondary,
            style = BitwardenTheme.typography.bodyMedium,
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
        ) {
            items(items = state.devices, key = { it.address }) { device ->
                BitKeyDeviceRow(
                    device = device,
                    onClick = { onDeviceClick(device.address) },
                )
            }
        }
    }
}

@Composable
private fun BitKeyDeviceRow(
    device: BitKeyDiscoveredDevice,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("BitKeyDeviceRow")
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = BitwardenTheme.colorScheme.background.pressed),
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.advertisedName ?: device.address,
                color = BitwardenTheme.colorScheme.text.primary,
                style = BitwardenTheme.typography.bodyLarge,
                modifier = Modifier.testTag("BitKeyDeviceName"),
            )
            Text(
                text = device.address,
                color = BitwardenTheme.colorScheme.text.secondary,
                style = BitwardenTheme.typography.bodySmall,
                modifier = Modifier.testTag("BitKeyDeviceAddress"),
            )
        }
        Text(
            text = "${device.rssi} dBm",
            color = BitwardenTheme.colorScheme.text.secondary,
            style = BitwardenTheme.typography.bodySmall,
        )
    }
}