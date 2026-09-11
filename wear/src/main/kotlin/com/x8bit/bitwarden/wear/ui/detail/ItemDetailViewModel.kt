package com.x8bit.bitwarden.wear.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitwarden.vault.CipherView
import com.x8bit.bitwarden.data.vault.manager.model.VerificationCodeItem
import com.x8bit.bitwarden.data.vault.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View model for the Wear OS cipher detail screen.
 *
 * The [cipherView] and TOTP code are resolved from `:appdata` streams keyed by
 * the cipher id, so the detail view always reflects the latest decrypted state
 * without any additional I/O on the wear side.
 */
@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    /**
     * The current detail screen state.
     */
    var uiState by mutableStateOf(ItemDetailUiState())
        private set

    /**
     * The UI state of the detail screen.
     */
    data class ItemDetailUiState(
        val cipherView: CipherView? = null,
        val authCode: VerificationCodeItem? = null,
        val isPasswordVisible: Boolean = false,
    )

    /**
     * Starts observing the cipher and its TOTP code.
     */
    fun start(cipherId: String) {
        viewModelScope.launch {
            vaultRepository.getVaultItemStateFlow(itemId = cipherId).collect { dataState ->
                dataState.data?.let { uiState = uiState.copy(cipherView = it) }
            }
        }
        viewModelScope.launch {
            vaultRepository.getAuthCodeFlow(cipherId = cipherId).collect { dataState ->
                dataState.data?.let { uiState = uiState.copy(authCode = it) }
            }
        }
    }

    /**
     * Toggles the visibility of the password field.
     */
    fun togglePasswordVisibility() {
        uiState = uiState.copy(isPasswordVisible = !uiState.isPasswordVisible)
    }
}