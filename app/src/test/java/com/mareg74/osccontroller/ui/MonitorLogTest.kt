package com.mareg74.osccontroller.ui

import org.junit.Assert.*
import org.junit.Test

class MonitorLogTest {
    @Test fun outDebouncesTinyFloatDeltas() {
        val log = MonitorLog()
        assertTrue(log.appendOut("/thor/AXIS_LX", listOf(0.5f)))
        assertFalse(log.appendOut("/thor/AXIS_LX", listOf(0.5004f)))
        assertTrue(log.appendOut("/thor/AXIS_LX", listOf(0.52f)))
        assertEquals(2, log.entries().size)
        assertFalse(log.entries()[0].argsText.contains("E", ignoreCase = true))
    }

    @Test fun intsAlwaysLogged() {
        val log = MonitorLog()
        assertTrue(log.appendOut("/thor/BTN_A", listOf(1)))
        assertTrue(log.appendOut("/thor/BTN_A", listOf(0)))
        assertEquals(2, log.entries().count { it.dir == MonitorDir.OUT })
    }

    @Test fun errorsAlwaysMatchFilter() {
        val e = MonitorEntry(1L, 0L, MonitorDir.ERR, "", "boom")
        assertTrue(
            e.matches(
                showIn = false,
                showOut = false,
                mappedOnly = true,
                mappedAddresses = emptySet(),
                selectedAddresses = setOf("/x"),
                contains = "nope",
            ),
        )
    }

    @Test fun chipFilterHidesOtherAddresses() {
        val e = MonitorEntry(1L, 0L, MonitorDir.OUT, "/thor/AXIS_LX", "[0.5]")
        assertFalse(
            e.matches(
                showIn = true,
                showOut = true,
                mappedOnly = false,
                mappedAddresses = emptySet(),
                selectedAddresses = setOf("/thor/BTN_SELECT"),
                contains = "",
            ),
        )
        assertTrue(
            e.matches(
                showIn = true,
                showOut = true,
                mappedOnly = false,
                mappedAddresses = emptySet(),
                selectedAddresses = emptySet(),
                contains = "",
            ),
        )
    }
}
