package com.mareg74.osccontroller.ndi

import com.mareg74.osccontroller.config.MemoryPrefs
import org.junit.Assert.*
import org.junit.Test

class NdiPrefsTest {
    @Test fun defaultsThenRoundTrip() {
        val prefs = MemoryPrefs()
        val store = NdiPrefs(prefs)
        val d = store.load()
        assertNull(d.lastSourceUrl)
        assertEquals(100, d.settings.quality)
        assertEquals(0, d.settings.maxWidth)
        assertEquals(0, d.settings.maxHeight)
        assertEquals(60, d.settings.maxFps)
        assertFalse(d.settings.audioEnabled)

        store.save(
            NdiPrefsData(
                lastSourceUrl = "ndi://PC (PGM)",
                lastSourceName = "PC (PGM)",
                settings = NdiSettings(quality = 25, maxWidth = 960, maxHeight = 540, maxFps = 15, audioEnabled = true),
            ),
        )
        val loaded = store.load()
        assertEquals("ndi://PC (PGM)", loaded.lastSourceUrl)
        assertEquals("PC (PGM)", loaded.lastSourceName)
        assertEquals(25, loaded.settings.quality)
        assertEquals(960, loaded.settings.maxWidth)
        assertEquals(540, loaded.settings.maxHeight)
        assertEquals(15, loaded.settings.maxFps)
        assertTrue(loaded.settings.audioEnabled)
    }

    @Test fun clampsOutOfRangeAndAllowsNativeZero() {
        val store = NdiPrefs(MemoryPrefs())
        store.save(
            NdiPrefsData(
                settings = NdiSettings(quality = 999, maxWidth = 10, maxHeight = 10, maxFps = 0, audioEnabled = false),
            ),
        )
        val s = store.load().settings
        assertEquals(100, s.quality)
        assertEquals(160, s.maxWidth)
        assertEquals(160, s.maxHeight)
        assertEquals(0, s.maxFps) // unlimited

        store.save(NdiPrefsData(settings = NdiSettings(maxWidth = 0, maxHeight = 0)))
        val native = store.load().settings
        assertEquals(0, native.maxWidth)
        assertEquals(0, native.maxHeight)
    }
}
