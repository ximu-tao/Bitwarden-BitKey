package com.x8bit.bitwarden.wear.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitwarden.network.model.TwoFactorDataModel
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.auth.repository.model.LoginResult
import com.x8bit.bitwarden.data.platform.repository.EnvironmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View model for the Wear OS login screen.
 *
 * Reuses the [AuthRepository] from `:appdata`, so the phone app's login logic
 * (pre-login KDF, identity API calls, token storage) is shared verbatim.
 * The wear UI only drives the flow: email + master password first, then an
 * inline two-factor code step when the server requires it.
 *
 * The currently configured server environment (e.g. `bitwarden.com`, a
 * self-hosted URL) is surfaced so the user can switch servers before logging
 * in; the switch itself happens on a dedicated environment screen.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val environmentRepository: EnvironmentRepository,
) : ViewModel() {

    /**
     * The current login form state.
     */
    var uiState by mutableStateOf(LoginUiState())
        private set

    init {
        // Keep the shown environment label in sync with the repository so it
        // reflects changes made on the environment screen without relaunching.
        // Note: this block must come after `uiState` is initialized because
        // StateFlow.collect emits the current value synchronously on
        // Dispatchers.Main.immediate, which would otherwise read the unset
        // property delegate and crash.
        viewModelScope.launch {
            environmentRepository.environmentStateFlow.collect { environment ->
                uiState = uiState.copy(environmentLabel = environment.label)
            }
        }
    }

    /**
     * Emits a single event when login completes successfully.
     */
    private val _loginSuccessEvent = MutableSharedFlow<Unit>()
    val loginSuccessEvent: SharedFlow<Unit> = _loginSuccessEvent.asSharedFlow()

    /**
     * The UI state of the login screen.
     */
    data class LoginUiState(
        val email: String = "",
        val password: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val requiresTwoFactor: Boolean = false,
        val twoFactorCode: String = "",
        val environmentLabel: String = "",
    )

    /**
     * Updates the entered email address.
     */
    fun onEmailChange(email: String) {
        uiState = uiState.copy(email = email)
    }

    /**
     * Updates the entered master password.
     */
    fun onPasswordChange(password: String) {
        uiState = uiState.copy(password = password)
    }

    /**
     * Attempts to log in with the entered email and master password.
     */
    fun login() {
        if (uiState.isLoading) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            val result = authRepository.login(
                email = uiState.email,
                password = uiState.password,
            )
            handleLoginResult(result)
        }
    }

    /**
     * Updates the entered two-factor code.
     */
    fun onTwoFactorCodeChange(code: String) {
        uiState = uiState.copy(twoFactorCode = code)
    }

    /**
     * Confirms the two-factor code and completes the login.
     */
    fun confirmTwoFactor() {
        val code = uiState.twoFactorCode
        if (code.isBlank() || uiState.isLoading) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            val result = authRepository.login(
                email = uiState.email,
                password = uiState.password,
                twoFactorData = TwoFactorDataModel(
                    code = code,
                    method = AUTHENTICATOR_METHOD_ID,
                    remember = false,
                ),
                orgIdentifier = null,
            )
            handleLoginResult(result)
        }
    }

    private fun handleLoginResult(loginResult: LoginResult) {
        when (loginResult) {
            is LoginResult.Success -> {
                uiState = uiState.copy(isLoading = false)
                viewModelScope.launch { _loginSuccessEvent.emit(Unit) }
            }

            // The wear UI keeps the two-factor step inline rather than
            // navigating to a separate screen.
            LoginResult.TwoFactorRequired,
            is LoginResult.NewDeviceVerification -> {
                uiState = uiState.copy(isLoading = false, requiresTwoFactor = true)
            }

            is LoginResult.Error -> {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = loginResult.errorMessage ?: GENERIC_ERROR_MESSAGE,
                )
            }

            // Not applicable on the wear flow; surface as an error.
            else -> {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = GENERIC_ERROR_MESSAGE,
                )
            }
        }
    }

    private companion object {
        /**
         * `TwoFactorAuthMethod.AUTHENTICATOR_APP` (TOTP authenticator app).
         */
        const val AUTHENTICATOR_METHOD_ID: String = "0"

        const val GENERIC_ERROR_MESSAGE: String = "登录失败，请稍后重试"
    }
}