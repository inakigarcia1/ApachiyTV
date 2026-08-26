package com.nuvio.tv.ui.screens.player

import android.content.Context
import androidx.media3.common.PlaybackException
import com.nuvio.tv.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class PlaybackExceptionDisplayMessageTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.getString(R.string.player_error_stream_blocked) } returns "Stream blocked"
        every { context.getString(R.string.player_error_stream_removed) } returns "Stream removed"
        every { context.getString(R.string.player_error_stream_expired) } returns "Stream expired"
        every { context.getString(R.string.player_error_stream_rate_limited) } returns "Rate limited"
        every { context.getString(R.string.player_error_stream_unavailable) } returns "Stream unavailable"
        every { context.getString(R.string.player_error_source_invalid_content) } returns "Invalid content"
        every { context.getString(R.string.player_error_unsupported_format) } returns "Unsupported format"
        every { context.getString(R.string.player_error_playback_fallback) } returns "Playback error"
    }

    @Test
    fun `toDisplayMessage never surfaces raw HTTP status text`() {
        val exception = PlaybackException(
            "HTTP 403 Forbidden [ERROR_CODE_IO_BAD_HTTP_STATUS]",
            null,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )

        val message = exception.toDisplayMessage(context)

        assertFalse(message.contains("HTTP"))
        assertFalse(message.contains("403"))
        assertFalse(message.contains("ERROR_CODE"))
        assertFalse(message.contains("Forbidden"))
    }

    @Test
    fun `toDisplayMessage maps decoder failures to unsupported format`() {
        val exception = PlaybackException(
            "Decoder init failed",
            null,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )

        val message = exception.toDisplayMessage(context)

        assertEquals("Unsupported format", message)
        assertFalse(message.contains("Decoder"))
    }

    @Test
    fun `toDisplayMessage maps network failures without exception text`() {
        val exception = PlaybackException(
            "Source error",
            null,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        )

        val message = exception.toDisplayMessage(context)

        assertEquals("Stream unavailable", message)
        assertFalse(message.contains("Source error"))
    }
}
