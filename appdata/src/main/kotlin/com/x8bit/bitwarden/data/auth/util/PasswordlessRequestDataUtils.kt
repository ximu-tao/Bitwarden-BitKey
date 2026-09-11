package com.x8bit.bitwarden.data.auth.util

import android.content.Context
import android.content.Intent
import com.x8bit.bitwarden.data.platform.util.getSafeParcelableExtra
import com.x8bit.bitwarden.data.platform.manager.model.PasswordlessRequestData

private const val NOTIFICATION_DATA: String = "notificationData"

/**
 * Creates an [Intent] that can be used to navigate the pending auth approval screen.
 *
 * The target activity lives in the hosting app, so the class name is resolved by name to keep the
 * shared data layer free of app-specific types.
 */
fun createPasswordlessRequestDataIntent(
    context: Context,
    data: PasswordlessRequestData,
): Intent =
    Intent()
        .setClassName(context, "com.x8bit.bitwarden.MainActivity")
        .putExtra(NOTIFICATION_DATA, data)

/**
 * Checks if the given [Intent] contains data for passwordless authorization.
 * The [PasswordlessRequestData] will be returned when present.
 */
fun Intent.getPasswordlessRequestDataIntentOrNull(): PasswordlessRequestData? =
    this.getSafeParcelableExtra(NOTIFICATION_DATA)
