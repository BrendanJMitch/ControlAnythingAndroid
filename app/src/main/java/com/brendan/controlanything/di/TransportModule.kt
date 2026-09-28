package com.brendan.controlanything.di

import com.brendan.controlanything.data.device.DeviceRepository
import com.brendan.controlanything.data.device.DeviceRepositoryImpl
import com.brendan.controlanything.data.pubsub.DefaultTransportFactory
import com.brendan.controlanything.data.pubsub.PubSubClient
import com.brendan.controlanything.data.pubsub.PubSubClientImpl
import com.brendan.controlanything.data.pubsub.TransportFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class TransportModule {
    @Binds
    abstract fun bindTransportFactory(impl: DefaultTransportFactory): TransportFactory

    @Binds
    @Singleton
    abstract fun bindPubSubClient(impl: PubSubClientImpl): PubSubClient

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    companion object {
        /** A missed pong fails the socket, so a dead link is noticed within roughly two intervals. */
        private const val PING_INTERVAL_SECONDS = 3L
        private const val CONNECT_TIMEOUT_SECONDS = 5L

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
