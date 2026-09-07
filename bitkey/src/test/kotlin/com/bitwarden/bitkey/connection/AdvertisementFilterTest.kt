package com.bitwarden.bitkey.connection

import com.bitwarden.bitkey.protocol.BitKeyConstants
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdvertisementFilterTest {

    private val otherServiceUuid: UUID =
        UUID.fromString("0000AAAA-0000-1000-8000-00805F9B34FB")

    @Test
    fun `name exactly equal to BitKey is accepted`() {
        assertTrue(
            isBitKeyAdvertisement(
                advertisedName = "BitKey",
                serviceUuids = emptyList(),
            ),
        )
    }

    @Test
    fun `name with BitKey dash variant is accepted`() {
        assertTrue(
            isBitKeyAdvertisement(
                advertisedName = "BitKey-Pro",
                serviceUuids = emptyList(),
            ),
        )
    }

    @Test
    fun `name with BitKey no-separator variant is accepted`() {
        assertTrue(
            isBitKeyAdvertisement(
                advertisedName = "BitKeyV2",
                serviceUuids = emptyList(),
            ),
        )
    }

    @Test
    fun `unrelated device name is rejected`() {
        assertFalse(
            isBitKeyAdvertisement(
                advertisedName = "MyKeyboard",
                serviceUuids = emptyList(),
            ),
        )
    }

    @Test
    fun `name that contains BitKey as a substring but does not start with it is rejected`() {
        // Defends against accidental substring matches like "MiBitKey" or
        // "NotABitKey". The filter must use `startsWith`, not `contains`,
        // so only BitKey-prefixed devices surface in the scan list.
        assertFalse(
            isBitKeyAdvertisement(
                advertisedName = "MiBitKey",
                serviceUuids = emptyList(),
            ),
        )
    }

    @Test
    fun `null name with matching service UUID is accepted as fallback`() {
        assertTrue(
            isBitKeyAdvertisement(
                advertisedName = null,
                serviceUuids = listOf(BitKeyConstants.SERVICE_UUID),
            ),
        )
    }

    @Test
    fun `null name without matching service UUID is rejected`() {
        assertFalse(
            isBitKeyAdvertisement(
                advertisedName = null,
                serviceUuids = listOf(otherServiceUuid),
            ),
        )
    }

    @Test
    fun `null name with empty service UUID list is rejected`() {
        // Sanity check: an empty advertisement must not slip through.
        assertFalse(
            isBitKeyAdvertisement(
                advertisedName = null,
                serviceUuids = emptyList(),
            ),
        )
    }
}
