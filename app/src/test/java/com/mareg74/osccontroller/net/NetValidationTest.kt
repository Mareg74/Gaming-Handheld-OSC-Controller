package com.mareg74.osccontroller.net

import org.junit.Assert.*
import org.junit.Test

class NetValidationTest {
    @Test fun ipAndPort() {
        assertTrue(NetValidation.isValidIpv4("192.168.0.1"))
        assertFalse(NetValidation.isValidIpv4("302.1.1.1"))
        assertTrue(NetValidation.isValidPort(7000))
        assertFalse(NetValidation.isValidPort(0))
        assertFalse(NetValidation.isValidPort(70000))
    }
}
