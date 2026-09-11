package com.x8bit.bitwarden.wear

import android.app.Application
import com.bitwarden.annotation.OmitFromCoverage
import com.x8bit.bitwarden.data.platform.manager.network.NetworkConfigManager
import com.x8bit.bitwarden.data.platform.repository.EnvironmentRepository
import com.x8bit.bitwarden.wear.sync.VaultSyncWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Custom application class for the Wear OS variant.
 */
@OmitFromCoverage
@HiltAndroidApp
class BitwardenWearApplication : Application() {

    @Inject
    lateinit var environmentRepository: EnvironmentRepository

    @Inject
    lateinit var networkConfigManager: NetworkConfigManager

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        // Keep the persisted server environment in sync with the active account
        // (used for login against self-hosted servers). Network configuration
        // reloads and token refresh are wired up by injecting
        // [networkConfigManager] above; its constructor starts collecting the
        // environment flow immediately.
        environmentRepository.initialize()
        // Daily background vault sync; WorkManager deduplicates via unique work.
        VaultSyncWorker.schedule(this)
    }
}