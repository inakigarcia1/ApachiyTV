package com.nuvio.tv.core.di

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.device.ApachiyDeviceApi
import com.nuvio.tv.data.remote.device.DeviceRegistrar
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
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
    fun provideApachiyOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideApachiyRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit {
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
    fun provideApachiyDeviceApi(retrofit: Retrofit): ApachiyDeviceApi =
        retrofit.create(ApachiyDeviceApi::class.java)

    @Provides
    @Singleton
    fun provideApachiyJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideDeviceRegistrar(
        authManager: com.nuvio.tv.core.auth.AuthManager,
        supabaseAuth: Auth,
        installationIdProvider: com.nuvio.tv.core.installation.InstallationIdProvider,
        apachiyDeviceApi: ApachiyDeviceApi,
        json: Json
    ): DeviceRegistrar = DeviceRegistrar(
        authManager = authManager,
        supabaseAuth = supabaseAuth,
        installationIdProvider = installationIdProvider,
        apachiyDeviceApi = apachiyDeviceApi,
        json = json
    )
}