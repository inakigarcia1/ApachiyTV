package com.nuvio.tv.data.remote.supabase

/**
 * Builds a public Storage URL for an object in the `avatars` bucket.
 *
 * [storagePath] is relative to the bucket per schema; legacy rows may prefix `avatars/`.
 */
fun resolveAvatarPublicObjectUrl(
    avatarPublicBaseUrl: String?,
    storagePath: String
): String {
    val trimmedPath = storagePath.trim()
    if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
        return trimmedPath
    }
    val baseUrl = avatarPublicBaseUrl.orEmpty().trim().trimEnd('/')
    if (baseUrl.isEmpty()) return trimmedPath
    val objectKey = trimmedPath.removePrefix("avatars/").trimStart('/')
    return "$baseUrl/$objectKey"
}
