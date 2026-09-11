package com.x8bit.bitwarden.wear.ui.feature

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.x8bit.bitwarden.wear.ui.auth.EnvironmentViewModel

/**
 * Server environment screen for the Wear OS app.
 *
 * Lets the user pick the official Bitwarden cloud (US/EU) or point the app at
 * a self-hosted Bitwarden server by URL. Selections are persisted through the
 * shared `EnvironmentRepository` from `:appdata`, which the network layer
 * consults on every request — the change takes effect for the next login or
 * vault sync without an app restart.
 */
@Composable
fun EnvironmentScreen(
    onDone: () -> Unit,
    viewModel: EnvironmentViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState
    val selectedPreset = uiState.selectedPreset

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = "服务器环境",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        item {
            Text(
                text = "选择登录和同步使用的 Bitwarden 服务器",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        listOf(
            EnvironmentViewModel.ServerPreset.BITWARDEN_COM,
            EnvironmentViewModel.ServerPreset.BITWARDEN_EU,
            EnvironmentViewModel.ServerPreset.SELF_HOSTED,
        ).forEach { preset ->
            item {
                Button(
                    onClick = { viewModel.onPresetSelect(preset) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (preset == selectedPreset) {
                            "✓ ${preset.label}"
                        } else {
                            preset.label
                        },
                    )
                }
            }
        }
        if (selectedPreset == EnvironmentViewModel.ServerPreset.SELF_HOSTED) {
            item {
                OutlinedTextField(
                    value = uiState.customUrl,
                    onValueChange = viewModel::onCustomUrlChange,
                    label = { Text("服务器地址") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            item {
                Text(
                    text = "例如 vault.example.com（自托管 Bitwarden 服务器域名）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        item {
            Button(
                onClick = { viewModel.save(onDone) },
                enabled = selectedPreset != EnvironmentViewModel.ServerPreset.SELF_HOSTED ||
                    uiState.customUrl.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(text = "保存并返回")
            }
        }
        uiState.errorMessage?.let { errorMessage ->
            item {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}