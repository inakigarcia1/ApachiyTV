package com.nuvio.tv.core.auth

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class AuthSessionValidationResult {
    VALID,
    INVALID_SESSION,
    EXPIRED_ACCESS_TOKEN,
    TRANSIENT_FAILURE
}

internal data class AuthSessionValidationOutcome(
    val result: AuthSessionValidationResult,
    val error: Throwable? = null
)

internal class AuthSessionValidator(
    private val auth: Auth
) {
    private val mutex = Mutex()
    @Volatile
    private var validatedAccessToken: String? = null

    suspend fun validate(force: Boolean): AuthSessionValidationOutcome =
        mutex.withLock {
            val accessToken = auth.currentAccessTokenOrNull()?.takeIf { it.isNotBlank() }
                ?: return@withLock AuthSessionValidationOutcome(
                    AuthSessionValidationResult.INVALID_SESSION
                )
            if (!force && validatedAccessToken == accessToken) {
                return@withLock AuthSessionValidationOutcome(
                    AuthSessionValidationResult.VALID
                )
            }

            try {
                val remoteUser = auth.retrieveUserForCurrentSession(false)
                if (auth.currentAccessTokenOrNull() != accessToken) {
                    val hasCurrentSession = auth.currentSessionOrNull() != null
                    if (hasCurrentSession) {
                        markCurrentSessionValidated()
                    }
                    return@withLock AuthSessionValidationOutcome(
                        if (hasCurrentSession) {
                            AuthSessionValidationResult.VALID
                        } else {
                            AuthSessionValidationResult.INVALID_SESSION
                        }
                    )
                }
                val localUser = auth.currentUserOrNull()
                if (
                    localUser == null ||
                    remoteUser.id != localUser.id ||
                    remoteUser.email.isNullOrBlank()
                ) {
                    AuthSessionValidationOutcome(
                        AuthSessionValidationResult.INVALID_SESSION
                    )
                } else {
                    validatedAccessToken = accessToken
                    AuthSessionValidationOutcome(
                        AuthSessionValidationResult.VALID
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (auth.currentAccessTokenOrNull() != accessToken) {
                    val hasCurrentSession = auth.currentSessionOrNull() != null
                    if (hasCurrentSession) {
                        markCurrentSessionValidated()
                    }
                    return@withLock AuthSessionValidationOutcome(
                        if (hasCurrentSession) {
                            AuthSessionValidationResult.VALID
                        } else {
                            AuthSessionValidationResult.INVALID_SESSION
                        },
                        error
                    )
                }
                AuthSessionValidationOutcome(
                    result = classifyAuthValidationFailure(
                        statusCode = error.authFailureStatusCode(),
                        message = error.authFailureMessage()
                    ),
                    error = error
                )
            }
        }

    fun markCurrentSessionValidated() {
        validatedAccessToken = auth.currentAccessTokenOrNull()
    }

    fun reset() {
        validatedAccessToken = null
    }
}

private fun Throwable.authFailureStatusCode(): Int? {
    findCause<RestException>()?.statusCode?.let { return it }
    findCause<ClientRequestException>()?.response?.status?.value?.let { return it }
    return null
}

private fun Throwable.authFailureMessage(): String {
    findCause<RestException>()?.let { error ->
        return "${error.error} ${error.description} ${causeMessages()}"
    }
    return causeMessages()
}

private inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return current
        current = current.cause
    }
    return null
}

private fun Throwable.causeMessages(): String {
    val messages = mutableListOf<String>()
    var current: Throwable? = this
    while (current != null) {
        current.message?.let(messages::add)
        current = current.cause
    }
    return messages.joinToString(" ")
}
