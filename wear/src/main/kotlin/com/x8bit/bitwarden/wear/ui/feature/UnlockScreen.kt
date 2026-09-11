package com.x8bit.bitwarden.wear.ui.feature

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import com.x8bit.bitwarden.wear.ui.auth.UnlockViewModel

/**
 * Unlock screen for the Wear OS app.
 *
 * Most wearables have no fingerprint hardware, so unlocking mirrors the phone
 * app's fallback path: master password (or PIN where the account has one).
 * The actual unlock is handled by [UnlockViewModel], which reuses
 * `VaultRepository.unlockVaultWithMasterPassword` from `:appdata`.
 */
@Composable
fun UnlockScreen(
    onUnlockSuccess: () -> Unit,
    onLogout: () -> Unit,
    viewModel: UnlockViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.unlockSuccessEvent.collect { onUnlockSuccess() }
    }
    LaunchedEffect(Unit) {
        viewModel.logoutSuccessEvent.collect { onLogout() }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = "解锁保险库",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        if (uiState.accountEmail.isNotBlank()) {
            item {
                Text(
                    text = uiState.accountEmail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        item {
            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("主密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        item {
            Button(
                onClick = viewModel::unlock,
                enabled = uiState.password.isNotBlank() && !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Text(text = "解锁")
                }
            }
        }
        item {
            TextButton(
                onClick = viewModel::logout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "退出登录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
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