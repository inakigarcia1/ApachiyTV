package com.nuvio.tv.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApachiyAddonBaseTest {
    @Test
    fun acceptsAddonRootWithoutTrailingSlash() {
        assertTrue(isApachiyAddonBase("http://10.0.2.2:10050/apachiy"))
        assertTrue(isApachiyAddonBase("http://10.0.2.2:10050/apachiy/"))
        assertTrue(isApachiyAddonBase("https://api.example/apachiy?token=1"))
    }

    @Test
    fun rejectsOtherAddons() {
        assertFalse(isApachiyAddonBase("https://api.example/metadata"))
        assertFalse(isApachiyAddonBase("http://10.0.2.2:10050/apachiy/stream/movie/tt0133093.json"))
    }
}
