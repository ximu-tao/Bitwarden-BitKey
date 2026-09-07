package com.bitwarden.bitkey.model

/**
 * The current state of a BitKey BLE connection.
 *
 * Modelled as a sealed hierarchy so consumers can exhaustively branch on each stage.
 */
sealed class BitKeyConnectionState {

    /** The manager has not attempted to connect yet. */
    data object Idle : BitKeyConnectionState()

    /** A scan is in progress; no device has been selected yet. */
    data class Scanning(val discoveredCount: Int) : BitKeyConnectionState()

    /** The host is performing the BLE GATT handshake (bonding, MTU, service discovery). */
    data class Connecting(val deviceAddress: String, val stage: Stage) :
        BitKeyConnectionState() {

        /**
         * Individual steps within [Connecting]. Used for UI affordances; the manager
         * transitions through them automatically without external input.
         */
        enum class Stage {
            Bonding,
            NegotiatingMtu,
            DiscoveringServices,
            Subscribing,
        }
    }

    /** The GATT link is up and the TX notification subscription is active. */
    data class Connected(val deviceAddress: String, val mtu: Int) : BitKeyConnectionState()

    /** A graceful disconnect or unrecoverable failure has occurred. */
    data class Disconnected(val reason: Reason) : BitKeyConnectionState() {

        enum class Reason {
            /** User requested disconnect. */
            UserRequested,

            /** Remote device closed the GATT link or went out of range. */
            LinkLost,

            /** Attempted operation exceeded its timeout (typically an ACK). */
            Timeout,

            /** Bonding with the peripheral failed or was rejected. */
            BondingFailed,

            /** Service discovery did not find the BitKey service or characteristics. */
            ServiceDiscoveryFailed,

            /**
             * GATT writeDescriptor/setCharacteristicNotification failed or another
             * internal check surfaced an unrecoverable error during the handshake.
             * Surfaced instead of crashing the host process.
             */
            HandshakeFailed,

            /** Generic catch-all for platform errors surfaced through [android.bluetooth]. */
            AdapterError,
        }
    }
}
