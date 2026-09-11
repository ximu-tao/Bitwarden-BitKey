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
import com.x8bit.bitwarden.wear.ui.auth.LoginViewModel

/**
 * Login screen for the Wear OS app.
 *
 * Collects the email address and master password of the user and authenticates
 * against the Bitwarden cloud by reusing [LoginViewModel] (backed by
 * `AuthRepository` from `:appdata`). When the server requires two-factor
 * verification the screen switches to an inline code entry step.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onOpenEnvironment: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.loginSuccessEvent.collect { onLoginSuccess() }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = "登录 Bitwarden",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        if (uiState.requiresTwoFactor) {
            item {
                Text(
                    text = "两步验证",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.twoFactorCode,
                    onValueChange = viewModel::onTwoFactorCodeChange,
                    label = { Text("验证码") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            item {
                SubmitButton(
                    text = "验证",
                    isLoading = uiState.isLoading,
                    enabled = uiState.twoFactorCode.isNotBlank(),
                    onClick = viewModel::confirmTwoFactor,
                )
            }
        } else {
            item {
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text("邮箱") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
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
                SubmitButton(
                    text = "登录",
                    isLoading = uiState.isLoading,
                    enabled = uiState.email.isNotBlank() && uiState.password.isNotBlank(),
                    onClick = viewModel::login,
                )
            }
        }
        item {
            TextButton(
                onClick = onOpenEnvironment,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "服务器：${uiState.environmentLabel}",
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

/**
 * Full-width login submit button; shows a spinner while a request is in flight.
 */
@Composable
private fun SubmitButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        } else {
            Text(text = text)
        }
    }
}