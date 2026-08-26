package com.nuvio.tv.core.error

import android.content.Context
import com.nuvio.tv.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserFacingErrorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.getString(R.string.error_generic) } returns "An error occurred"
        every { context.getString(R.string.account_error_invalid_credentials) } returns "Incorrect email or password."
        every { context.getString(R.string.account_error_no_internet) } returns "No internet connection."
        every { context.getString(R.string.player_error_stream_blocked) } returns "Stream blocked"
        every { context.getString(R.string.player_error_playback_fallback) } returns "Playback error"
        every { context.getString(R.string.stream_error_fetch_failed) } returns "Could not load streams"
        every { context.getString(R.string.account_error_unexpected) } returns "Unexpected error"
    }

    @Test
    fun `looksTechnical detects HTTP and exception patterns`() {
        assertTrue(UserFacingError.looksTechnical("HTTP 403 Forbidden"))
        assertTrue(UserFacingError.looksTechnical("Playback error [ERROR_CODE_IO_BAD_HTTP_STATUS]"))
        assertTrue(UserFacingError.looksTechnical("java.net.UnknownHostException: Unable to resolve host"))
        assertFalse(UserFacingError.looksTechnical("Incorrect email or password."))
    }

    @Test
    fun `fromMessage maps login credentials without leaking raw text`() {
        val result = UserFacingError.fromMessage(
            "Invalid login credentials",
            context,
            UserFacingErrorSituation.Login
        )
        assertEquals("Incorrect email or password.", result)
        assertFalse(result.contains("Invalid login"))
    }

    @Test
    fun `fromMessage maps network host failures`() {
        val result = UserFacingError.fromMessage(
            "Unable to resolve host \"example.com\"",
            context,
            UserFacingErrorSituation.Network
        )
        assertEquals("No internet connection.", result)
    }

    @Test
    fun `sanitizeForDisplay replaces technical text with generic`() {
        val result = UserFacingError.sanitizeForDisplay(
            "HTTP 500 Internal Server Error [ERROR_CODE_IO_NETWORK_CONNECTION_FAILED]",
            context
        )
        assertEquals("An error occurred", result)
    }

    @Test
    fun `sanitizeForDisplay keeps user friendly copy`() {
        val friendly = "Could not load streams"
        val result = UserFacingError.sanitizeForDisplay(friendly, context)
        assertEquals(friendly, result)
    }
}
