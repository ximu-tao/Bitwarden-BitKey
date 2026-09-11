package com.x8bit.bitwarden.wear.ui.bitkey

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bitwarden.bitkey.model.BitKeyConnectionState
import com.bitwarden.bitkey.model.BitKeyDiscoveredDevice

/**
 * BitKey content of the home screen: connection status, scan trigger and the
 * discovered device list. Driven by [BitKeyViewModel].
 */
fun ScalingLazyListScope.BitKeyTabContent(
    uiState: BitKeyViewModel.BitKeyUiState,
    onScanClick: () -> Unit,
    onConnectClick: (String) -> Unit,
    onDisconnectClick: () -> Unit,
) {
    item {
        Text(
            text = connectionStatusText(uiState.connectionState),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    when (uiState.connectionState) {
        is BitKeyConnectionState.Connected -> {
            item {
                ActionButton(
                    text = "断开连接",
                    onClick = onDisconnectClick,
                )
            }
        }

        else -> {
            item {
                ActionButton(
                    text = if (uiState.isScanning) "扫描中…" else "扫描设备",
                    onClick = onScanClick,
                    enabled = !uiState.isScanning,
                )
            }
        }
    }
    if (uiState.devices.isEmpty()) {
        item {
            Text(
                text = if (uiState.isScanning) "正在查找附近的 BitKey…" else "未发现设备",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    } else {
        items(uiState.devices) { device ->
            DeviceRow(
                device = device,
                isConnected = (uiState.connectionState as? BitKeyConnectionState.Connected)
                    ?.deviceAddress == device.address,
                onClick = { onConnectClick(device.address) },
            )
        }
    }
    uiState.errorMessage?.let { errorMessage ->
        item {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * A single discovered BitKey device row.
 */
@Composable
private fun DeviceRow(
    device: BitKeyDiscoveredDevice,
    isConnected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = device.advertisedName?.takeIf { it.isNotBlank() } ?: "BitKey 设备",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Text(
            text = if (isConnected) {
                "已连接"
            } else {
                device.address
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
        )
    }
}

/**
 * Maps the connection state to user-facing text.
 */
private fun connectionStatusText(state: BitKeyConnectionState): String = when (state) {
    BitKeyConnectionState.Idle -> "未连接"
    is BitKeyConnectionState.Scanning -> "扫描中…"
    is BitKeyConnectionState.Connecting -> "正在连接…"
    is BitKeyConnectionState.Connected -> "已连接（MTU ${state.mtu}）"
    is BitKeyConnectionState.Disconnected -> "连接已断开"
}

/**
 * Full-width action button used by the BitKey surface.
 */
@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = text)
    }
}

/**
 * Full screen for picking a BitKey device to send the pending field to.
 */
@Composable
fun BitKeyPickerScreen(
    onDone: () -> Unit,
    viewModel: BitKeyPickerViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val uiState = viewModel.uiState

    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = "发送到 BitKey",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        item {
            Text(
                text = if (uiState.isScanning) "正在扫描…" else "选择一个设备发送",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (uiState.isScanning) {
            item {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        if (uiState.devices.isEmpty()) {
            item {
                Text(
                    text = if (uiState.isScanning) "" else "未发现设备，请确认 BitKey 已开机",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(uiState.devices) { device ->
                val isSending = uiState.sendingAddress == device.address
                Card(
                    onClick = { viewModel.sendTo(device.address) },
                    enabled = !isSending && uiState.sendingAddress == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = device.advertisedName?.takeIf { it.isNotBlank() }
                            ?: "BitKey 设备",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    Text(
                        text = if (isSending) "发送中…" else device.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                    )
                }
            }
        }
        uiState.sendResultMessage?.let { sendResult ->
            item {
                Text(
                    text = sendResult,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        uiState.errorMessage?.let { errorMessage ->
            item {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        item {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(text = "返回")
            }
        }
    }
}