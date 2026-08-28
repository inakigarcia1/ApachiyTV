package com.nuvio.tv.core.di

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.network.IPv4FirstDns
import com.nuvio.tv.data.remote.device.ApachiyDeviceApi
import com.nuvio.tv.core.installation.InstallationIdManager
import com.nuvio.tv.core.installation.InstallationIdProvider
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

/**
 * Hilt bindings for the Apachiy device-registration REST client.
 *
 * Uses Moshi because the rest of the network stack (NetworkModule.kt) already
 * configures it; we keep the same parser so error decoding matches the rest
 * of the app.
 */
@Module
@InstallIn(SingletonComponent::class)
object DeviceRegistrationModule {

    @Provides
    @Singleton
    @Named("apachiy")
    fun provideApachiyOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    @Named("apachiy")
    fun provideApachiyRetrofit(
        @Named("apachiy") client: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        val base = BuildConfig.APACHIY_API_BASE_URL.trim().trimEnd('/')
        require(base.isNotBlank()) {
            "APACHIY_API_BASE_URL is empty. Set it in local.properties before building the APK."
        }
        return Retrofit.Builder()
            .baseUrl(if (base.endsWith("/")) base else "$base/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideApachiyDeviceApi(@Named("apachiy") retrofit: Retrofit): ApachiyDeviceApi =
        retrofit.create(ApachiyDeviceApi::class.java)

    @Provides
    @Singleton
    fun provideInstallationIdProvider(manager: InstallationIdManager): InstallationIdProvider = manager

    @Provides
    @Singleton
    @Named("apachiy")
    fun provideApachiyJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}