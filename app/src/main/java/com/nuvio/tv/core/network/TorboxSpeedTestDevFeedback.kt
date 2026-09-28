package com.nuvio.tv.core.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.nuvio.tv.BuildConfig

object TorboxSpeedTestDevFeedback {
    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private fun enabled(): Boolean = BuildConfig.IS_DEBUG_BUILD

    fun onMeasureStarted(manual: Boolean) {
        if (!enabled()) return
        showToast(
            if (manual) {
                "Speedtest TorBox (manual): midiendo ~6s contra CDN latm…"
            } else {
                "Speedtest TorBox: midiendo ~6s contra CDN latm…"
            },
        )
    }

    fun onMeasureSucceeded(mbps: Double, manual: Boolean) {
        if (!enabled()) return
        val formatted = if (mbps >= 100) "%.0f".format(mbps) else "%.1f".format(mbps)
        val prefix = if (manual) "Speedtest TorBox (manual)" else "Speedtest TorBox"
        showToast("$prefix: listo · $formatted Mbps guardado")
    }

    fun onMeasureFailed(manual: Boolean) {
        if (!enabled()) return
        val prefix = if (manual) "Speedtest TorBox (manual)" else "Speedtest TorBox"
        showToast("$prefix: falló · no se cambió el valor guardado")
    }

    fun onSkippedPlaybackActive() {
        if (!enabled()) return
        showToast("Speedtest TorBox: omitido · hay reproducción activa")
    }

    fun onSkippedAlreadyMeasuring() {
        if (!enabled()) return
        showToast("Speedtest TorBox: ya hay una medición en curso")
    }

    private fun showToast(message: String) {
        val context = appContext ?: return
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
