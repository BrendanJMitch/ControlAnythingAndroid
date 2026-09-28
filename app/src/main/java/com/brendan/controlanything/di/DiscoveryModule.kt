package com.brendan.controlanything.di

import com.brendan.controlanything.data.discovery.DeviceDiscovery
import com.brendan.controlanything.data.discovery.MdnsDeviceDiscovery
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Each transport contributes its discovery mechanism to the set; all of them run side by side. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DiscoveryModule {
    @Binds
    @IntoSet
    abstract fun bindMdnsDeviceDiscovery(impl: MdnsDeviceDiscovery): DeviceDiscovery
}
