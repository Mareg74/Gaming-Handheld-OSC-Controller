package com.mareg74.osccontroller.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TactileGridTest {
    @Test fun cellHeightDerivesFromAspect() {
        assertEquals(1f / 16f, TactileGrid.cellWidth(16), 1e-6f)
        // Square canvas → square cells in normalized space too.
        assertEquals(1f / 16f, TactileGrid.cellHeight(1f, 16), 1e-6f)
        assertEquals((16f / 9f) / 16f, TactileGrid.cellHeight(16f / 9f, 16), 1e-5f)
        assertEquals(1f / 8f, TactileGrid.cellWidth(8), 1e-6f)
        assertEquals(1f / 24f, TactileGrid.cellWidth(24), 1e-6f)
        assertEquals(1f / 32f, TactileGrid.cellWidth(32), 1e-6f)
        assertEquals(1f / 64f, TactileGrid.cellWidth(64), 1e-6f)
    }

    @Test fun normalizeColumnsSnapsToChoices() {
        assertEquals(8, TactileGrid.normalizeColumns(8))
        assertEquals(16, TactileGrid.normalizeColumns(16))
        assertEquals(16, TactileGrid.normalizeColumns(15))
        assertEquals(32, TactileGrid.normalizeColumns(30))
        assertEquals(32, TactileGrid.normalizeColumns(32))
        assertEquals(64, TactileGrid.normalizeColumns(64))
        assertEquals(64, TactileGrid.normalizeColumns(50))
        assertEquals(12, TactileGrid.normalizeColumns(11))
    }

    @Test fun snapAndMinOneCell() {
        val aspect = 16f / 9f
        val w = TactileWidget(
            id = "a",
            page = 0,
            kind = TactileKind.TRIGGER,
            x = 0.03f,
            y = 0.04f,
            w = 0.01f,
            h = 0.01f,
        )
        val snapped = TactileGrid.snapWidget(w, aspect, 16)
        assertEquals(TactileGrid.cellWidth(16), snapped.w, 1e-5f)
        assertEquals(TactileGrid.cellHeight(aspect, 16), snapped.h, 1e-5f)
        assertEquals(0f, snapped.x, 1e-5f)
        assertTrue(snapped.y >= 0f)
    }

    @Test fun bumpSizeChangesCells() {
        val aspect = 1f
        val base = TactileWidget("b", 0, TactileKind.FADER, w = 2f / 16f, h = 3f / 16f)
        val wider = TactileGrid.bumpSize(
            base, deltaWCells = 1, deltaHCells = 0, aspectWH = aspect, columns = 16,
        )
        assertEquals(3, TactileGrid.cellsAlong(wider.w, TactileGrid.cellWidth(16)))
        val taller = TactileGrid.bumpSize(
            base, deltaWCells = 0, deltaHCells = 2, aspectWH = aspect, columns = 16,
        )
        assertEquals(5, TactileGrid.cellsAlong(taller.h, TactileGrid.cellHeight(aspect, 16)))
    }

    @Test fun fillHexNormalizeAndDarken() {
        assertEquals("#3DDC84", normalizeFillHex("3ddc84"))
        assertEquals("#3DDC84", normalizeFillHex("#3DDC84"))
        assertEquals("#AABBCC", normalizeFillHex("#ABC"))
        assertNull(normalizeFillHex("nope"))
        val idle = parseFillArgb("#3DDC84")!!
        val down = darkenArgb(idle, 0.55f)
        assertTrue(((down ushr 16) and 0xFF) < ((idle ushr 16) and 0xFF))
    }

    @Test fun hsvRoundTripRough() {
        val hex = hsvToRgbHex(140f, 0.7f, 0.85f)
        val hsv = rgbHexToHsv(hex)!!
        assertEquals(140f, hsv.first, 2f)
        assertEquals(0.7f, hsv.second, 0.05f)
        assertEquals(0.85f, hsv.third, 0.05f)
    }

    @Test fun fillColorAndGridColumnsCodecRoundTrip() {
        val widget = TactileWidget(
            id = "c1",
            page = 1,
            kind = TactileKind.TRIGGER,
            label = "Go",
            x = 0.125f,
            y = 0.1f,
            w = 0.25f,
            h = 0.2f,
            fillColor = "#FB7185",
        )
        val cfg = AppConfig(
            pageCount = 2,
            pageTypes = mapOf(1 to PageType.TACTILE),
            widgets = listOf(widget),
            tactileGridColumns = 24,
        )
        val loaded = ConfigCodec.parse(ConfigCodec.serialize(cfg))
        assertEquals("#FB7185", loaded.widgets.single().fillColor)
        assertEquals("#FB7185", loaded.widgets.single().resolvedFillHex())
        assertEquals(24, loaded.tactileGridColumns)

        // Legacy configs without line 19 default to 16.
        val legacy = "127.0.0.1\n7000\n7001\n0\n\n\n0\nSYSTEM\n1.0\n1.0\n1.0\n1.0\n\n2\n1|TACTILE\nw1|1|TRIGGER|Go|0.1|0.2|0.25|0.2\n0\n1|1|0|||1\nTEAL_SCENE"
        val legacyCfg = ConfigCodec.parse(legacy)
        assertNull(legacyCfg.widgets.single().fillColor)
        assertEquals("#3DDC84", legacyCfg.widgets.single().resolvedFillHex())
        assertEquals(16, legacyCfg.tactileGridColumns)
    }
}
