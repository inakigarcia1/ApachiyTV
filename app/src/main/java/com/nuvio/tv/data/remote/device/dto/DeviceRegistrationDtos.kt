package com.nuvio.tv.data.remote.device.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegistrationRequest(
    @SerialName("installation_id") val installationId: String,
    @SerialName("platform")        val platform: String,
    @SerialName("app")             val app: String = "apachiy",
    @SerialName("app_version")     val appVersion: String,
    @SerialName("os_version")      val osVersion: String? = null,
    @SerialName("device_model")    val deviceModel: String? = null
)

@Serializable
data class DeviceRegistrationResponse(
    @SerialName("device_id")  val deviceId: Long,
    @SerialName("created")    val created: Boolean = false,
    @SerialName("revoked")    val revoked: Boolean = false,
    @SerialName("last_login_at") val lastLoginAt: String? = null
)

@Serializable
data class DeviceRegistrationError(
    @SerialName("error")   val error: String,
    @SerialName("message") val message: String? = null,
    @SerialName("revoked") val revoked: Boolean = false
)