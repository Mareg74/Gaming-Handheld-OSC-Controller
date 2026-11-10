package com.mareg74.osccontroller.ui

import org.junit.Assert.*
import org.junit.Test

class OscDisplayFormatTest {
    @Test fun twoDecimalsNeverScientific() {
        val s = OscDisplayFormat.formatFloat(0.0004f)
        assertFalse(s.contains("E", ignoreCase = true))
        assertEquals("0.00", s)
        assertEquals("0.42", OscDisplayFormat.formatFloat(0.42f))
        assertEquals("1.00", OscDisplayFormat.formatFloat(1f))
        assertEquals("-0.25", OscDisplayFormat.formatFloat(-0.25f))
    }

    @Test fun formatArgsList() {
        assertEquals("[0.42, 1]", OscDisplayFormat.formatArgs(listOf(0.42f, 1)))
    }

    @Test fun floatNearDebounce() {
        assertTrue(OscDisplayFormat.floatNear(0.5f, 0.5004f))
        assertFalse(OscDisplayFormat.floatNear(0.5f, 0.502f))
    }
}
