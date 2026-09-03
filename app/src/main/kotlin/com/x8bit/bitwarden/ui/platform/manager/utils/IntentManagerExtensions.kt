@file:OmitFromCoverage

package com.x8bit.bitwarden.ui.platform.manager.utils

import android.content.Intent
import android.provider.Settings
import com.bitwarden.annotation.OmitFromCoverage
import com.bitwarden.ui.platform.manager.IntentManager
import com.x8bit.bitwarden.data.autofill.model.browser.BrowserPackage
import androidx.core.net.toUri

/**
 * Starts the browser autofill settings activity for the provided [browserPackage].
 */
fun IntentManager.startBrowserAutofillSettingsActivity(
    browserPackage: BrowserPackage,
): Boolean {
    val intent = Intent(Intent.ACTION_APPLICATION_PREFERENCES)
        .apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_APP_BROWSER)
            addCategory(Intent.CATEGORY_PREFERENCE)
            setPackage(browserPackage.packageName)
        }
    return startActivity(intent)
}

/**
 * Starts the system "App info" screen for the current app so the user can grant a permission
 * that was permanently denied.
 *
 * Returns `true` if the activity was launched successfully, `false` otherwise (e.g. the
 * device has no Settings provider installed).
 */
fun IntentManager.startApplicationDetailsSettingsActivity(): Boolean {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:$packageName".toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return startActivity(intent)
}
