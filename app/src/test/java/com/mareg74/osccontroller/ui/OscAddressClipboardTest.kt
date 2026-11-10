package com.mareg74.osccontroller.ui

import org.junit.Assert.*
import org.junit.Test

class OscAddressClipboardTest {
    @Test fun canCopyInOutWithAddress() {
        assertTrue(OscAddressClipboard.canCopy(MonitorDir.IN, "/composition/layers/1"))
        assertTrue(OscAddressClipboard.canCopy(MonitorDir.OUT, "/composition/layers/1"))
    }

    @Test fun cannotCopyErrOrBlank() {
        assertFalse(OscAddressClipboard.canCopy(MonitorDir.ERR, "/x"))
        assertFalse(OscAddressClipboard.canCopy(MonitorDir.IN, ""))
        assertFalse(OscAddressClipboard.canCopy(MonitorDir.OUT, ""))
    }

    @Test fun dedupSkipsSameAddress() {
        assertFalse(OscAddressClipboard.shouldSet("/a", "/a"))
        assertTrue(OscAddressClipboard.shouldSet(null, "/a"))
        assertTrue(OscAddressClipboard.shouldSet("/b", "/a"))
    }
}
