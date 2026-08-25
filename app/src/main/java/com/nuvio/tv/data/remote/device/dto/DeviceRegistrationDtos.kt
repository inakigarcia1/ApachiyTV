package com.nuvio.tv.data.remote.device.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
data class DeviceRegistrationRequest(
    @SerialName("installation_id")
    @Json(name = "installationId")
    val installationId: String,
    @SerialName("platform")
    @Json(name = "platform")
    val platform: String,
    @SerialName("app")
    @Json(name = "app")
    val app: String = "apachiy",
    @SerialName("app_version")
    @Json(name = "appVersion")
    val appVersion: String,
    @SerialName("os_version")
    @Json(name = "osVersion")
    val osVersion: String? = null,
    @SerialName("device_model")
    @Json(name = "deviceModel")
    val deviceModel: String? = null
)

@Serializable
@JsonClass(generateAdapter = true)
data class DeviceRegistrationResponse(
    @SerialName("device_id")
    @Json(name = "deviceId")
    val deviceId: Long,
    @SerialName("created")
    @Json(name = "created")
    val created: Boolean = false,
    @SerialName("revoked")
    @Json(name = "revoked")
    val revoked: Boolean = false,
    @SerialName("last_login_at")
    @Json(name = "lastLoginAt")
    val lastLoginAt: String? = null,
    @SerialName("last_seen_at")
    @Json(name = "lastSeenAt")
    val lastSeenAt: String? = null
)

@Serializable
@JsonClass(generateAdapter = true)
data class DeviceSummaryDto(
    @Json(name = "id") val id: Long,
    @Json(name = "installationId") val installationId: String = ""
)

@Serializable
data class DeviceRegistrationError(
    @SerialName("error")   val error: String,
    @SerialName("message") val message: String? = null,
    @SerialName("revoked") val revoked: Boolean = false
)