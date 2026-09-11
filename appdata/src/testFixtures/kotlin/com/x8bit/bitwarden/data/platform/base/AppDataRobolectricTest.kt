package com.x8bit.bitwarden.data.platform.base

import dagger.hilt.android.testing.HiltTestApplication
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * A base class that can be used for performing tests that use Robolectric and JUnit 4.
 *
 * This mirrors the `BaseRobolectricTest` in the `:ui` module's test fixtures so the shared
 * `:appdata` module does not need to depend on the UI layer.
 */
@Config(
    application = HiltTestApplication::class,
    sdk = [Config.NEWEST_SDK],
)
@RunWith(RobolectricTestRunner::class)
abstract class AppDataRobolectricTest {
    init {
        ShadowLog.stream = System.out
    }
}