package com.nuvio.tv.core.network

import android.os.Build
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * The local API advertises HTTPS on localhost. Inside the emulator that host is
 * the guest itself, and the plaintext ports are not TLS. Rewrite loopback to
 * 10.0.2.2 and drop HTTPS on ports that are plain HTTP on the host.
 */
private const val EMULATOR_HOST = "10.0.2.2"
private val TLS_PORTS = setOf(443, 8081, 10051)

fun rewriteLoopbackHostToEmulator(url: String): String {
    val schemeEnd = url.indexOf("://").takeIf { it > 0 } ?: return url
    val hostStart = schemeEnd + 3
    val afterScheme = url.substring(hostStart)
    val hostLength = when {
        afterScheme.startsWith("localhost", ignoreCase = true) -> "localhost".length
        afterScheme.startsWith("127.0.0.1") -> "127.0.0.1".length
        else -> return url
    }
    val afterHost = afterScheme.substring(hostLength)
    if (afterHost.isNotEmpty() && afterHost[0] !in charArrayOf(':', '/', '?', '#')) return url
    return url.substring(0, hostStart) + EMULATOR_HOST + afterHost
}

fun downgradeEmulatorPlaintextUrl(url: String): String {
    if (!url.startsWith("https://", ignoreCase = true)) return url
    val afterScheme = url.substring("https://".length)
    if (!afterScheme.startsWith(EMULATOR_HOST)) return url
    val afterHost = afterScheme.substring(EMULATOR_HOST.length)
    if (afterHost.isNotEmpty() && afterHost[0] !in charArrayOf(':', '/', '?', '#')) return url
    val portDigits = if (afterHost.startsWith(":")) afterHost.drop(1).takeWhile { it.isDigit() } else ""
    val port = when {
        portDigits.isNotEmpty() -> portDigits.toIntOrNull() ?: return url
        else -> 443
    }
    if (port in TLS_PORTS) return url
    return "http://$afterScheme"
}

fun reachEmulatorHostUrl(url: String): String =
    downgradeEmulatorPlaintextUrl(rewriteLoopbackHostToEmulator(url))

fun adaptLocalUrlForCurrentDevice(url: String): String {
    val swapped = if (runningOnAndroidEmulator()) rewriteLoopbackHostToEmulator(url) else url
    return downgradeEmulatorPlaintextUrl(swapped)
}

internal fun runningOnAndroidEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.orEmpty()
    val model = Build.MODEL.orEmpty()
    val hardware = Build.HARDWARE.orEmpty()
    val product = Build.PRODUCT.orEmpty()
    return fingerprint.contains("generic", ignoreCase = true) ||
        fingerprint.contains("emulator", ignoreCase = true) ||
        model.contains("sdk", ignoreCase = true) ||
        model.contains("emulator", ignoreCase = true) ||
        hardware.contains("goldfish", ignoreCase = true) ||
        hardware.contains("ranchu", ignoreCase = true) ||
        product.contains("sdk", ignoreCase = true)
}

internal object EmulatorPlaintextInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val rewritten = adaptLocalUrlForCurrentDevice(request.url.toString())
        if (rewritten == request.url.toString()) return chain.proceed(request)
        return chain.proceed(request.newBuilder().url(rewritten).build())
    }
}

internal fun OkHttpClient.Builder.useEmulatorPlaintext(): OkHttpClient.Builder =
    addInterceptor(EmulatorPlaintextInterceptor)
