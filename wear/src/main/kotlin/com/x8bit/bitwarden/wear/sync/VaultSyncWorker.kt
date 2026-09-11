package com.x8bit.bitwarden.wear.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.x8bit.bitwarden.data.auth.manager.UserStateManager
import com.x8bit.bitwarden.data.vault.repository.VaultRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Background vault sync for the Wear OS app.
 *
 * Runs once per day via WorkManager (which is itself Doze-friendly), keeps the
 * local Room copy fresh for offline browsing, and depends on the same
 * [VaultRepository] as the phone app via `:appdata`.
 *
 * Injection uses a Hilt [EntryPoint] instead of the `@HiltWorker` factory so
 * the module needs no extra hilt-work dependency; the entry point is scoped to
 * the singleton component which always exists in this process.
 */
class VaultSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            VaultSyncWorkerEntryPoint::class.java,
        )
        // Nothing to sync while no account is active; finish without retrying.
        if (entryPoint.userStateManager().userStateFlow.value?.activeUserId == null) {
            return Result.success()
        }
        return try {
            entryPoint.vaultRepository().sync(forced = false)
            Result.success()
        } catch (_: Exception) {
            // Offline or transient failures: let WorkManager back off and retry.
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME: String = "wear-vault-daily-sync"

        /**
         * Enqueues the periodic sync worker. Safe to call from `Application.onCreate`;
         * the [ExistingPeriodicWorkPolicy.KEEP] policy guarantees a single copy.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<VaultSyncWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

/**
 * Hilt entry point exposing the `:appdata` managers needed by [VaultSyncWorker].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface VaultSyncWorkerEntryPoint {
    fun vaultRepository(): VaultRepository

    fun userStateManager(): UserStateManager
}