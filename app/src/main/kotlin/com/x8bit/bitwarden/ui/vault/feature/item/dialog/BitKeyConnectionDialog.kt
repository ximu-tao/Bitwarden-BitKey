package com.x8bit.bitwarden.ui.vault.feature.item.dialog

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.x8bit.bitwarden.ui.platform.manager.permissions.PermissionsManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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
 * Runtime permissions required to discover BitKey BLE peripherals.
 *
 * - API 31+ (Android 12+): `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`.
 * - API ≤30: legacy `ACCESS_FINE_LOCATION` (required for raw BLE scans).
 */
private val bitkeyRequiredPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

/**
 * Visible states of the runtime permission flow rendered inside the dialog.
 *
 * - [Granted]: all required permissions are currently granted, scanning proceeds.
 * - [NeedsPrompt]: permission missing but the user has not yet hard-denied; show a rationale
 *   and offer to launch the system permission prompt.
 * - [PermanentlyDenied]: the user has denied with "don't ask again"; show a "Open settings"
 *   affordance instead of re-launching the prompt.
 */
private sealed class BitKeyPermissionUiState {
    data object Granted : BitKeyPermissionUiState()
    data object NeedsPrompt : BitKeyPermissionUiState()
    data object PermanentlyDenied : BitKeyPermissionUiState()
}

/**
 * A self-contained dialog that lets the user pick a nearby BitKey peripheral and start a
 * send.
 *
 * The dialog owns the [BitKeyConnectionManager] scan lifecycle; when it appears it triggers
 * `scan()` and stops the scan on dismissal. Selecting a device invokes [onDeviceSelected]
 * with the device's MAC address — that callback is the integration point with
 * [com.x8bit.bitwarden.ui.vault.feature.item.VaultItemViewModel].
 *
 * Before scanning, the dialog drives a runtime permission state machine via
 * [permissionsManager]: if the required BLE permissions are missing it shows an in-dialog
 * rationale + grant button. If the user previously selected "don't ask again" the dialog
 * surfaces [onOpenAppSettings] instead so they can grant access from system settings.
 */
@Suppress("LongMethod")
@Composable
fun BitKeyConnectionDialog(
    connectionManager: BitKeyConnectionManager,
    permissionsManager: PermissionsManager,
    onDeviceSelected: (deviceAddress: String) -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismissRequest: () -> Unit,
    initialState: BitKeyDialogState = BitKeyDialogState(),
) {
    var state by remember { mutableStateOf(initialState) }
    var hasRequested by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val permissionUiState = remember(permissionsManager, hasRequested) {
        computePermissionUiState(permissionsManager, hasRequested)
    }

    val permissionLauncher = permissionsManager.getPermissionsLauncher { _ ->
        // Trigger recomputation so a successful grant moves us back to the scan view.
        hasRequested = true
    }

    DisposableEffect(connectionManager, permissionUiState) {
        if (permissionUiState != BitKeyPermissionUiState.Granted) {
            connectionManager.stopScan()
            return@DisposableEffect onDispose { connectionManager.stopScan() }
        }
        val job = connectionManager
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
            .launchIn(scope)
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
                .background(
                    color = BitwardenTheme.colorScheme.background.primary,
                    shape = BitwardenTheme.shapes.dialog,
                )
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

            when (permissionUiState) {
                BitKeyPermissionUiState.Granted -> {
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
                }

                BitKeyPermissionUiState.NeedsPrompt -> PermissionRationale(
                    message = stringResource(
                        id = BitwardenString.bitkey_send_permission_rationale,
                    ),
                    actionLabel = stringResource(
                        id = BitwardenString.bitkey_send_permission_grant,
                    ),
                    onAction = {
                        permissionLauncher.launch(bitkeyRequiredPermissions)
                    },
                )

                BitKeyPermissionUiState.PermanentlyDenied -> PermissionRationale(
                    message = stringResource(
                        id = BitwardenString.bitkey_send_permission_denied_explanation,
                    ),
                    actionLabel = stringResource(
                        id = BitwardenString.bitkey_send_permission_open_settings,
                    ),
                    onAction = onOpenAppSettings,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isScanning &&
                    permissionUiState == BitKeyPermissionUiState.Granted
                ) {
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

/**
 * Reduces the current runtime permission status into a UI state.
 *
 * @return one of [BitKeyPermissionUiState.Granted], [BitKeyPermissionUiState.NeedsPrompt]
 * or [BitKeyPermissionUiState.PermanentlyDenied], in priority order.
 */
private fun computePermissionUiState(
    permissionsManager: PermissionsManager,
    hasRequested: Boolean,
): BitKeyPermissionUiState {
    if (permissionsManager.checkPermissions(bitkeyRequiredPermissions)) {
        return BitKeyPermissionUiState.Granted
    }
    val anyShouldShowRationale = bitkeyRequiredPermissions.any {
        permissionsManager.shouldShowRequestPermissionRationale(it)
    }
    return when {
        anyShouldShowRationale -> BitKeyPermissionUiState.NeedsPrompt
        hasRequested -> BitKeyPermissionUiState.PermanentlyDenied
        else -> BitKeyPermissionUiState.NeedsPrompt
    }
}

@Composable
private fun ColumnScope.PermissionRationale(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .weight(1f, fill = false),
    ) {
        Text(
            text = message,
            color = BitwardenTheme.colorScheme.text.secondary,
            style = BitwardenTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("BitKeyDialogPermissionAction"),
        ) {
            Text(text = actionLabel)
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