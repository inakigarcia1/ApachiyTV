package com.nuvio.tv.ui.screens.player.embedded

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nuvio.tv.core.network.useEmulatorPlaintext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

internal class EmbeddedSubtitleExtractor(
    private val httpClient: OkHttpClient = defaultClient,
) {
    suspend fun extract(
        sourceUrl: String,
        headers: Map<String, String>,
    ): EmbeddedExtractResult? = withContext(Dispatchers.IO) {
        val prefix = readMediaPrefix(sourceUrl, headers, PREFIX_BYTES) ?: return@withContext null
        val tracks = when {
            looksLikeMkv(prefix) -> MkvTextSubtitleParser.parse(prefix)
            looksLikeMp4(prefix) -> Mp4TextSubtitleParser.parse(prefix)
            else -> emptyList()
        }
        if (tracks.isEmpty()) return@withContext null
        EmbeddedExtractResult(
            hasEmbeddedSpanish = hasEmbeddedSpanishTextTrack(tracks),
            reference = null,
        )
    }

    private fun readMediaPrefix(
        sourceUrl: String,
        headers: Map<String, String>,
        maxBytes: Int,
    ): ByteArray? {
        val trimmed = sourceUrl.trim()
        if (trimmed.startsWith("file:", ignoreCase = true)) {
            val path = trimmed.removePrefix("file://").removePrefix("file:")
            return readLocalFilePrefix(path, maxBytes)
        }
        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            return readLocalFilePrefix(trimmed, maxBytes)
        }
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return readLocalFilePrefix(trimmed, maxBytes)
        }
        return runCatching {
            val builder = Request.Builder().url(trimmed)
            headers.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    builder.header(key, value)
                }
            }
            builder.header("Range", "bytes=0-${maxBytes - 1}")
            httpClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) return@use null
                response.body?.byteStream()?.use { stream ->
                    readAtMostBytes(stream, maxBytes)
                }
            }
        }.getOrNull()
    }

    private fun readLocalFilePrefix(path: String, maxBytes: Int): ByteArray? {
        val file = File(path)
        if (!file.isFile) return null
        return file.inputStream().use { stream -> readAtMostBytes(stream, maxBytes) }
    }

    private fun readAtMostBytes(stream: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
        val buffer = ByteArray(8 * 1024)
        var remaining = maxBytes
        while (remaining > 0) {
            val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            out.write(buffer, 0, read)
            remaining -= read
        }
        return out.toByteArray()
    }

    private fun looksLikeMkv(data: ByteArray): Boolean =
        data.size >= 4 &&
            (data[0].toInt() and 0xFF) == 0x1A &&
            (data[1].toInt() and 0xFF) == 0x45 &&
            (data[2].toInt() and 0xFF) == 0xDF &&
            (data[3].toInt() and 0xFF) == 0xA3

    private fun looksLikeMp4(data: ByteArray): Boolean {
        if (data.size < 8) return false
        val type = data.decodeToString(4, 8)
        return type == "ftyp" || type == "moov" || type == "mdat"
    }

    companion object {
        private const val PREFIX_BYTES = 16 * 1024 * 1024
        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .useEmulatorPlaintext()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
