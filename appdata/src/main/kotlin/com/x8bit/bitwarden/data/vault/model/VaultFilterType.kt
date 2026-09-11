package com.x8bit.bitwarden.data.vault.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a way to filter on vaults when more than one may be present in a list of vault items.
 *
 * Display labels are provided by the UI layer (see the `displayName`/`displayDescription`
 * extensions in the app's vault feature model package).
 */
sealed class VaultFilterType : Parcelable {
    /**
     * Data from all vaults should be present (i.e. there is no filtering).
     */
    @Parcelize
    data object AllVaults : VaultFilterType()

    /**
     * Only data from the user's personal vault should be present.
     */
    @Parcelize
    data object MyVault : VaultFilterType()

    /**
     * Only data from the organization with the given [organizationId] should be present.
     */
    @Parcelize
    data class OrganizationVault(
        val organizationId: String,
        val organizationName: String,
    ) : VaultFilterType()
}