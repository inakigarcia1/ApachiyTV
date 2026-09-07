package com.nuvio.tv.core.auth

internal fun isInvalidRemoteSessionResponse(
    statusCode: Int?,
    message: String
): Boolean {
    val normalizedMessage = message.lowercase()
    if (isExpiredAccessTokenMessage(normalizedMessage)) return false
    return INVALID_REMOTE_SESSION_MARKERS.any(normalizedMessage::contains)
}

internal fun classifyAuthValidationFailure(
    statusCode: Int?,
    message: String
): AuthSessionValidationResult {
    if (isInvalidRemoteSessionResponse(statusCode, message)) {
        return AuthSessionValidationResult.INVALID_SESSION
    }
    if (isExpiredAccessTokenMessage(message) || statusCode == 401) {
        return AuthSessionValidationResult.EXPIRED_ACCESS_TOKEN
    }
    return AuthSessionValidationResult.TRANSIENT_FAILURE
}

internal fun Throwable.isJwtExpiredAuthError(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (isExpiredAccessTokenMessage(current.message.orEmpty())) return true
        current = current.cause
    }
    return false
}

internal fun isExpiredAccessTokenMessage(message: String): Boolean {
    val normalizedMessage = message.lowercase()
    return EXPIRED_ACCESS_TOKEN_MARKERS.any(normalizedMessage::contains)
}

private val INVALID_REMOTE_SESSION_MARKERS = listOf(
    "session not found",
    "session_not_found",
    "invalid session",
    "user not found",
    "user does not exist",
    "invalid refresh token",
    "refresh token is not valid",
    "refresh token not found",
    "refresh_token_not_found",
    "invalid_grant"
)

private val EXPIRED_ACCESS_TOKEN_MARKERS = listOf(
    "jwt expired",
    "expired jwt",
    "token is expired",
    "token has expired",
    "token expired",
    "expired token"
)
