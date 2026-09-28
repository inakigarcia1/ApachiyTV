package com.nuvio.tv.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException

private const val PREFS = "torbox_speed_test"
private const val KEY_SPEED = "speed_mbps"
private const val KEY_AT = "measured_at"
private const val KEY_CONN = "connection_type"
private const val KEY_NET_SIG = "network_signature"

@Serializable
private data class TorboxSpeedtestApiResponse(
    val success: Boolean = false,
    val data: List<TorboxSpeedtestEntry> = emptyList(),
)

@Serializable
private data class TorboxSpeedtestEntry(
    val url: String = "",
)

object TorboxSpeedTestHarness {
    private var appContext: Context? = null
    private var httpClient: OkHttpClient = OkHttpClient.Builder().build()
    private val json = Json { ignoreUnknownKeys = true }

    fun initialize(context: Context, client: OkHttpClient? = null) {
        appContext = context.applicationContext
        if (client != null) {
            httpClient = client
        }
    }

    fun readSample(): TorboxSpeedSample? {
        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return null
        val speed = prefs.getFloat(KEY_SPEED, -1f).toDouble()
        val at = prefs.getLong(KEY_AT, 0L)
        if (speed <= 0.0 || at <= 0L) return null
        val conn = prefs.getString(KEY_CONN, null)?.takeIf { it.isNotBlank() }
        return TorboxSpeedSample(speedMbps = speed, measuredAtEpochMs = at, connectionType = conn)
    }

    fun readStoredNetworkSignature(): String? =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getString(KEY_NET_SIG, null)
            ?.takeIf { it.isNotBlank() }

    fun persist(sample: TorboxSpeedSample, networkSignature: String?) {
        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
        prefs.edit()
            .putFloat(KEY_SPEED, sample.speedMbps.toFloat())
            .putLong(KEY_AT, sample.measuredAtEpochMs)
            .putString(KEY_CONN, sample.connectionType)
            .putString(KEY_NET_SIG, networkSignature)
            .apply()
    }

    fun currentNetworkSignature(): String? {
        val context = appContext ?: return null
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        return "$transport:$network"
    }

    suspend fun measure(): TorboxSpeedSample? = withContext(Dispatchers.IO) {
        val listRequest = Request.Builder()
            .url(TorboxSpeedTestPolicy.SPEEDTEST_LIST_URL)
            .get()
            .build()
        val downloadUrl = try {
            httpClient.newCall(listRequest).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                json.decodeFromString<TorboxSpeedtestApiResponse>(body)
                    .data
                    .firstOrNull { it.url.isNotBlank() }
                    ?.url
            }
        } catch (_: IOException) {
            null
        } ?: return@withContext null

        val downloadRequest = Request.Builder().url(downloadUrl).get().build()
        try {
            httpClient.newCall(downloadRequest).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val source = body.source()
                val buffer = ByteArray(64 * 1024)
                val startMs = System.currentTimeMillis()
                var totalBytes = 0L
                while (System.currentTimeMillis() - startMs < TorboxSpeedTestPolicy.MEASURE_DURATION_MS) {
                    val read = source.read(buffer)
                    if (read == -1) break
                    totalBytes += read
                }
                val elapsed = System.currentTimeMillis() - startMs
                val mbps = TorboxSpeedTestPolicy.calculateMbps(totalBytes, elapsed) ?: return@withContext null
                val conn = currentNetworkSignature()?.substringBefore(':')
                TorboxSpeedSample(
                    speedMbps = mbps,
                    measuredAtEpochMs = System.currentTimeMillis(),
                    connectionType = conn,
                )
            }
        } catch (_: IOException) {
            null
        }
    }
}

object TorboxSpeedTestCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile
    private var measuring = false

    fun lastSample(): TorboxSpeedSample? = TorboxSpeedTestHarness.readSample()

    fun scheduleBackgroundCheck() {
        scope.launch { runIfNeeded(force = false) }
    }

    fun runManualMeasure(onComplete: (() -> Unit)? = null) {
        scope.launch {
            runIfNeeded(force = true, manual = true)
            onComplete?.invoke()
        }
    }

    suspend fun runIfNeeded(force: Boolean, manual: Boolean = false) {
        if (PlaybackActiveGuard.isPlaybackActive) {
            if (force) TorboxSpeedTestDevFeedback.onSkippedPlaybackActive()
            return
        }
        if (measuring) {
            if (force) TorboxSpeedTestDevFeedback.onSkippedAlreadyMeasuring()
            return
        }
        val now = System.currentTimeMillis()
        val sample = TorboxSpeedTestHarness.readSample()
        val currentSig = TorboxSpeedTestHarness.currentNetworkSignature()
        val storedSig = TorboxSpeedTestHarness.readStoredNetworkSignature()
        if (!TorboxSpeedTestPolicy.shouldMeasure(now, sample, storedSig, currentSig, force)) {
            return
        }
        measuring = true
        TorboxSpeedTestDevFeedback.onMeasureStarted(manual = force || manual)
        try {
            val measured = TorboxSpeedTestHarness.measure()
            if (measured != null && measured.isValid()) {
                TorboxSpeedTestHarness.persist(measured, currentSig)
                TorboxSpeedTestDevFeedback.onMeasureSucceeded(measured.speedMbps, manual = force || manual)
            } else {
                TorboxSpeedTestDevFeedback.onMeasureFailed(manual = force || manual)
            }
        } finally {
            measuring = false
        }
    }
}
