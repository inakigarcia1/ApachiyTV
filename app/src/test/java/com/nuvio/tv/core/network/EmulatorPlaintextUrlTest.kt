package com.nuvio.tv.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class EmulatorPlaintextUrlTest {
    @Test
    fun downgradesPlainHttpPortsOnTheEmulatorHost() {
        assertEquals(
            "http://10.0.2.2:10050/subtitles/download/abc",
            downgradeEmulatorPlaintextUrl("https://10.0.2.2:10050/subtitles/download/abc"),
        )
    }

    @Test
    fun keepsPortsThatActuallyUseTls() {
        assertEquals(
            "https://10.0.2.2:10051/apachiy/subtitles/proxy/token",
            downgradeEmulatorPlaintextUrl("https://10.0.2.2:10051/apachiy/subtitles/proxy/token"),
        )
    }

    @Test
    fun rewritesLoopbackProxyUrlsOntoTheEmulatorHost() {
        assertEquals(
            "http://10.0.2.2:10050/apachiy/subtitles/proxy/token?t=abc",
            reachEmulatorHostUrl("https://localhost:10050/apachiy/subtitles/proxy/token?t=abc"),
        )
        assertEquals(
            "http://10.0.2.2:10050/apachiy/subtitles/proxy/token",
            reachEmulatorHostUrl("https://127.0.0.1:10050/apachiy/subtitles/proxy/token"),
        )
        assertEquals(
            "https://10.0.2.2:10051/apachiy/subtitles/proxy/token",
            reachEmulatorHostUrl("https://localhost:10051/apachiy/subtitles/proxy/token"),
        )
        assertEquals(
            "https://localhost.example.com:10050/api",
            reachEmulatorHostUrl("https://localhost.example.com:10050/api"),
        )
    }

    @Test
    fun leavesHttpAndPublicHostsAlone() {
        assertEquals(
            "http://10.0.2.2:10050/subtitles/download/abc",
            downgradeEmulatorPlaintextUrl("http://10.0.2.2:10050/subtitles/download/abc"),
        )
        assertEquals(
            "https://api.apachiy.org/subtitles/download/abc",
            downgradeEmulatorPlaintextUrl("https://api.apachiy.org/subtitles/download/abc"),
        )
    }
}
