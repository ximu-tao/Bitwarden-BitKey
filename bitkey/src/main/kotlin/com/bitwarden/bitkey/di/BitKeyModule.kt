package com.bitwarden.bitkey.di

import com.bitwarden.bitkey.connection.BitKeyConnectionManager
import com.bitwarden.bitkey.connection.BluetoothGattBitKeyConnectionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the BitKey BLE transport.
 *
 * Hosts that want to use the BitKey Android library do not need to interact with this module
 * directly: it is auto-installed into the [SingletonComponent] and exposes the only
 * [BitKeyConnectionManager] implementation backed by the platform
 * `android.bluetooth` stack.
 *
 * Tests can override the binding by providing their own [BitKeyConnectionManager] via a
 * `@TestInstallIn(replaces = [BitKeyModule::class])` module.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BitKeyModule {

    /**
     * Binds the production [BluetoothGattBitKeyConnectionManager] as the default
     * [BitKeyConnectionManager] implementation.
     */
    @Binds
    @Singleton
    abstract fun bindBitKeyConnectionManager(
        impl: BluetoothGattBitKeyConnectionManager,
    ): BitKeyConnectionManager
}