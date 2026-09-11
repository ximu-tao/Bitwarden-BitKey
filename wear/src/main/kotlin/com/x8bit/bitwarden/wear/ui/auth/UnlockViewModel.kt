package com.x8bit.bitwarden.wear.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x8bit.bitwarden.data.auth.manager.UserStateManager
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.auth.repository.model.LogoutReason
import com.x8bit.bitwarden.data.vault.repository.VaultRepository
import com.x8bit.bitwarden.data.vault.repository.model.VaultUnlockResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View model for the Wear OS unlock screen.
 *
 * Reuses [VaultRepository.unlockVaultWithMasterPassword] from `:appdata` so the
 * key derivation and vault decryption logic is identical to the phone app.
 * Most wearables have no fingerprint hardware, so the master password (or PIN
 * where the account uses one) is the primary unlock path.
 */
@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val authRepository: AuthRepository,
    userStateManager: UserStateManager,
) : ViewModel() {

    /**
     * The current unlock form state.
     */
    var uiState by mutableStateOf(UnlockUiState())
        private set

    /**
     * Emits a single event when the vault is successfully unlocked.
     */
    private val _unlockSuccessEvent = MutableSharedFlow<Unit>()
    val unlockSuccessEvent: SharedFlow<Unit> = _unlockSuccessEvent.asSharedFlow()

    /**
     * Emits a single event after the user opts to log out.
     */
    private val _logoutSuccessEvent = MutableSharedFlow<Unit>()
    val logoutSuccessEvent: SharedFlow<Unit> = _logoutSuccessEvent.asSharedFlow()

    init {
        // The account email is read once at start-up; the unlock screen only
        // exists while an account is active.
        uiState = uiState.copy(
            accountEmail = userStateManager.userStateFlow.value?.activeAccount?.email.orEmpty(),
        )
    }

    /**
     * The UI state of the unlock screen.
     */
    data class UnlockUiState(
        val accountEmail: String = "",
        val password: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    )

    /**
     * Updates the entered master password.
     */
    fun onPasswordChange(password: String) {
        uiState = uiState.copy(password = password)
    }

    /**
     * Attempts to unlock the vault with the entered password.
     */
    fun unlock() {
        if (uiState.isLoading) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            when (val result = vaultRepository.unlockVaultWithMasterPassword(uiState.password)) {
                VaultUnlockResult.Success -> {
                    uiState = uiState.copy(isLoading = false)
                    _unlockSuccessEvent.emit(Unit)
                }

                is VaultUnlockResult.AuthenticationError -> {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = result.message ?: INVALID_PASSWORD_MESSAGE,
                    )
                }

                is VaultUnlockResult.InvalidStateError,
                is VaultUnlockResult.BiometricDecodingError,
                is VaultUnlockResult.GenericError -> {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = GENERIC_ERROR_MESSAGE,
                    )
                }
            }
        }
    }

    /**
     * Logs out of the current account so the user can sign in again from the
     * login screen. This is the recovery path when unlocking is impossible
     * (e.g. the Android Keystore keys backing the account were lost).
     */
    fun logout() {
        authRepository.logout(reason = LogoutReason.Click(source = "UnlockViewModel"))
        viewModelScope.launch { _logoutSuccessEvent.emit(Unit) }
    }

    private companion object {
        const val INVALID_PASSWORD_MESSAGE: String = "主密码错误，请重试"
        const val GENERIC_ERROR_MESSAGE: String = "解锁失败，请稍后重试"
    }
}