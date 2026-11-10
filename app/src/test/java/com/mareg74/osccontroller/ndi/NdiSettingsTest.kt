package com.mareg74.osccontroller.ndi

import org.junit.Assert.*
import org.junit.Test

class NdiSettingsTest {
    @Test fun bandwidthTiersMatchNdiSdk() {
        assertFalse(NdiSettings.useHighestBandwidth(0))
        assertFalse(NdiSettings.useHighestBandwidth(49))
        assertTrue(NdiSettings.useHighestBandwidth(50))
        assertTrue(NdiSettings.useHighestBandwidth(100))
    }

    @Test fun defaultsFavourMaxQuality() {
        val d = NdiSettings()
        assertEquals(100, d.quality)
        assertTrue(NdiSettings.useHighestBandwidth(d.quality))
        assertEquals(0, d.maxWidth)
        assertEquals(0, d.maxHeight)
        assertEquals(60, d.maxFps)
        assertFalse(d.audioEnabled)
    }

    @Test fun clampedPreservesNativeAndUnlimited() {
        val s = NdiSettings(quality = -1, maxWidth = 0, maxHeight = 99999, maxFps = 0).clamped()
        assertEquals(0, s.quality)
        assertEquals(0, s.maxWidth)
        assertEquals(4096, s.maxHeight)
        assertEquals(0, s.maxFps)
    }
}
