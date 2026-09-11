package com.x8bit.bitwarden.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitwarden.core.ClientManagedTokens
import com.bitwarden.data.manager.NativeLibraryManager
import com.bitwarden.sdk.Client
import com.x8bit.bitwarden.wear.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * POC-only view model that verifies the Bitwarden SDK native library can be loaded and a
 * [Client] instance can be constructed on a Wear OS device (arm64-v8a).
 *
 * This screen will be replaced by the real login/unlock flow in a later stage.
 */
@HiltViewModel
class SdkStatusViewModel @Inject constructor(
    private val nativeLibraryManager: NativeLibraryManager,
) : ViewModel() {

    private val mutableSdkStatusFlow = MutableStateFlow("Loading native library...")
    val sdkStatusFlow = mutableSdkStatusFlow.asStateFlow()

    init {
        viewModelScope.launch {
            mutableSdkStatusFlow.value = when {
                nativeLibraryManager.loadLibrary("bitwarden_uniffi").isFailure -> {
                    "Native library load failed"
                }

                runCatching {
                    Client(
                        tokenProvider = object : ClientManagedTokens {
                            override suspend fun getAccessToken(): String? = null
                        },
                        settings = null,
                    )
                }.isSuccess -> {
                    "SDK ${BuildConfig.SDK_VERSION} ready"
                }

                else -> {
                    "SDK construct failed"
                }
            }
        }
    }
}