package com.bitwarden.bitkey.model

/**
 * A BitKey peripheral observed during a scan.
 *
 * @property address Stable MAC address; pass this to
 * [com.bitwarden.bitkey.connection.BitKeyConnectionManager.connect].
 * @property rssi Most recent received signal strength in dBm.
 * @property advertisedName Value of the Complete Local Name advertising record, if any.
 * @property firstSeenMillis System uptime millis at which the device was first seen
 * during the current scan.
 */
data class BitKeyDiscoveredDevice(
    val address: String,
    val rssi: Int,
    val advertisedName: String?,
    val firstSeenMillis: Long,
)
