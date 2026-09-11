package com.x8bit.bitwarden.wear

import android.app.Application
import com.bitwarden.annotation.OmitFromCoverage
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Custom application class for the Wear OS variant.
 */
@OmitFromCoverage
@HiltAndroidApp
class BitwardenWearApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}