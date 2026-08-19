package com.nuvio.tv.core.auth

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSessionRegistrationTest {

    @Test
    fun `builds Apachiy TV client registration payload`() {
        val params = buildDeviceRegistrationParams(
            installationId = "550e8400-e29b-41d4-a716-446655440000",
            clientVersion = "0.8.6-beta",
            metadata = DeviceClientMetadata(
                deviceName = "Living Room TV",
                platform = "Android TV 14"
            )
        )

        assertEquals("550e8400-e29b-41d4-a716-446655440000", params.getValue("p_installation_id").jsonPrimitive.content)
        assertEquals("Apachiy TV", params.getValue("p_client_name").jsonPrimitive.content)
        assertEquals("0.8.6-beta", params.getValue("p_client_version").jsonPrimitive.content)
        assertEquals("Android TV 14", params.getValue("p_platform").jsonPrimitive.content)
        assertEquals("Living Room TV", params.getValue("p_device_name").jsonPrimitive.content)
    }

    @Test
    fun `prefers configured TV name and falls back to hardware name`() {
        assertEquals(
            "Living Room TV",
            selectDeviceName(
                configuredName = " Living Room TV ",
                manufacturer = "Sony",
                model = "BRAVIA 4K",
                fallback = "Android TV"
            )
        )
        assertEquals(
            "Sony BRAVIA 4K",
            selectDeviceName(
                configuredName = null,
                manufacturer = "Sony",
                model = "BRAVIA 4K",
                fallback = "Android TV"
            )
        )
        assertEquals(
            "Google TV Streamer",
            selectDeviceName(
                configuredName = "",
                manufacturer = "Google",
                model = "Google TV Streamer",
                fallback = "Android TV"
            )
        )
    }
}