package com.mareg74.osccontroller.ndi

import org.junit.Assert.assertFalse
import org.junit.Test

class NdiReceiverFactoryTest {
    @Test
    fun factoryFallsBackWhenSdkUnavailable() {
        val r = NdiReceiverFactory.create()
        assertFalse(r.isAvailable())
    }
}
