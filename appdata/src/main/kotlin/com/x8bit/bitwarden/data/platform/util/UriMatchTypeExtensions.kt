package com.x8bit.bitwarden.data.platform.util

import com.x8bit.bitwarden.data.platform.repository.model.UriMatchType

/**
 * Convert this internal [UriMatchType] to the sdk model.
 */
fun UriMatchType.toSdkUriMatchType(): com.bitwarden.vault.UriMatchType =
    when (this) {
        UriMatchType.DOMAIN -> com.bitwarden.vault.UriMatchType.DOMAIN
        UriMatchType.EXACT -> com.bitwarden.vault.UriMatchType.EXACT
        UriMatchType.HOST -> com.bitwarden.vault.UriMatchType.HOST
        UriMatchType.NEVER -> com.bitwarden.vault.UriMatchType.NEVER
        UriMatchType.REGULAR_EXPRESSION -> com.bitwarden.vault.UriMatchType.REGULAR_EXPRESSION
        UriMatchType.STARTS_WITH -> com.bitwarden.vault.UriMatchType.STARTS_WITH
    }