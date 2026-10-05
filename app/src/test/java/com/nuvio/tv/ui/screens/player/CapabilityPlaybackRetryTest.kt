package com.nuvio.tv.ui.screens.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityPlaybackRetryTest {
    @Test
    fun `10-bit hevc exceed message is not retried`() {
        val error = PlaybackException(
            "MediaCodecVideoRenderer error, format_supported=NO_EXCEEDS_CAPABILITIES",
            null,
            PlaybackException.ERROR_CODE_DECODING_FAILED
        )
        assertTrue(isHardCapabilityPlaybackError(error))
        assertFalse(isRetryablePlaybackError(error))
    }

    @Test
    fun `plain decode failure stays retryable`() {
        val error = PlaybackException(
            "MediaCodecVideoRenderer error",
            null,
            PlaybackException.ERROR_CODE_DECODING_FAILED
        )
        assertFalse(isHardCapabilityPlaybackError(error))
        assertTrue(isRetryablePlaybackError(error))
    }
}
