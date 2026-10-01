package com.nuvio.tv.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackFailureReportTest {
    @Test
    fun stripsStreamUrlsFromFailureText() {
        val scrubbed = scrubPlaybackText(
            "Decoder failed https://cdn.example/stream.mkv?token=secret magnet:?xt=urn:btih:abc&tr=http://tracker",
        )

        assertEquals("Decoder failed [url] [url]", scrubbed)
    }

    @Test
    fun reportsOnlyTheStreamHost() {
        assertEquals("cdn.example", playbackReportHost("https://cdn.example:8443/a/b?token=secret"))
        assertNull(playbackReportHost("http://127.0.0.1:11470/stream"))
        assertNull(playbackReportHost("not a url"))
    }

    @Test
    fun groupsByErrorCodeInsteadOfTheUrl() {
        val report = PlaybackFailureReport(
            message = "failed https://cdn.example/video?token=secret",
            engine = "tv-exoplayer",
            host = "cdn.example",
            provider = null,
            streamType = null,
            streamLabel = null,
            contentId = null,
            title = null,
            season = null,
            episode = null,
            errorCode = "ERROR_CODE_DECODER_INIT_FAILED",
            exceptionClass = null,
            causeClass = null,
            causeMessage = null,
            httpStatus = null,
            videoCodec = null,
            mimeType = null,
        )

        assertEquals(
            listOf("playback-failure", "tv-exoplayer", "ERROR_CODE_DECODER_INIT_FAILED"),
            report.fingerprint(),
        )
        assertEquals(
            "Playback failure [ERROR_CODE_DECODER_INIT_FAILED] failed [url]",
            report.summary(),
        )
    }
}
