package com.x8bit.bitwarden.wear.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitwarden.data.datasource.disk.model.EnvironmentUrlDataJson
import com.bitwarden.data.repository.model.Environment
import com.x8bit.bitwarden.data.platform.repository.EnvironmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * View model for the Wear OS server environment screen.
 *
 * Mirrors the phone app's environment settings by writing the chosen server
 * back through the shared [EnvironmentRepository] from `:appdata`. The network
 * layer (`BaseUrlsProviderImpl`) reads the environment from disk on every
 * request, so login and vault sync automatically target the new server once
 * the selection is saved — no app restart required.
 */
@HiltViewModel
class EnvironmentViewModel @Inject constructor(
    private val environmentRepository: EnvironmentRepository,
) : ViewModel() {

    /**
     * The current server selection form state.
     */
    var uiState by mutableStateOf(initialUiState())
        private set

    init {
        // Reflect changes made elsewhere (e.g. saved per-account environment
        // after login) without relaunching the screen.
        viewModelScope.launch {
            environmentRepository.environmentStateFlow.collect { environment ->
                uiState = uiState.copy(
                    selectedPreset = when (environment) {
                        is Environment.Prod.Us -> ServerPreset.BITWARDEN_COM
                        is Environment.Prod.Eu -> ServerPreset.BITWARDEN_EU
                        // Any other environment is treated as a custom URL.
                        is Environment.Prod.FedRamp,
                        is Environment.SelfHosted -> ServerPreset.SELF_HOSTED
                    },
                    customUrl = when (environment) {
                        is Environment.SelfHosted -> environment.environmentUrlData.base
                        else -> ""
                    },
                )
            }
        }
    }

    /**
     * Selects one of the predefined [ServerPreset] options.
     */
    fun onPresetSelect(preset: ServerPreset) {
        uiState = uiState.copy(
            selectedPreset = preset,
            errorMessage = null,
        )
    }

    /**
     * Updates the custom self-hosted server URL.
     */
    fun onCustomUrlChange(url: String) {
        uiState = uiState.copy(customUrl = url, errorMessage = null)
    }

    /**
     * Persists the selected server and invokes [onSaved] when validation
     * passes; otherwise surfaces the validation problem inline.
     */
    fun save(onSaved: () -> Unit) {
        when (uiState.selectedPreset) {
            ServerPreset.BITWARDEN_COM -> {
                environmentRepository.environment = Environment.Prod.Us
                onSaved()
            }

            ServerPreset.BITWARDEN_EU -> {
                environmentRepository.environment = Environment.Prod.Eu
                onSaved()
            }

            ServerPreset.SELF_HOSTED -> {
                val baseUrl = uiState.customUrl
                    .trim()
                    .prefixHttpsIfNecessaryOrNull()
                if (baseUrl == null) {
                    uiState = uiState.copy(errorMessage = INVALID_URL_MESSAGE)
                    return
                }
                environmentRepository.environment = Environment.SelfHosted(
                    environmentUrlData = EnvironmentUrlDataJson(base = baseUrl),
                )
                onSaved()
            }
        }
    }

    private fun initialUiState(): EnvironmentUiState {
        val environment = environmentRepository.environment
        return EnvironmentUiState(
            selectedPreset = when (environment) {
                is Environment.Prod.Us -> ServerPreset.BITWARDEN_COM
                is Environment.Prod.Eu -> ServerPreset.BITWARDEN_EU
                else -> ServerPreset.SELF_HOSTED
            },
            // Only prefill a URL when a custom environment is already set.
            customUrl = when (environment) {
                is Environment.SelfHosted -> environment.environmentUrlData.base
                else -> ""
            },
        )
    }

    /**
     * The server choices offered on the wear environment screen.
     */
    enum class ServerPreset(val label: String) {
        /**
         * The official Bitwarden US cloud.
         */
        BITWARDEN_COM(label = "bitwarden.com"),

        /**
         * The official Bitwarden EU cloud.
         */
        BITWARDEN_EU(label = "bitwarden.eu"),

        /**
         * A custom self-hosted server URL.
         */
        SELF_HOSTED(label = "自托管"),
    }

    /**
     * The UI state of the environment screen.
     */
    data class EnvironmentUiState(
        val selectedPreset: ServerPreset = ServerPreset.BITWARDEN_COM,
        val customUrl: String = "",
        val errorMessage: String? = null,
    )

    private companion object {
        const val INVALID_URL_MESSAGE: String = "请输入有效的服务器地址"
    }
}

/**
 * Returns the [String] with "https://" prepended when it is a valid URI
 * without an explicit scheme, or `null` when it is not a valid URI.
 *
 * Mirrors the phone app's `prefixHttpsIfNecessaryOrNull()` validation so
 * self-hosted URLs behave identically on both surfaces.
 */
private fun String.prefixHttpsIfNecessaryOrNull(): String? = when {
    isBlank() || !isValidUri() -> null
    "http://" in this || "https://" in this -> this
    else -> "https://$this"
}

/**
 * Returns `true` when the [String] is a non-blank, well-formed URI.
 */
private fun String.isValidUri(): Boolean = try {
    URI.create(this)
    isNotBlank()
} catch (_: IllegalArgumentException) {
    false
}