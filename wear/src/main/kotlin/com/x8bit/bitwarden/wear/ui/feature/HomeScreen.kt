package com.x8bit.bitwarden.wear.ui.feature

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.x8bit.bitwarden.data.vault.manager.model.VerificationCodeItem
import com.x8bit.bitwarden.wear.BuildConfig
import com.x8bit.bitwarden.wear.ui.bitkey.BitKeyTabContent
import com.x8bit.bitwarden.wear.ui.bitkey.BitKeyViewModel
import com.x8bit.bitwarden.wear.ui.home.HomeViewModel
import com.x8bit.bitwarden.wear.ui.home.HomeViewModel.VaultFilter
import kotlinx.coroutines.delay

/**
 * Home screen of the Wear OS app.
 *
 * Hosts the four main sections as tabs, mirroring the phone app's vault /
 * TOTP / settings / BitKey surface. Vault and TOTP tabs render real data from
 * `:appdata` via [HomeViewModel]; the BitKey tab is wired up in stage 4.
 */
@Composable
fun HomeScreen(
    onCipherClick: (cipherId: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val uiState = viewModel.uiState
    val bitKeyViewModel: BitKeyViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        viewModel.cipherClickEvent.collect { cipherId -> onCipherClick(cipherId) }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = "Bitwarden",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
        item {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(text = "Vault") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(text = "TOTP") },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(text = "设置") },
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text(text = "BitKey") },
                )
            }
        }
        when (selectedTab) {
            0 -> VaultTabContent(
                groupedCiphers = viewModel.groupedCiphers,
                selectedFilter = uiState.selectedFilter,
                isLoading = uiState.isLoading,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onFilterSelect = viewModel::onFilterSelect,
                onCipherClick = viewModel::onCipherClick,
            )

            1 -> TotpTabContent(authCodes = uiState.authCodes)

            2 -> SettingsTabContent(
                accountEmail = uiState.accountEmail,
                onSyncClick = viewModel::syncVault,
            )

            3 -> BitKeyTabContent(
                uiState = bitKeyViewModel.uiState,
                onScanClick = bitKeyViewModel::scan,
                onConnectClick = bitKeyViewModel::connect,
                onDisconnectClick = bitKeyViewModel::disconnect,
            )
        }
    }
}

/**
 * Vault browsing tab: search field, type filter chips + folder-grouped list.
 */
private fun ScalingLazyListScope.VaultTabContent(
    groupedCiphers: List<HomeViewModel.CipherGroup>,
    selectedFilter: VaultFilter,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelect: (VaultFilter) -> Unit,
    onCipherClick: (String) -> Unit,
) {
    item {
        SearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
        )
    }
    item {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            items(VaultFilter.entries.size) { index ->
                val filter = VaultFilter.entries[index]
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelect(filter) },
                    label = { Text(text = filter.label) },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
    if (groupedCiphers.all { it.ciphers.isEmpty() }) {
        item {
            SectionPlaceholder(
                title = "暂无条目",
                hint = if (isLoading) "同步中…" else "点击设置页的立即同步从云端获取保险库",
            )
        }
    } else {
        groupedCiphers.forEach { group ->
            group.folderName?.let { folderName ->
                item {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
            }
            items(group.ciphers) { cipher ->
                CipherListItem(
                    name = cipher.name.orEmpty(),
                    subtitle = cipher.subtitle?.takeIf { it.isNotBlank() }
                        ?: cipherTypeName(cipher.type),
                    onClick = { onCipherClick(cipher.id.orEmpty()) },
                )
            }
        }
    }
}

/**
 * Watch-sized search field for the vault list. Uses the foundational
 * [BasicTextField] because Wear Material3 has no text input component yet;
 * the card keeps the input tappable on round screens.
 */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = "搜索…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
    }
}

/**
 * A single cipher row in the vault list.
 */
@Composable
private fun CipherListItem(
    name: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
            )
        }
    }
}

/**
 * The human-readable name of the SDK cipher list view type.
 */
private fun cipherTypeName(type: com.bitwarden.vault.CipherListViewType): String =
    when (type) {
        is com.bitwarden.vault.CipherListViewType.Login -> "登录"
        is com.bitwarden.vault.CipherListViewType.Card -> "卡片"
        is com.bitwarden.vault.CipherListViewType.Identity -> "身份"
        is com.bitwarden.vault.CipherListViewType.SecureNote -> "笔记"
        is com.bitwarden.vault.CipherListViewType.SshKey -> "SSH 密钥"
        is com.bitwarden.vault.CipherListViewType.BankAccount -> "银行账户"
        is com.bitwarden.vault.CipherListViewType.DriversLicense -> "驾照"
        is com.bitwarden.vault.CipherListViewType.Passport -> "护照"
    }

/**
 * TOTP tab: verification codes with a per-second countdown.
 */
private fun ScalingLazyListScope.TotpTabContent(authCodes: List<VerificationCodeItem>) {
    if (authCodes.isEmpty()) {
        item {
            SectionPlaceholder(
                title = "暂无验证码",
                hint = "开启两步验证的登录项会在这里显示实时验证码",
            )
        }
    } else {
        items(authCodes) { item ->
            CodeItemCard(item = item)
        }
    }
}

/**
 * A single TOTP row with live countdown.
 */
@Composable
private fun CodeItemCard(item: VerificationCodeItem) {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(item.id) {
        while (true) {
            delay(1_000)
            tick++
        }
    }
    val secondsLeft = (item.timeLeftSeconds - tick).toInt().coerceAtLeast(0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Text(
            text = item.code,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Text(
            text = "${secondsLeft}s",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
        )
    }
}

/**
 * Settings tab: account email, manual sync and app info.
 */
private fun ScalingLazyListScope.SettingsTabContent(
    accountEmail: String,
    onSyncClick: () -> Unit,
) {
    if (accountEmail.isNotBlank()) {
        item {
            Text(
                text = accountEmail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
    item {
        androidx.wear.compose.material3.Button(
            onClick = onSyncClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(text = "立即同步")
        }
    }
    item {
        Text(
            text = "Bitwarden Wear ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/**
 * Placeholder content for a home section.
 */
@Composable
private fun SectionPlaceholder(
    title: String,
    hint: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
    Text(
        text = hint,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )
}