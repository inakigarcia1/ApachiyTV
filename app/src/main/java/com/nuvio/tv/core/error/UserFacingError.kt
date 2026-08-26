package com.nuvio.tv.core.error

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.nuvio.tv.R

enum class UserFacingErrorSituation {
    Login,
    Playback,
    StreamSearch,
    Catalog,
    Search,
    Network,
    Plugin,
    Library,
    Generic
}

object UserFacingError {
    private const val TAG = "UserFacingError"

    fun fromThrowable(
        throwable: Throwable,
        context: Context,
        situation: UserFacingErrorSituation = UserFacingErrorSituation.Generic
    ): String {
        Log.w(TAG, "Mapping ${throwable.javaClass.simpleName}: ${throwable.message}", throwable)
        return fromMessage(throwable.message, context, situation)
    }

    fun fromMessage(
        message: String?,
        context: Context,
        situation: UserFacingErrorSituation = UserFacingErrorSituation.Generic
    ): String {
        val raw = message?.trim().orEmpty()
        if (raw.isBlank()) {
            return context.getString(fallbackResId(situation))
        }
        if (situation == UserFacingErrorSituation.Login) {
            return mapTechnicalMessage(raw, context, situation)
        }
        if (!looksTechnical(raw)) {
            return raw
        }
        return mapTechnicalMessage(raw, context, situation)
    }

    /**
     * Last-line defense for composables that receive a message string directly.
     * Known user-facing copy (aggregate meta/stream errors, localized resources) passes through.
     */
    fun sanitizeForDisplay(message: String?, context: Context): String {
        val trimmed = message?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return context.getString(R.string.error_generic)
        }
        if (looksTechnical(trimmed)) {
            return context.getString(R.string.error_generic)
        }
        return trimmed
    }

    internal fun looksTechnical(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("exception") ||
            lower.contains("error_code_") ||
            lower.startsWith("http ") ||
            lower.contains("unable to resolve host") ||
            lower.contains("no address associated with hostname") ||
            lower.contains("failed to connect") ||
            lower.contains("cleartext communication") ||
            lower.contains("sockettimeoutexception") ||
            lower.contains("javax.") ||
            lower.contains("kotlin.") ||
            lower.contains("java.lang.") ||
            lower.contains("retrofit") ||
            lower.contains("okhttp") ||
            lower.contains("android.") ||
            lower.contains("playback error") && lower.contains("[")
        ) {
            return true
        }
        if (Regex("""\[[A-Z0-9_]+\]""").containsMatchIn(message)) {
            return true
        }
        if (Regex("""\b(4\d{2}|5\d{2})\b""").containsMatchIn(message) &&
            (lower.contains("forbidden") || lower.contains("unauthorized") || lower.contains("not found") || lower.contains("bad request"))
        ) {
            return true
        }
        return false
    }

    @StringRes
    private fun fallbackResId(situation: UserFacingErrorSituation): Int = when (situation) {
        UserFacingErrorSituation.Login -> R.string.account_error_unexpected
        UserFacingErrorSituation.Playback -> R.string.player_error_playback_fallback
        UserFacingErrorSituation.StreamSearch -> R.string.stream_error_fetch_failed
        UserFacingErrorSituation.Catalog -> R.string.error_generic
        UserFacingErrorSituation.Search -> R.string.search_error_failed
        UserFacingErrorSituation.Network -> R.string.network_error_unknown
        UserFacingErrorSituation.Plugin -> R.string.plugin_error_generic
        UserFacingErrorSituation.Library -> R.string.library_error_refresh_failed
        UserFacingErrorSituation.Generic -> R.string.error_generic
    }

    private fun mapTechnicalMessage(
        raw: String,
        context: Context,
        situation: UserFacingErrorSituation
    ): String {
        val message = raw.lowercase()
        val loginResId = when {
            message.contains("incorrect pin") || message.contains("invalid pin") || message.contains("wrong pin") ->
                R.string.account_error_incorrect_pin
            message.contains("expired") && message.contains("sync") ->
                R.string.account_error_sync_code_expired
            message.contains("expired") && situation == UserFacingErrorSituation.Login ->
                R.string.account_error_qr_login_expired
            message.contains("expired") ->
                R.string.account_error_sync_code_expired
            message.contains("invalid") && message.contains("code") ->
                R.string.account_error_invalid_sync_code
            message.contains("not found") && message.contains("sync") ->
                R.string.account_error_sync_code_not_found
            message.contains("already linked") ->
                R.string.account_error_device_already_linked
            message.contains("empty response") ->
                R.string.account_error_generic_retry
            message.contains("invalid login credentials") ->
                R.string.account_error_invalid_credentials
            message.contains("email not confirmed") ->
                R.string.account_error_email_not_confirmed
            message.contains("user already registered") ->
                R.string.account_error_email_already_registered
            message.contains("invalid email") ->
                R.string.account_error_invalid_email
            message.contains("password") && message.contains("short") ->
                R.string.account_error_password_too_short
            message.contains("password") && message.contains("weak") ->
                R.string.account_error_password_too_weak
            message.contains("signup is disabled") ->
                R.string.account_error_signup_disabled
            message.contains("rate limit") || message.contains("too many requests") ->
                R.string.account_error_rate_limited
            message.contains("tv login") && message.contains("invalid") ->
                R.string.account_error_invalid_qr_login
            message.contains("tv login") && message.contains("nonce") ->
                R.string.account_error_qr_login_other_device
            message.contains("start_tv_login_session") || message.contains("gen_random_bytes") ||
                message.contains("invalid tv login redirect") ->
                R.string.account_error_qr_login_failed
            message.contains("invalid device nonce") ->
                R.string.account_error_qr_login_invalid_request
            message.contains("not authenticated") ->
                R.string.account_error_not_authenticated
            else -> null
        }
        if (loginResId != null && (situation == UserFacingErrorSituation.Login || situation == UserFacingErrorSituation.Generic)) {
            return context.getString(loginResId)
        }

        val networkResId = when {
            message.contains("unable to resolve host") || message.contains("no address associated") ->
                R.string.account_error_no_internet
            message.contains("timeout") || message.contains("timed out") ->
                when (situation) {
                    UserFacingErrorSituation.StreamSearch -> R.string.stream_error_detail_addon_timeout
                    UserFacingErrorSituation.Playback -> R.string.player_error_stream_unavailable
                    else -> R.string.account_error_connection_timeout
                }
            message.contains("connection refused") || message.contains("connect failed") || message.contains("failed to connect") ->
                R.string.account_error_connection_refused
            message.contains("cleartext communication") ->
                R.string.stream_error_detail_addon_cleartext_blocked
            else -> null
        }
        if (networkResId != null) {
            return context.getString(networkResId)
        }

        val httpResId = when {
            message.contains("401") || message.contains("403") || message.contains("blocked") ->
                R.string.player_error_stream_blocked
            message.contains("404") || message.contains("not found") && message.contains("stream") ->
                R.string.player_error_stream_removed
            message.contains("410") || message.contains("expired") ->
                R.string.player_error_stream_expired
            message.contains("429") || message.contains("rate limit") ->
                R.string.player_error_stream_rate_limited
            message.contains("500") || message.contains("502") || message.contains("503") || message.contains("504") ->
                R.string.player_error_stream_unavailable
            message.contains("404") || message.contains("could not find") ->
                R.string.account_error_service_unavailable
            message.contains("400") || message.contains("bad request") ->
                R.string.account_error_invalid_request
            else -> null
        }
        if (httpResId != null) {
            return context.getString(httpResId)
        }

        return context.getString(fallbackResId(situation))
    }
}
