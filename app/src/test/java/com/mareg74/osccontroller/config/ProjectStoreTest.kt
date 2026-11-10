package com.mareg74.osccontroller.config

import org.junit.Assert.*
import org.junit.Test

class ProjectStoreTest {
    @Test
    fun saveListGetRoundTrip() {
        val store = ProjectStore(MemoryPrefs())
        val cfg = AppConfig(
            targetIp = "10.0.0.5",
            targetPort = 7010,
            mappings = listOf(
                ControlMapping("BTN_A", "/composition/columns/1/connect", ArgKind.MOMENTARY, 0),
            ),
            buttonNotes = mapOf(noteStorageKey(0, "BTN_A") to "STROBE"),
            pageCount = 2,
            pageNames = mapOf(0 to "Intro", 1 to "Drop"),
        )
        val meta = store.save("Club set", cfg)
        assertEquals(1, store.list().size)
        assertEquals("Club set", store.list().single().name)
        val loaded = store.get(meta.id)!!
        assertEquals("10.0.0.5", loaded.config.targetIp)
        assertEquals(7010, loaded.config.targetPort)
        assertEquals("/composition/columns/1/connect", loaded.config.mappings.single().address)
        assertEquals("STROBE", loaded.config.noteFor(0, "BTN_A"))
        assertEquals("Intro", loaded.config.pageNames[0])
    }

    @Test
    fun overwriteSameIdKeepsOneEntry() {
        val store = ProjectStore(MemoryPrefs())
        val first = store.save("Show", AppConfig(targetIp = "1.1.1.1"))
        Thread.sleep(2)
        val second = store.save("Show v2", AppConfig(targetIp = "2.2.2.2"), existingId = first.id)
        assertEquals(first.id, second.id)
        assertEquals(1, store.list().size)
        assertEquals("Show v2", store.list().single().name)
        assertEquals("2.2.2.2", store.get(first.id)!!.config.targetIp)
    }

    @Test
    fun deleteRemovesBodyAndIndex() {
        val prefs = MemoryPrefs()
        val store = ProjectStore(prefs)
        val meta = store.save("Temp", AppConfig())
        assertTrue(prefs.getString(ProjectStore.bodyKey(meta.id), "").isNotBlank())
        store.delete(meta.id)
        assertTrue(store.list().isEmpty())
        assertEquals("", prefs.getString(ProjectStore.bodyKey(meta.id), ""))
    }

    @Test
    fun applyProjectPartsOscOnlyLeavesMapping() {
        val live = AppConfig(
            targetIp = "192.168.1.1",
            targetPort = 7000,
            mappings = listOf(ControlMapping("BTN_A", "/live", ArgKind.MOMENTARY)),
            stickCurve = 2f,
        )
        val project = AppConfig(
            targetIp = "10.0.0.9",
            targetPort = 9000,
            listenPort = 9001,
            mappings = listOf(ControlMapping("BTN_B", "/proj", ArgKind.MOMENTARY)),
            stickCurve = 4f,
        )
        val next = live.applyProjectParts(project, setOf(ProjectPart.OSC_NETWORK))
        assertEquals("10.0.0.9", next.targetIp)
        assertEquals(9000, next.targetPort)
        assertEquals(9001, next.listenPort)
        assertEquals("/live", next.mappings.single().address)
        assertEquals(2f, next.stickCurve)
    }

    @Test
    fun applyProjectPartsMappingOnlyLeavesOsc() {
        val live = AppConfig(
            targetIp = "192.168.1.1",
            mappings = listOf(ControlMapping("BTN_A", "/live", ArgKind.MOMENTARY)),
            pageCount = 1,
        )
        val project = AppConfig(
            targetIp = "10.0.0.9",
            mappings = listOf(
                ControlMapping("BTN_B", "/proj", ArgKind.MOMENTARY, page = 1),
            ),
            pageCount = 2,
            pageNames = mapOf(1 to "B"),
            buttonNotes = mapOf(noteStorageKey(1, "BTN_B") to "note"),
            activePage = 1,
        )
        val next = live.applyProjectParts(project, setOf(ProjectPart.MAPPING))
        assertEquals("192.168.1.1", next.targetIp)
        assertEquals(2, next.pageCount)
        assertEquals(1, next.activePage)
        assertEquals("/proj", next.mappings.single().address)
        assertEquals("note", next.noteFor(1, "BTN_B"))
        assertEquals("B", next.pageNames[1])
    }

    @Test
    fun applyAllParts() {
        val live = AppConfig()
        val project = AppConfig(
            targetIp = "8.8.8.8",
            forceSingleDisplay = true,
            themeMode = ThemeMode.DARK,
            colorPalette = ColorPalette.CYAN_BROADCAST,
            stickCurve = 3f,
            stickLeftFull = 0.8f,
            mappings = listOf(ControlMapping("BTN_X", "/x", ArgKind.MOMENTARY)),
        )
        val next = live.applyProjectParts(project, ProjectPart.entries.toSet())
        assertEquals("8.8.8.8", next.targetIp)
        assertTrue(next.forceSingleDisplay)
        assertEquals(ThemeMode.DARK, next.themeMode)
        assertEquals(ColorPalette.CYAN_BROADCAST, next.colorPalette)
        assertEquals(3f, next.stickCurve)
        assertEquals(0.8f, next.stickLeftFull)
        assertEquals("/x", next.mappings.single().address)
    }

    @Test
    fun saveAutosaveOverwritesSameSlot() {
        val store = ProjectStore(MemoryPrefs())
        val a = store.saveAutosave(AppConfig(targetIp = "1.1.1.1"))
        val b = store.saveAutosave(AppConfig(targetIp = "2.2.2.2"))
        assertEquals(a.id, b.id)
        assertEquals(1, store.list().count { it.name.equals(ProjectStore.AUTOSAVE_NAME, true) })
        assertEquals("2.2.2.2", store.get(a.id)!!.config.targetIp)
    }

    @Test
    fun activeIdTracksOpenProjectAndClearsOnDelete() {
        val store = ProjectStore(MemoryPrefs())
        val open = store.save("Ouvert", AppConfig(targetIp = "1.1.1.1"))
        val other = store.save("Autre", AppConfig(targetIp = "2.2.2.2"))
        store.setActiveId(open.id)
        assertTrue(store.isActive(open.id))
        assertFalse(store.isActive(other.id))
        store.delete(other.id)
        assertEquals(open.id, store.activeId())
        assertEquals("1.1.1.1", store.get(open.id)!!.config.targetIp)
        store.delete(open.id)
        assertNull(store.activeId())
        assertTrue(store.list().isEmpty())
    }
}
