package com.mareg74.osccontroller.config

import org.junit.Assert.*
import org.junit.Test

class ConfigStoreTest {
    @Test fun defaultsThenRoundTrip() {
        val store = ConfigStore(MemoryPrefs())
        val d = store.load()
        assertEquals("127.0.0.1", d.targetIp)
        assertEquals(7000, d.targetPort)
        assertEquals(7001, d.listenPort)
        val next = d.copy(
            targetIp = "192.168.1.10",
            mappings = listOf(
                ControlMapping("BTN_A", "/composition/columns/1/connect", ArgKind.MOMENTARY, page = 1),
            ),
            buttonNotes = mapOf(noteStorageKey(0, "BTN_A") to "A = STROBE")
        )
        store.save(next)
        val loaded = store.load()
        assertEquals("192.168.1.10", loaded.targetIp)
        assertEquals("/composition/columns/1/connect", loaded.mappings.single().address)
        assertEquals(1, loaded.mappings.single().page)
        assertEquals("A = STROBE", loaded.noteFor(0, "BTN_A"))
        assertEquals("", loaded.noteFor(1, "BTN_A"))
    }

    @Test fun legacyMappingWithoutPageDefaultsToZero() {
        val prefs = MemoryPrefs()
        prefs.putString(
            ConfigStore.KEY,
            "127.0.0.1\n7000\n7001\n0\nBTN_A|/foo|BANG\n"
        )
        val store = ConfigStore(prefs)
        val m = store.load().mappings.single()
        assertEquals(0, m.page)
        assertEquals(ArgKind.MOMENTARY, m.argKind) // legacy BANG
    }

    @Test fun corruptConfigRecoversAndPersistsDefaults() {
        val prefs = MemoryPrefs()
        prefs.putString(ConfigStore.KEY, "not\nvalid\nconfig\nat\nall")
        val store = ConfigStore(prefs)
        val cfg = store.load()
        assertTrue(store.lastLoadRecovered)
        assertEquals("127.0.0.1", cfg.targetIp)
        assertEquals("127.0.0.1", store.load().targetIp)
        assertFalse(store.lastLoadRecovered)
    }

    @Test fun resetSoftDefaults() {
        val prefs = MemoryPrefs()
        prefs.putString("other_key", "keep")
        val store = ConfigStore(prefs)
        store.save(store.load().copy(targetIp = "9.9.9.9"))
        store.resetToDefaults()
        assertEquals("127.0.0.1", store.load().targetIp)
        assertEquals("keep", prefs.getString("other_key", ""))
    }

    @Test fun forceSingleDisplayRoundTrip() {
        val store = ConfigStore(MemoryPrefs())
        store.save(store.load().copy(forceSingleDisplay = true))
        assertTrue(store.load().forceSingleDisplay)
        store.save(store.load().copy(forceSingleDisplay = false))
        assertFalse(store.load().forceSingleDisplay)
    }

    @Test fun legacyConfigWithoutForceSingleDefaultsFalse() {
        val prefs = MemoryPrefs()
        prefs.putString(
            ConfigStore.KEY,
            "127.0.0.1\n7000\n7001\n0\nBTN_A|/foo|BANG\n"
        )
        assertFalse(ConfigStore(prefs).load().forceSingleDisplay)
    }

    @Test fun migratesLegacyTriggerIds() {
        val prefs = MemoryPrefs()
        prefs.putString(
            ConfigStore.KEY,
            "127.0.0.1\n7000\n7001\n0\nBTN_L2|/a|FLOAT|0;BTN_R2|/b|FLOAT|0\nBTN_L2|left\n0"
        )
        val cfg = ConfigStore(prefs).load()
        assertEquals(setOf("AXIS_L2", "AXIS_R2"), cfg.mappings.map { it.controlId }.toSet())
        assertEquals("left", cfg.noteFor(0, "AXIS_L2"))
    }

    @Test fun legacyButtonNotesMigrateToPageZero() {
        val prefs = MemoryPrefs()
        prefs.putString(
            ConfigStore.KEY,
            "127.0.0.1\n7000\n7001\n0\n\nBTN_A|STROBE\n0"
        )
        val cfg = ConfigStore(prefs).load()
        assertEquals("STROBE", cfg.noteFor(0, "BTN_A"))
        assertEquals("", cfg.noteFor(1, "BTN_A"))
    }

    @Test fun buttonNotesArePerPageAndNewPageEmpty() {
        var cfg = AppConfig(
            pageCount = 1,
            buttonNotes = mapOf(noteStorageKey(0, "BTN_A") to "clip1"),
        )
        cfg = cfg.addPage()
        assertEquals(1, cfg.activePage)
        assertEquals("clip1", cfg.noteFor(0, "BTN_A"))
        assertEquals("", cfg.noteFor(1, "BTN_A"))
        cfg = cfg.withNote(1, "BTN_A", "other")
        assertEquals("clip1", cfg.noteFor(0, "BTN_A"))
        assertEquals("other", cfg.noteFor(1, "BTN_A"))
        cfg = cfg.copyPage(0)
        assertEquals("clip1", cfg.noteFor(2, "BTN_A"))
    }

    @Test fun stickCalibrationRoundTrip() {
        val store = ConfigStore(MemoryPrefs())
        store.save(store.load().copy(stickLeftFull = 0.82f, stickRightFull = 0.76f))
        val loaded = store.load()
        assertEquals(0.82f, loaded.stickLeftFull, 0.0001f)
        assertEquals(0.76f, loaded.stickRightFull, 0.0001f)
    }

    @Test fun pageNamesRoundTripAndLabel() {
        val store = ConfigStore(MemoryPrefs())
        store.save(store.load().copy(pageNames = mapOf(0 to "Intro", 2 to "Strobe"), pageCount = 3))
        val loaded = store.load()
        assertEquals("Intro", loaded.pageNames[0])
        assertEquals("Strobe", loaded.pageNames[2])
        assertEquals(3, loaded.pageCount)
        assertEquals("Intro", loaded.pageLabel(0))
        assertEquals("Page 1", loaded.pageLabel(1))
        assertEquals("Strobe", loaded.pageLabel(2))
    }

    @Test fun pageAddCopyDelete() {
        var cfg = AppConfig(pageCount = 1, pageNames = mapOf(0 to "A"))
        cfg = cfg.addPage()
        assertEquals(2, cfg.pageCount)
        assertEquals(1, cfg.activePage)
        cfg = cfg.copy(
            mappings = listOf(ControlMapping("BTN_A", "/a", ArgKind.MOMENTARY, page = 0)),
            activePage = 0,
        )
        cfg = cfg.copyPage(0)
        assertEquals(3, cfg.pageCount)
        assertEquals(2, cfg.activePage)
        assertEquals(1, cfg.mappings.count { it.page == 2 })
        assertTrue(cfg.pageNames[2]!!.contains("copie"))
        cfg = cfg.deletePage(2)
        assertEquals(2, cfg.pageCount)
        assertEquals(0, cfg.mappings.count { it.page == 2 })
        cfg = cfg.deletePage(0)
        assertEquals(1, cfg.pageCount)
        assertEquals(0, cfg.activePage)
        assertEquals(cfg, cfg.deletePage(0)) // no-op
    }

    @Test fun floatValueRoundTripAndLegacyDefault() {
        val store = ConfigStore(MemoryPrefs())
        store.save(store.load().copy(
            mappings = listOf(
                ControlMapping("BTN_A", "/opacity", ArgKind.FLOAT, page = 0, floatValue = 0.33f),
            )
        ))
        assertEquals(0.33f, store.load().mappings.single().floatValue, 0.0001f)

        val prefs = MemoryPrefs()
        prefs.putString(
            ConfigStore.KEY,
            "127.0.0.1\n7000\n7001\n0\nBTN_B|/foo|FLOAT|0\n"
        )
        assertEquals(1f, ConfigStore(prefs).load().mappings.single().floatValue, 0.0001f)
    }

    @Test fun tactilePageTypeAndWidgetsRoundTrip() {
        val store = ConfigStore(MemoryPrefs())
        var cfg = store.load().addPage("Perso", PageType.TACTILE)
        val widget = TactileWidget(
            id = "w1",
            page = cfg.activePage,
            kind = TactileKind.TRIGGER,
            label = "Go",
            x = 0.1f,
            y = 0.2f,
            w = 0.25f,
            h = 0.2f,
        )
        cfg = cfg.upsertWidget(widget).copy(
            mappings = listOf(
                ControlMapping("w1", "/composition/columns/1/connect", ArgKind.MOMENTARY, page = cfg.activePage),
            ),
        )
        store.save(cfg)
        val loaded = store.load()
        assertEquals(PageType.TACTILE, loaded.pageType(1))
        assertEquals(PageType.PHYSICAL, loaded.pageType(0))
        assertEquals(1, loaded.widgetsOn(1).size)
        assertEquals("Go", loaded.widgetsOn(1).single().label)
        assertEquals(listOf(1), loaded.tactilePageIndices())

        val copied = loaded.copyPage(1)
        assertEquals(PageType.TACTILE, copied.pageType(2))
        assertEquals(1, copied.widgetsOn(2).size)
        assertTrue(copied.widgetsOn(2).single().id != "w1")
        assertEquals(1, copied.mappings.count { it.page == 2 })
    }

    @Test fun bottomPlayPageIndependentRoundTrip() {
        val store = ConfigStore(MemoryPrefs())
        store.save(
            store.load().copy(
                pageCount = 3,
                activePage = 0,
                bottomPlayPage = 2,
            ),
        )
        val loaded = store.load()
        assertEquals(0, loaded.activePage)
        assertEquals(2, loaded.bottomPlayPage)
    }

    @Test fun monitorLineSizeRoundTrip() {
        val store = ConfigStore(MemoryPrefs())
        store.save(store.load().copy(monitorLineSize = 3))
        assertEquals(3, store.load().monitorLineSize)
        assertEquals(1, ConfigCodec.parse(ConfigCodec.serialize(AppConfig())).monitorLineSize)
    }

    @Test fun colorPaletteRoundTripAndLegacyDefault() {
        val store = ConfigStore(MemoryPrefs())
        store.save(store.load().copy(colorPalette = ColorPalette.LIME_CLUB))
        assertEquals(ColorPalette.LIME_CLUB, store.load().colorPalette)
        assertEquals(ColorPalette.TEAL_SCENE, ConfigCodec.parse(ConfigCodec.serialize(AppConfig())).colorPalette)
        // Legacy configs without line 18 fall back to Teal scène.
        val legacy = ConfigCodec.serialize(AppConfig()).lineSequence().take(18).joinToString("\n")
        assertEquals(ColorPalette.TEAL_SCENE, ConfigCodec.parse(legacy).colorPalette)
    }

    @Test fun multiAddressMappingRoundTrip() {
        val store = ConfigStore(MemoryPrefs())
        store.save(
            store.load().copy(
                mappings = listOf(
                    ControlMapping(
                        "BTN_A",
                        "/one",
                        ArgKind.MOMENTARY,
                        extraAddresses = listOf("/two", "/three"),
                    ),
                ),
            ),
        )
        val m = store.load().mappings.single()
        assertEquals(listOf("/one", "/two", "/three"), m.allAddresses())
    }
}
