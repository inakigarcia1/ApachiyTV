package com.nuvio.tv.core.network

import android.content.Context
import com.nuvio.tv.R
import com.nuvio.tv.core.error.UserFacingError
import com.nuvio.tv.core.error.UserFacingErrorSituation
import kotlinx.coroutines.CancellationException
import retrofit2.Response

suspend fun <T> safeApiCall(
    context: Context,
    apiCall: suspend () -> Response<T>
): NetworkResult<T> {
    return try {
        val response = apiCall()
        if (response.isSuccessful) {
            response.body()?.let {
                NetworkResult.Success(it)
            } ?: NetworkResult.Error(context.getString(R.string.network_error_empty_response_body))
        } else {
            NetworkResult.Error(
                message = context.getString(R.string.network_error_unknown),
                code = response.code()
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        NetworkResult.Error(
            UserFacingError.fromThrowable(e, context, UserFacingErrorSituation.Network)
        )
    }
}
