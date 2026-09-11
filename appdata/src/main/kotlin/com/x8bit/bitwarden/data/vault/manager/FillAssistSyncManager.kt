package com.x8bit.bitwarden.data.vault.manager

/**
 * A lightweight hook invoked after a successful vault sync.
 *
 * Implementations are provided by the app layer (e.g. delegating to the autofill fill-assist
 * rule manager) so that the shared data layer does not depend on app-only managers.
 */
interface FillAssistSyncManager {
    /**
     * Triggers a background sync of any app-layer data that depends on vault state.
     */
    fun syncIfNecessary()
}