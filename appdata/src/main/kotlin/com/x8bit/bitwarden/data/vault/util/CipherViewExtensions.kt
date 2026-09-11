package com.x8bit.bitwarden.data.vault.util

import com.bitwarden.vault.CipherView
import com.x8bit.bitwarden.data.platform.util.isActive

/**
 * Returns true when the cipher is not archived, not deleted and contains at least one FIDO 2
 * credential.
 */
val CipherView.isActiveWithFido2Credentials: Boolean
    get() = isActive && !(login?.fido2Credentials.isNullOrEmpty())

/**
 * Returns true when the cipher is not archived, not deleted and contains at least one Password
 * credential.
 */
val CipherView.isActiveWithPasswordCredentials: Boolean
    get() = isActive && !(login?.password.isNullOrEmpty())