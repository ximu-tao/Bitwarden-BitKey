package com.x8bit.bitwarden.wear.di

import android.content.Context
import android.view.autofill.AutofillManager
import com.bitwarden.core.data.manager.BuildInfoManager
import com.bitwarden.network.provider.PermissionProvider
import com.x8bit.bitwarden.data.autofill.accessibility.manager.AccessibilityEnabledManager
import com.x8bit.bitwarden.data.autofill.manager.AutofillEnabledManager
import com.x8bit.bitwarden.data.autofill.manager.browser.BrowserThirdPartyAutofillEnabledManager
import com.x8bit.bitwarden.data.autofill.model.browser.BrowserThirdPartyAutoFillData
import com.x8bit.bitwarden.data.autofill.model.browser.BrowserThirdPartyAutofillStatus
import com.x8bit.bitwarden.data.platform.manager.CredentialExchangeRegistryManager
import com.x8bit.bitwarden.data.platform.manager.LogsManager
import com.x8bit.bitwarden.data.platform.manager.model.RegisterExportResult
import com.x8bit.bitwarden.data.platform.manager.model.UnregisterExportResult
import com.x8bit.bitwarden.data.platform.manager.network.NetworkPermissionManager
import com.x8bit.bitwarden.data.vault.manager.CredentialExchangeImportManager
import com.x8bit.bitwarden.data.vault.manager.FillAssistSyncManager
import com.x8bit.bitwarden.data.vault.manager.model.ImportCxfPayloadResult
import com.x8bit.bitwarden.wear.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Singleton

/**
 * Provides app-layer bindings that the shared `:appdata` data layer requires.
 *
 * On the phone, these are supplied by the app module (e.g. `AppOnlyManagerModule`);
 * on wear they are either real implementations ([BuildInfoManager]) or no-op
 * stubs for phone-only concerns (autofill, credential exchange, local network).
 */
@Module
@InstallIn(SingletonComponent::class)
object WearAppOnlyBindingsModule {

    @Provides
    @Singleton
    fun provideBuildInfoManager(): BuildInfoManager = WearBuildInfoManagerImpl()

    @Provides
    @Singleton
    fun provideAutofillManager(@ApplicationContext context: Context): AutofillManager =
        checkNotNull(context.getSystemService(AutofillManager::class.java))

    @Provides
    @Singleton
    fun provideAutofillEnabledManager(): AutofillEnabledManager = object : AutofillEnabledManager {
        override var isAutofillEnabled: Boolean = false
        override val isAutofillEnabledStateFlow: StateFlow<Boolean> = MutableStateFlow(false)
    }

    @Provides
    @Singleton
    fun provideAccessibilityEnabledManager(): AccessibilityEnabledManager =
        object : AccessibilityEnabledManager {
            override val isAccessibilityEnabledStateFlow: StateFlow<Boolean> =
                MutableStateFlow(false)

            override fun refreshAccessibilityEnabledFromSettings() = Unit
        }

    @Provides
    @Singleton
    fun provideBrowserThirdPartyAutofillEnabledManager(): BrowserThirdPartyAutofillEnabledManager =
        object : BrowserThirdPartyAutofillEnabledManager {
            private val noBrowsersAvailable = BrowserThirdPartyAutofillStatus(
                braveStableStatusData = BrowserThirdPartyAutoFillData(
                    isAvailable = false,
                    isThirdPartyEnabled = false,
                ),
                chromeStableStatusData = BrowserThirdPartyAutoFillData(
                    isAvailable = false,
                    isThirdPartyEnabled = false,
                ),
                chromeBetaChannelStatusData = BrowserThirdPartyAutoFillData(
                    isAvailable = false,
                    isThirdPartyEnabled = false,
                ),
                vivaldiStableChannelStatusData = BrowserThirdPartyAutoFillData(
                    isAvailable = false,
                    isThirdPartyEnabled = false,
                ),
                defaultBrowserPackageName = null,
            )

            override var browserThirdPartyAutofillStatus: BrowserThirdPartyAutofillStatus =
                noBrowsersAvailable

            override val browserThirdPartyAutofillStatusFlow: Flow<BrowserThirdPartyAutofillStatus>
                get() = flowOf(noBrowsersAvailable)
        }

    @Provides
    @Singleton
    fun provideCredentialExchangeRegistryManager(): CredentialExchangeRegistryManager =
        NoOpCredentialExchangeRegistryManager()

    @Provides
    @Singleton
    fun provideCredentialExchangeImportManager(): CredentialExchangeImportManager =
        NoOpCredentialExchangeImportManager()

    @Provides
    @Singleton
    fun provideFillAssistSyncManager(): FillAssistSyncManager =
        object : FillAssistSyncManager {
            override fun syncIfNecessary() = Unit
        }

    @Provides
    @Singleton
    fun provideLogsManager(): LogsManager = object : LogsManager {
        override var isEnabled: Boolean = false
        override fun trackNonFatalException(throwable: Throwable) = Unit
        override fun setUserData(userId: String?, environmentType: com.bitwarden.data.repository.model.Environment.Type) = Unit
    }

    @Provides
    @Singleton
    fun provideNetworkPermissionManager(): NetworkPermissionManager = object : NetworkPermissionManager {
        override val errorMessageString: String = ""
        override val hasLocalNetworkAccessPermission: Boolean = true
        override fun acquireLocalNetworkAccessPermission() = Unit
        override val isLocalNetworkAccessRequiredStateFlow: StateFlow<Boolean> =
            MutableStateFlow(false)

        override fun clearIsLocalNetworkAccessRequired() = Unit
    }
}

/**
 * No-op [CredentialExchangeRegistryManager] for the Wear OS app; credential
 * exchange is a phone-only flow.
 */
@Suppress("UnusedPrivateClass")
private class NoOpCredentialExchangeRegistryManager : CredentialExchangeRegistryManager {
    override suspend fun register(): RegisterExportResult = RegisterExportResult.Failure(null)

    override suspend fun unregister(): UnregisterExportResult = UnregisterExportResult.Failure(null)
}

/**
 * No-op [CredentialExchangeImportManager] for the Wear OS app; credential
 * exchange is a phone-only flow.
 */
private class NoOpCredentialExchangeImportManager : CredentialExchangeImportManager {
    override suspend fun importCxfPayload(
        userId: String,
        payload: String,
    ): ImportCxfPayloadResult = ImportCxfPayloadResult.Error(
        error = NotImplementedError(
            "Credential exchange import is not available on Wear OS",
        ),
    )
}

/**
 * [BuildInfoManager] implementation backed by the wear module's [BuildConfig].
 */
private class WearBuildInfoManagerImpl : BuildInfoManager {
    override val applicationId: String
        get() = BuildConfig.APPLICATION_ID

    override val applicationName: String
        get() = "Bitwarden Wear"

    override val isFdroid: Boolean
        get() = false

    override val isDevBuild: Boolean
        get() = BuildConfig.BUILD_TYPE == "debug"

    override val isReleaseBuild: Boolean
        get() = BuildConfig.BUILD_TYPE == "release"

    override val versionData: String
        get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    override val sdkData: String
        get() = BuildConfig.SDK_VERSION

    override val ciBuildInfo: String?
        get() = null

    override val buildFlavorName: String
        get() = ""

    override val buildTypeName: String
        get() = when (BuildConfig.BUILD_TYPE) {
            "debug" -> "dev"
            "release" -> "prod"
            else -> BuildConfig.BUILD_TYPE
        }

    override val buildAndFlavor: String
        get() = BuildConfig.BUILD_TYPE
}