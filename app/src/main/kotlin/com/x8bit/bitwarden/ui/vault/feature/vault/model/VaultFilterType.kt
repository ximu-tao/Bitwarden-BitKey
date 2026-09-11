package com.x8bit.bitwarden.ui.vault.feature.vault.model

import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.util.Text
import com.bitwarden.ui.util.asText
import com.x8bit.bitwarden.data.vault.model.VaultFilterType

/**
 * A short name for the filter.
 */
val VaultFilterType.name: Text
    get() = when (this) {
        VaultFilterType.AllVaults -> BitwardenString.all.asText()
        VaultFilterType.MyVault -> BitwardenString.my_vault.asText()
        is VaultFilterType.OrganizationVault -> organizationName.asText()
    }

/**
 * A potentially longer description of the filter. This may be the same as the owner (i.e. the
 * [name]) when there is no distinction necessary.
 */
val VaultFilterType.description: Text
    get() = when (this) {
        VaultFilterType.AllVaults -> BitwardenString.all_vaults.asText()
        VaultFilterType.MyVault -> BitwardenString.my_vault.asText()
        is VaultFilterType.OrganizationVault -> organizationName.asText()
    }