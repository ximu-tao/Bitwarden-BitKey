package com.x8bit.bitwarden.data.platform.manager.di

import android.app.Application
import android.content.Context
import com.bitwarden.core.data.manager.toast.ToastManager
import com.bitwarden.cxf.parser.CredentialExchangePayloadParser
import com.bitwarden.cxf.registry.CredentialExchangeRegistry
import com.bitwarden.cxf.registry.dsl.credentialExchangeRegistry
import com.bitwarden.network.service.CiphersService
import com.bitwarden.ui.platform.feature.cardscanner.manager.CardScanManager
import com.bitwarden.ui.platform.feature.cardscanner.manager.CardScanManagerImpl
import com.x8bit.bitwarden.ui.platform.manager.resource.ResourceManager
import com.x8bit.bitwarden.data.autofill.manager.FillAssistManager
import com.x8bit.bitwarden.data.auth.datasource.disk.AuthDiskSource
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.platform.datasource.disk.SettingsDiskSource
import com.x8bit.bitwarden.data.platform.datasource.disk.legacy.LegacyAppCenterMigrator
import com.x8bit.bitwarden.data.platform.manager.AppResumeManager
import com.x8bit.bitwarden.data.platform.manager.AppResumeManagerImpl
import com.x8bit.bitwarden.data.platform.manager.CredentialExchangeRegistryManager
import com.x8bit.bitwarden.data.platform.manager.CredentialExchangeRegistryManagerImpl
import com.x8bit.bitwarden.data.platform.manager.PolicyManager
import com.x8bit.bitwarden.data.platform.manager.clipboard.BitwardenClipboardManager
import com.x8bit.bitwarden.data.platform.manager.clipboard.BitwardenClipboardManagerImpl
import com.x8bit.bitwarden.data.platform.manager.network.NetworkPermissionManager
import com.x8bit.bitwarden.data.platform.manager.network.NetworkPermissionManagerImpl
import com.x8bit.bitwarden.data.platform.manager.LogsManager
import com.x8bit.bitwarden.data.platform.manager.LogsManagerImpl
import com.x8bit.bitwarden.data.platform.repository.SettingsRepository
import com.x8bit.bitwarden.data.vault.datasource.sdk.VaultSdkSource
import com.x8bit.bitwarden.data.vault.manager.CredentialExchangeImportManager
import com.x8bit.bitwarden.data.vault.manager.CredentialExchangeImportManagerImpl
import com.x8bit.bitwarden.data.vault.manager.FillAssistSyncManager
import com.x8bit.bitwarden.data.vault.manager.VaultLockManager
import com.x8bit.bitwarden.data.vault.manager.VaultSyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Provides app-only managers that were previously supplied by the shared data layer. These
 * implementations depend on UI or app-specific components (e.g. `:ui` resources, custom tabs,
 * credential exchange) and therefore cannot live in the shared `:appdata` module.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppOnlyManagerModule {

    @Provides
    @Singleton
    fun provideCardScanManager(): CardScanManager = CardScanManagerImpl()

    @Provides
    @Singleton
    fun provideCredentialExchangeImportManager(
        vaultSdkSource: VaultSdkSource,
        ciphersService: CiphersService,
        vaultSyncManager: VaultSyncManager,
        policyManager: PolicyManager,
        credentialExchangePayloadParser: CredentialExchangePayloadParser,
    ): CredentialExchangeImportManager = CredentialExchangeImportManagerImpl(
        vaultSdkSource = vaultSdkSource,
        ciphersService = ciphersService,
        vaultSyncManager = vaultSyncManager,
        policyManager = policyManager,
        credentialExchangePayloadParser = credentialExchangePayloadParser,
    )

    @Provides
    @Singleton
    fun provideAppResumeManager(
        settingsDiskSource: SettingsDiskSource,
        authDiskSource: AuthDiskSource,
        authRepository: AuthRepository,
        vaultLockManager: VaultLockManager,
        clock: Clock,
    ): AppResumeManager {
        return AppResumeManagerImpl(
            settingsDiskSource = settingsDiskSource,
            authDiskSource = authDiskSource,
            authRepository = authRepository,
            vaultLockManager = vaultLockManager,
            clock = clock,
        )
    }

    @Provides
    @Singleton
    fun provideCredentialExchangeRegistry(
        application: Application,
    ): CredentialExchangeRegistry = credentialExchangeRegistry(
        application = application,
    )

    @Provides
    @Singleton
    fun provideCredentialExchangeRegistryManager(
        credentialExchangeRegistry: CredentialExchangeRegistry,
        settingsDiskSource: SettingsDiskSource,
    ): CredentialExchangeRegistryManager = CredentialExchangeRegistryManagerImpl(
        credentialExchangeRegistry = credentialExchangeRegistry,
        settingsDiskSource = settingsDiskSource,
    )

    @Provides
    @Singleton
    fun provideNetworkPermissionManager(
        @ApplicationContext context: Context,
        resourceManager: ResourceManager,
    ): NetworkPermissionManager = NetworkPermissionManagerImpl(
        context = context,
        resourceManager = resourceManager,
    )

    @Provides
    @Singleton
    fun provideBitwardenClipboardManager(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository,
        toastManager: ToastManager,
    ): BitwardenClipboardManager = BitwardenClipboardManagerImpl(
        context = context,
        settingsRepository = settingsRepository,
        toastManager = toastManager,
    )

    @Provides
    @Singleton
    fun provideLogsManager(
        legacyAppCenterMigrator: LegacyAppCenterMigrator,
        settingsRepository: SettingsRepository,
    ): LogsManager = LogsManagerImpl(
        settingsRepository = settingsRepository,
        legacyAppCenterMigrator = legacyAppCenterMigrator,
    )

    @Provides
    @Singleton
    fun provideFillAssistSyncManager(
        fillAssistManager: FillAssistManager,
    ): FillAssistSyncManager =
        object : FillAssistSyncManager {
            override fun syncIfNecessary() {
                fillAssistManager.syncIfNecessary()
            }
        }
}