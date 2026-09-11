@file:OmitFromCoverage

package com.x8bit.bitwarden.data.platform.util

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import com.bitwarden.annotation.OmitFromCoverage
import com.x8bit.bitwarden.data.auth.manager.AddTotpItemFromAuthenticatorManager

private const val ADD_TOTP_ITEM_FROM_AUTHENTICATOR_KEY = "add-totp-item-from-authenticator-key"

/**
 * Creates an intent for launching add TOTP item flow from the Authenticator app.
 *
 * The target activity lives in the hosting app, so the class name is resolved by name to keep the
 * shared data layer free of app-specific types.
 */
fun createAddTotpItemFromAuthenticatorIntent(
    context: Context,
): Intent =
    Intent()
        .setClassName(context, "com.x8bit.bitwarden.MainActivity")
        .apply {
            putExtra(
                ADD_TOTP_ITEM_FROM_AUTHENTICATOR_KEY,
                true,
            )
            addFlags(FLAG_ACTIVITY_NEW_TASK)
            addFlags(FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

/**
 * Returns true if the Intent was started by the Authenticator app to add a TOTP item. The TOTP
 * item can be found in [AddTotpItemFromAuthenticatorManager].
 */
fun Intent.isAddTotpLoginItemFromAuthenticator(): Boolean =
    getBooleanExtra(ADD_TOTP_ITEM_FROM_AUTHENTICATOR_KEY, false)
