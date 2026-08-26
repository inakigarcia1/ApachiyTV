package com.nuvio.tv.ui.screens.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.nuvio.tv.core.network.IPv4FirstDns
import okhttp3.OkHttpClient
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal object PlayerPlaybackNetworking {
    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val playbackHostnameVerifier = HostnameVerifier { _, _ -> true }

    private val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }
    }

    /**
     * Fallback OkHttpClient equipped with trust-all SSL configuration for self-signed
     * or untrusted local media servers (e.g. self-signed WebDAV / Plex / Jellyfin).
     */
    internal val trustAllPlaybackHttpClient: OkHttpClient by lazy {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 32
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .dns(IPv4FirstDns())
            .eventListenerFactory(PlaybackConnectionEvents)
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier(playbackHostnameVerifier)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Primary OkHttpClient using standard system SSL certificates and full SNI support.
     * Includes an automatic fallback to [trustAllPlaybackHttpClient] if an [SSLException]
     * occurs on self-signed local media servers.
     */
    internal val playbackHttpClient: OkHttpClient by lazy {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 32
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .dns(IPv4FirstDns())
            .eventListenerFactory(PlaybackConnectionEvents)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                try {
                    chain.proceed(request)
                } catch (e: SSLException) {
                    // Fallback to trust-all client if standard system SSL fails (e.g. self-signed local server)
                    trustAllPlaybackHttpClient.newCall(request).execute()
                }
            }
            .build()
    }

    data class RedirectResolution(
        val originalUrl: String,
        val finalUrl: String,
        val redirected: Boolean,
        val httpCode: Int
    )

    /**
     * Follows HTTP redirects for Comet-style `/playback/` proxy URLs and returns the
     * final CDN URL. ExoPlayer then opens the CDN directly, avoiding a mid-open
     * redirect+DNS hang that presents as an infinite buffering spinner.
     */
    fun resolveRedirectedPlaybackUrl(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): RedirectResolution {
        if (!shouldResolvePlaybackRedirects(url)) {
            return RedirectResolution(url, url, redirected = false, httpCode = -1)
        }
        val client = playbackHttpClient.newBuilder()
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .get()
        val sanitized = PlayerMediaSourceFactory.sanitizeHeaders(headers)
        sanitized.forEach { (key, value) ->
            if (!key.equals("Range", ignoreCase = true)) {
                requestBuilder.header(key, value)
            }
        }
        if (sanitized.none { it.key.equals("User-Agent", ignoreCase = true) }) {
            requestBuilder.header("User-Agent", PlayerMediaSourceFactory.DEFAULT_USER_AGENT)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            // Do not drain the body — Torbox CDNs may ignore Range and stream the full file.
            val finalUrl = response.request.url.toString()
            val redirected = !finalUrl.equals(url, ignoreCase = true)
            return RedirectResolution(
                originalUrl = url,
                finalUrl = finalUrl,
                redirected = redirected,
                httpCode = response.code
            )
        }
    }

    fun shouldResolvePlaybackRedirects(url: String): Boolean {
        val path = android.net.Uri.parse(url).path.orEmpty()
        return path.contains("/playback/", ignoreCase = true)
    }

    fun createHttpClient(defaultHeaders: Map<String, String> = emptyMap()): OkHttpClient {
        val builder = playbackHttpClient.newBuilder()
        if (defaultHeaders.any { it.key.equals("Authorization", ignoreCase = true) }) {
            // OkHttp strips the Authorization header on cross-host redirects.
            // WebDAV servers behind reverse proxies commonly redirect to a
            // different host/port, causing auth to be lost. A network
            // interceptor ensures the header is always present on every
            // outgoing request — same behavior as mpv/curl.
            val authValue = defaultHeaders.entries
                .first { it.key.equals("Authorization", ignoreCase = true) }
                .value
            builder.addNetworkInterceptor { chain ->
                val request = chain.request()
                if (request.header("Authorization") == null) {
                    chain.proceed(
                        request.newBuilder()
                            .header("Authorization", authValue)
                            .build()
                    )
                } else {
                    chain.proceed(request)
                }
            }
        }
        return builder
            .let { NuvioExoPlayerPerformanceHelper.applyNetworkOptimizations(it) }
            .build()
    }

    @UnstableApi
    fun createHttpDataSourceFactory(defaultHeaders: Map<String, String> = emptyMap()): DataSource.Factory {
        val client = createHttpClient(defaultHeaders)
        val httpFactory = OkHttpDataSource.Factory(client).apply {
            setDefaultRequestProperties(defaultHeaders)
            if (defaultHeaders.none { it.key.equals("User-Agent", ignoreCase = true) }) {
                setUserAgent(PlayerMediaSourceFactory.DEFAULT_USER_AGENT)
            }
        }
        return LoggingDataSourceFactory(httpFactory, "HTTP")
    }

    @UnstableApi
    fun createDataSourceFactory(
        context: android.content.Context,
        defaultHeaders: Map<String, String> = emptyMap()
    ): DataSource.Factory {
        return DefaultDataSource.Factory(context, createHttpDataSourceFactory(defaultHeaders))
    }

    fun openConnection(
        url: String,
        headers: Map<String, String>,
        method: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        range: String? = null
    ): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = method
            setRequestProperty("User-Agent", headers["User-Agent"] ?: PlayerMediaSourceFactory.DEFAULT_USER_AGENT)
            headers.forEach { (key, value) ->
                if (key.equals("Range", ignoreCase = true)) return@forEach
                if (key.equals("User-Agent", ignoreCase = true)) return@forEach
                setRequestProperty(key, value)
            }
            range?.let { setRequestProperty("Range", it) }
        }
    }
}
