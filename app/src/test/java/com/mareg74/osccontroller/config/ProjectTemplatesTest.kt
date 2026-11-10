package com.mareg74.osccontroller.config

import com.mareg74.osccontroller.mapping.ControlId
import org.junit.Assert.*
import org.junit.Test

class ProjectTemplatesTest {
    @Test fun nextDefaultNameIncrements() {
        assertEquals("Projet 1", ProjectTemplates.nextDefaultName(emptyList()))
        assertEquals("Projet 3", ProjectTemplates.nextDefaultName(listOf("Projet 1", "Projet 2", "Autre")))
        assertEquals("Projet 5", ProjectTemplates.nextDefaultName(listOf("projet 4")))
    }

    @Test fun emptyClearsMappingsKeepsNetwork() {
        val base = AppConfig(targetIp = "10.0.0.5", targetPort = 9000)
        val cfg = ProjectTemplates.empty(base)
        assertEquals("10.0.0.5", cfg.targetIp)
        assertEquals(9000, cfg.targetPort)
        assertTrue(cfg.mappings.isEmpty())
        assertEquals(1, cfg.pageCount)
        assertEquals(PageType.PHYSICAL, cfg.pageType(0))
        assertTrue(cfg.widgets.isEmpty())
    }

    @Test fun basicTemplatePadAndTactileOnly() {
        val cfg = ProjectTemplates.basicOsc(AppConfig(targetIp = "192.168.1.2"))
        assertEquals("192.168.1.2", cfg.targetIp)
        assertEquals(2, cfg.pageCount)
        assertEquals("Pad", cfg.pageNames[0])
        assertEquals("Tactile", cfg.pageNames[1])
        assertEquals(PageType.PHYSICAL, cfg.pageType(0))
        assertEquals(PageType.TACTILE, cfg.pageType(1))
        assertEquals(64, cfg.tactileGridColumns)
        assertEquals(ColorPalette.VIOLET_SCENE, cfg.colorPalette)
        assertFalse(cfg.pageNames.containsKey(2))
    }

    @Test fun basicTemplatePhysicalPadMomentaryAndAxes() {
        val cfg = ProjectTemplates.basicOsc()
        ControlId.entries.forEach { id ->
            val m = cfg.mappings.first { it.controlId == id.name && it.page == 0 }
            assertEquals("/thor/${id.name}", m.address)
            assertEquals(id.name, cfg.noteFor(0, id.name))
            if (id.name.startsWith("AXIS_")) {
                assertEquals(ArgKind.FLOAT, m.argKind)
            } else {
                assertEquals(ArgKind.MOMENTARY, m.argKind)
            }
        }
    }

    @Test fun basicTemplateTactileLayoutLiveScene() {
        val cfg = ProjectTemplates.basicOsc()
        val widgets = cfg.widgetsOn(1)
        assertEquals(19, widgets.size)
        assertEquals(3, widgets.count { it.kind == TactileKind.FADER })
        assertEquals(16, widgets.count { it.kind == TactileKind.TRIGGER })

        val faders = widgets.filter { it.kind == TactileKind.FADER }.sortedBy { it.x }
        assertEquals(listOf("Fader 1", "Fader 2", "Fader 3"), faders.map { it.label })
        assertEquals("#AA00FF", faders[0].fillColor)
        assertEquals("#22D3EE", faders[1].fillColor)
        assertNull(faders[2].fillColor)
        assertEquals(0.0f, faders[0].x, 0.0001f)
        assertEquals(0.09375f, faders[0].w, 0.0001f)
        assertEquals(0.97218f, faders[0].h, 0.0001f)
        assertEquals(0.10938f, faders[1].x, 0.0001f)
        assertEquals(0.21875f, faders[2].x, 0.0001f)

        faders.forEachIndexed { i, w ->
            val m = cfg.mappings.first { it.controlId == w.id && it.page == 1 }
            assertEquals("/thor/fader/${i + 1}", m.address)
            assertEquals(ArgKind.FLOAT, m.argKind)
            assertEquals(0f, m.floatMin)
            assertEquals(1f, m.floatMax)
        }

        val triggers = widgets.filter { it.kind == TactileKind.TRIGGER }
            .sortedWith(compareBy({ it.y }, { it.x }))
        assertEquals(16, triggers.size)

        // Row 1: Toggle 1..4
        triggers.take(4).forEachIndexed { i, w ->
            assertEquals("Toggle ${i + 1}", w.label)
            assertEquals("#C4B5FD", w.fillColor)
            val m = cfg.mappings.first { it.controlId == w.id && it.page == 1 }
            assertEquals(ArgKind.TOGGLE, m.argKind)
            assertEquals("/thor/trigger/${i + 1}", m.address)
        }

        // Rows 2–4: Momentary 1..12 → /thor/trigger/5..16
        triggers.drop(4).forEachIndexed { i, w ->
            assertEquals("Momentary ${i + 1}", w.label)
            val m = cfg.mappings.first { it.controlId == w.id && it.page == 1 }
            assertEquals(ArgKind.MOMENTARY, m.argKind)
            assertEquals("/thor/trigger/${i + 5}", m.address)
        }

        assertEquals("#2EC4B6", triggers[4].fillColor)
        assertNull(triggers[8].fillColor)
        assertEquals("#E8B84A", triggers[12].fillColor)

        assertEquals(0.32812f, triggers[0].x, 0.0001f)
        assertEquals(0.15625f, triggers[0].w, 0.0001f)
        assertEquals(0.23332f, triggers[0].h, 0.0001f)
        assertEquals(0.27221f, triggers[4].y, 0.0001f)
        assertEquals(0.54442f, triggers[8].y, 0.0001f)
        assertEquals(0.81663f, triggers[12].y, 0.0001f)
        assertEquals(0.15555f, triggers[12].h, 0.0001f)
    }

    @Test fun firstFreeThorIndexSkipsGaps() {
        assertEquals(1, ThorDefaultOsc.firstFreeIndex(emptyList(), ThorDefaultOsc.TRIGGER_PREFIX))
        assertEquals(
            5,
            ThorDefaultOsc.firstFreeIndex(
                listOf("/thor/trigger/1", "/thor/trigger/2", "/thor/trigger/3", "/thor/trigger/4", "/thor/trigger/6"),
                ThorDefaultOsc.TRIGGER_PREFIX,
            ),
        )
        assertEquals(
            3,
            ThorDefaultOsc.firstFreeIndex(
                listOf("/thor/fader/1", "/thor/fader/2", "/composition/x"),
                ThorDefaultOsc.FADER_PREFIX,
            ),
        )
    }

    @Test fun defaultAddSpecsUseFreeIndexAndLabels() {
        val maps = listOf(
            ControlMapping("a", "/thor/trigger/1", ArgKind.MOMENTARY, page = 1),
            ControlMapping("b", "/thor/trigger/2", ArgKind.TOGGLE, page = 1),
            ControlMapping("c", "/thor/fader/1", ArgKind.FLOAT, page = 1),
        )
        val trig = ThorDefaultOsc.defaultAddSpec(TactileKind.TRIGGER, maps)
        assertEquals(3, trig.index)
        assertEquals("/thor/trigger/3", trig.address)
        assertEquals("Trig 3", trig.label)
        assertEquals(ArgKind.MOMENTARY, trig.argKind)

        val fader = ThorDefaultOsc.defaultAddSpec(TactileKind.FADER, maps)
        assertEquals(2, fader.index)
        assertEquals("/thor/fader/2", fader.address)
        assertEquals("Fader 2", fader.label)
        assertEquals(ArgKind.FLOAT, fader.argKind)
    }
}
