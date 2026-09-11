package com.x8bit.bitwarden.wear.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitwarden.vault.CipherListView
import com.bitwarden.vault.CipherListViewType
import com.x8bit.bitwarden.data.auth.manager.UserStateManager
import com.x8bit.bitwarden.data.vault.manager.model.VerificationCodeItem
import com.x8bit.bitwarden.data.vault.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View model for the Wear OS home screen.
 *
 * Hosts the vault browsing, TOTP, settings and BitKey surfaces. Cipher and
 * verification code data come verbatim from `:appdata` via [VaultRepository],
 * so the phone app's decryption and premium-gating logic is reused as-is.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    userStateManager: UserStateManager,
) : ViewModel() {

    /**
     * The current home screen state.
     */
    var uiState by mutableStateOf(HomeUiState())
        private set

    /**
     * Emits a request to navigate to the detail screen of [cipherId].
     */
    private val _cipherClickEvent = MutableSharedFlow<String>()
    val cipherClickEvent: SharedFlow<String> = _cipherClickEvent.asSharedFlow()

    /**
     * The filter selected on the vault tab.
     */
    enum class VaultFilter(
        val label: String,
        val matchesType: (CipherListViewType) -> Boolean,
    ) {
        ALL(label = "全部", matchesType = { true }),
        LOGIN(label = "登录", matchesType = { it is CipherListViewType.Login }),
        CARD(label = "卡片", matchesType = { it is CipherListViewType.Card }),
        IDENTITY(label = "身份", matchesType = { it is CipherListViewType.Identity }),
        SECURE_NOTE(label = "笔记", matchesType = { it is CipherListViewType.SecureNote }),
    }

    /**
     * The UI state of the home screen.
     */
    data class HomeUiState(
        val accountEmail: String = "",
        val ciphers: List<CipherListView> = emptyList(),
        val authCodes: List<VerificationCodeItem> = emptyList(),
        val isLoading: Boolean = true,
        val selectedFilter: VaultFilter = VaultFilter.ALL,
        val syncErrorMessage: String? = null,
    )

    init {
        uiState = uiState.copy(
            accountEmail = userStateManager.userStateFlow.value?.activeAccount?.email.orEmpty(),
        )
        observeVaultData()
        observeAuthCodes()
    }

    /**
     * The vault-browsing cipher list filtered by the currently selected type.
     */
    val filteredCiphers: List<CipherListView>
        get() = uiState.ciphers
            .filter { cipherView ->
                uiState.selectedFilter.matchesType(cipherView.type)
            }
            .sortedBy { it.name.orEmpty().lowercase() }

    /**
     * Selects the vault list filter.
     */
    fun onFilterSelect(filter: VaultFilter) {
        uiState = uiState.copy(selectedFilter = filter)
    }

    /**
     * Requests navigation to the cipher detail screen.
     */
    fun onCipherClick(cipherId: String) {
        viewModelScope.launch { _cipherClickEvent.emit(cipherId) }
    }

    /**
     * Triggers a forced vault sync and surfaces failures in the UI.
     */
    fun syncVault() {
        viewModelScope.launch {
            uiState = uiState.copy(
                syncErrorMessage = null,
                isLoading = true,
            )
            vaultRepository.sync(forced = true)
        }
    }

    private fun observeVaultData() {
        viewModelScope.launch {
            vaultRepository.vaultDataStateFlow.collect { dataState ->
                dataState.data?.let { vaultData ->
                    uiState = uiState.copy(
                        ciphers = vaultData.decryptCipherListResult.successes,
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun observeAuthCodes() {
        viewModelScope.launch {
            vaultRepository.getAuthCodesFlow().collect { dataState ->
                dataState.data?.let { uiState = uiState.copy(authCodes = it) }
            }
        }
    }

    /**
     * Clears any sync error message shown in settings.
     */
    fun clearSyncError() {
        uiState = uiState.copy(syncErrorMessage = null)
    }
}