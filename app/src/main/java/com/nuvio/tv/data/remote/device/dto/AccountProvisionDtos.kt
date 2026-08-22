package com.nuvio.tv.data.remote.device.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountProvisionRequest(
    @SerialName("password") val password: String
)

@Serializable
data class AccountProvisionResponse(
    @SerialName("userId") val userId: String,
    @SerialName("created") val created: Boolean = false,
    @SerialName("linked") val linked: Boolean = false
)
