package com.nuvio.tv.domain.model

data class UserProfile(
    val id: Int,
    val name: String,
    val avatarColorHex: String,
    val usesPrimaryAddons: Boolean = true,
    val usesPrimaryPlugins: Boolean = false,
    val avatarId: String? = null,
    val avatarUrl: String? = null
) {
    val isPrimary: Boolean get() = id == 1
}
