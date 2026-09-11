package com.x8bit.bitwarden.wear.ui.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bitwarden.vault.CipherType
import com.bitwarden.vault.LoginUriView
import com.x8bit.bitwarden.wear.ui.bitkey.PendingBitKeySend

/**
 * Detail screen of a single cipher item.
 *
 * Shows the fields relevant on a watch: username, password (tap to reveal),
 * TOTP code when available, URIs and notes. All data is provided by
 * [ItemDetailViewModel] from the shared `:appdata` decryption streams.
 */
@Composable
fun ItemDetailScreen(
    cipherId: String,
    onBack: () -> Unit,
    onSendToBitKey: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState

    LaunchedEffect(cipherId) {
        viewModel.start(cipherId)
    }

    val cipherView = uiState.cipherView
    if (cipherView == null) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    text = "加载中…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                DetailButton(
                    text = "返回",
                    onClick = onBack,
                )
            }
        }
        return
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = cipherView.name,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
        item { Divider() }

        when (cipherView.type) {
            CipherType.LOGIN -> {
                cipherView.login?.username?.takeIf { it.isNotBlank() }?.let { username ->
                    item { DetailRow(label = "用户名", value = username) }
                }
                cipherView.login?.password?.takeIf { it.isNotBlank() }?.let { password ->
                    item {
                        DetailRow(
                            label = "密码",
                            value = if (uiState.isPasswordVisible) password else "••••••••",
                        )
                    }
                    item {
                        DetailButton(
                            text = if (uiState.isPasswordVisible) "隐藏密码" else "显示密码",
                            onClick = viewModel::togglePasswordVisibility,
                        )
                    }
                    item {
                        DetailButton(
                            text = "发送密码到 BitKey",
                            onClick = {
                                PendingBitKeySend.text = password
                                onSendToBitKey()
                            },
                        )
                    }
                }
                uiState.authCode?.let { authCode ->
                    item {
                        DetailRow(
                            label = "两步验证码",
                            value = authCode.code,
                            valueStyle = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
                cipherView.login?.uris.orEmpty().forEach { uri ->
                    item { DetailRow(label = "地址", value = uri.displayUriOrEmpty) }
                }
            }

            CipherType.CARD -> {
                cipherView.card?.cardholderName?.takeIf { it.isNotBlank() }?.let {
                    item { DetailRow(label = "持卡人", value = it) }
                }
                cipherView.card?.number?.takeIf { it.isNotBlank() }?.let {
                    item { DetailRow(label = "卡号", value = it) }
                }
                cipherView.card?.brand?.takeIf { it.isNotBlank() }?.let {
                    item { DetailRow(label = "品牌", value = it) }
                }
            }

            CipherType.IDENTITY -> {
                cipherView.identity?.let { identity ->
                    val identityName = listOfNotNull(
                        identity.firstName,
                        identity.lastName,
                    ).joinToString(" ").ifBlank { null }
                    identityName?.let { item { DetailRow(label = "姓名", value = it) } }
                    identity.username?.takeIf { it.isNotBlank() }?.let {
                        item { DetailRow(label = "用户名", value = it) }
                    }
                    identity.email?.takeIf { it.isNotBlank() }?.let {
                        item { DetailRow(label = "邮箱", value = it) }
                    }
                }
            }

            // Other cipher types (SSH key, bank account, licenses, passports)
            // are browsable but have no dedicated watch-optimized layout yet.
            else -> Unit
        }

        cipherView.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            item { DetailRow(label = "备注", value = notes) }
        }

        item {
            DetailButton(
                text = "返回",
                onClick = onBack,
            )
        }
    }
}

/**
 * A label/value row of the detail screen.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Text(
            text = value,
            style = valueStyle,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/**
 * Full-width action button of the detail screen.
 */
@Composable
private fun DetailButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(text = text)
    }
}

/**
 * Thin divider between detail sections.
 */
@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * Resolves a [LoginUriView] to its displayable URI.
 */
private val LoginUriView.displayUriOrEmpty: String
    get() = uri?.takeIf { it.isNotBlank() } ?: ""