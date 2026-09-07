package com.nuvio.tv.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionValidationPolicyTest {

    @Test
    fun `generic unauthorized responses try refresh instead of signing out`() {
        assertFalse(isInvalidRemoteSessionResponse(401, "Unauthorized"))
        assertEquals(
            AuthSessionValidationResult.EXPIRED_ACCESS_TOKEN,
            classifyAuthValidationFailure(401, "Unauthorized")
        )
    }

    @Test
    fun `expired access token messages try refresh`() {
        assertTrue(isExpiredAccessTokenMessage("JWT expired"))
        assertTrue(isExpiredAccessTokenMessage("Token is expired"))
        assertTrue(isExpiredAccessTokenMessage("Token has expired"))
        assertFalse(isInvalidRemoteSessionResponse(401, "jwt expired"))
        assertEquals(
            AuthSessionValidationResult.EXPIRED_ACCESS_TOKEN,
            classifyAuthValidationFailure(401, "Token is expired")
        )
        assertTrue(RuntimeException("Token is expired").isJwtExpiredAuthError())
        assertFalse(RuntimeException("Unauthorized").isJwtExpiredAuthError())
    }

    @Test
    fun `invalidates explicit revoked session responses`() {
        assertTrue(isInvalidRemoteSessionResponse(400, "session_not_found"))
        assertTrue(isInvalidRemoteSessionResponse(403, "Invalid session"))
        assertTrue(isInvalidRemoteSessionResponse(404, "User does not exist"))
        assertTrue(isInvalidRemoteSessionResponse(400, "invalid_grant"))
        assertEquals(
            AuthSessionValidationResult.INVALID_SESSION,
            classifyAuthValidationFailure(401, "session_not_found")
        )
    }

    @Test
    fun `keeps cached session for transient failures`() {
        assertFalse(isInvalidRemoteSessionResponse(403, "Forbidden"))
        assertFalse(isInvalidRemoteSessionResponse(403, "Request blocked by proxy"))
        assertFalse(isInvalidRemoteSessionResponse(429, "Too many requests"))
        assertFalse(isInvalidRemoteSessionResponse(503, "Service unavailable"))
        assertFalse(isInvalidRemoteSessionResponse(null, "Unable to resolve host"))
        assertEquals(
            AuthSessionValidationResult.TRANSIENT_FAILURE,
            classifyAuthValidationFailure(503, "Service unavailable")
        )
    }
}
