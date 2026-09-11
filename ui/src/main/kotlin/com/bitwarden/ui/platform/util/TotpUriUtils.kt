package com.bitwarden.ui.platform.util

import android.net.Uri
import androidx.core.net.toUri
import com.x8bit.bitwarden.data.platform.model.TotpData
import com.x8bit.bitwarden.data.platform.util.getTotpDataOrNull as getSharedTotpDataOrNull

private const val TOTP_HOST_NAME: String = "totp"
private const val TOTP_SCHEME_NAME: String = "otpauth"
private const val PARAM_NAME_ALGORITHM: String = "algorithm"
private const val PARAM_NAME_DIGITS: String = "digits"
private const val PARAM_NAME_ISSUER: String = "issuer"
private const val PARAM_NAME_PERIOD: String = "period"
private const val PARAM_NAME_SECRET: String = "secret"

/**
 * Checks if the given [String] contains valid data for a TOTP. The [TotpData] will be returned
 * when the correct data is present or `null` if data is invalid or missing.
 */
fun String.getTotpDataOrNull(): TotpData? = this.toUri().getTotpDataOrNull()

/**
 * Checks if the given [Uri] contains valid data for a TOTP. The [TotpData] will be returned when
 * the correct data is present or `null` if data is invalid or missing.
 *
 * The implementation lives in the shared `:appdata` module; this file only forwards to it to
 * preserve the original package for existing UI-layer call sites.
 */
fun Uri.getTotpDataOrNull(): TotpData? = this.getSharedTotpDataOrNull()