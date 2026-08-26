package com.nuvio.tv.data.remote.device.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AccountMeResponse(
    @Json(name = "user") val user: AccountUserDto
)

@JsonClass(generateAdapter = true)
data class AccountUserDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "username") val username: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "isActive") val isActive: Boolean = true
)
