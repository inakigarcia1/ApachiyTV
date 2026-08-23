package com.nuvio.tv.data.remote.device

import org.junit.Assert.assertThrows
import org.junit.Test

class AccountProvisionRemovalTest {

    @Test
    fun accountProvisionApiClassRemoved() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.nuvio.tv.data.remote.device.ApachiyAccountApi")
        }
    }

    @Test
    fun accountProvisionerClassRemoved() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.nuvio.tv.data.remote.device.AccountProvisioner")
        }
    }
}
