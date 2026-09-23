package com.nuvio.tv.data.remote.supabase

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarPublicUrlTest {

    private val base = "https://example.com/storage/v1/object/public/avatars"

    @Test
    fun relativePath_appendsToBase() {
        assertEquals(
            "$base/anime/foo.webp",
            resolveAvatarPublicObjectUrl(base, "anime/foo.webp")
        )
    }

    @Test
    fun legacyAvatarsPrefix_isStripped() {
        assertEquals(
            "$base/default-blue.svg",
            resolveAvatarPublicObjectUrl(base, "avatars/default-blue.svg")
        )
    }

    @Test
    fun absoluteUrl_isReturnedUnchanged() {
        val url = "https://cdn.example/avatar.png"
        assertEquals(url, resolveAvatarPublicObjectUrl(base, url))
    }

    @Test
    fun blankBase_returnsPathOnly() {
        assertEquals("anime/foo.webp", resolveAvatarPublicObjectUrl(null, "anime/foo.webp"))
    }
}
