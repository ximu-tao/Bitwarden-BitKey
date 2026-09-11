package com.x8bit.bitwarden.ui.platform.feature.settings.autofill.util

import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.util.Text
import com.bitwarden.ui.util.asText
import com.x8bit.bitwarden.data.platform.repository.model.UriMatchType
import com.x8bit.bitwarden.data.platform.util.toSdkUriMatchType as toDataSdkUriMatchType

/**
 * Returns a human-readable display label for the given [UriMatchType].
 */
val UriMatchType.displayLabel: Text
    get() = when (this) {
        UriMatchType.DOMAIN -> BitwardenString.base_domain
        UriMatchType.HOST -> BitwardenString.host
        UriMatchType.STARTS_WITH -> BitwardenString.starts_with
        UriMatchType.REGULAR_EXPRESSION -> BitwardenString.reg_ex
        UriMatchType.EXACT -> BitwardenString.exact
        UriMatchType.NEVER -> BitwardenString.never
    }
        .asText()

/**
 * Convert this internal [UriMatchType] to the sdk model.
 */
fun UriMatchType.toSdkUriMatchType(): com.bitwarden.vault.UriMatchType =
    this.toDataSdkUriMatchType()

/**
 * Checks if the [UriMatchType] is considered an advanced matching strategy.
 */
fun UriMatchType.isAdvancedMatching(): Boolean =
    when (this) {
        UriMatchType.REGULAR_EXPRESSION,
        UriMatchType.STARTS_WITH,
            -> true

        else -> false
    }
