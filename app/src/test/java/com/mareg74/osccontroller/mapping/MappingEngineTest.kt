package com.mareg74.osccontroller.mapping

import com.mareg74.osccontroller.config.*
import org.junit.Assert.*
import org.junit.Test

class MappingEngineTest {
    private val engine = MappingEngine()

    @Test fun unmappedReturnsNull() {
        assertNull(engine.resolve(AppConfig(), ControlId.BTN_A, 1f))
    }

    @Test fun momentarySendsIntOnPressAndRelease() {
        val cfg = AppConfig(mappings = listOf(
            ControlMapping(ControlId.BTN_A.name, "/composition/columns/1/connect", ArgKind.MOMENTARY)
        ))
        val press = engine.resolveResult(cfg, ControlId.BTN_A.name, 1f, continuous = false)
        assertEquals("/composition/columns/1/connect", press.message!!.address)
        assertEquals(1, press.message!!.args.single())
        assertNull(press.updatedMapping)

        val release = engine.resolveResult(cfg, ControlId.BTN_A.name, 0f, continuous = false)
        assertEquals(0, release.message!!.args.single())
    }

    @Test fun multiAddressMomentaryOffAllAndToggleAll() {
        val mapping = ControlMapping(
            ControlId.BTN_A.name,
            "/a",
            ArgKind.MOMENTARY,
            extraAddresses = listOf("/b", "/c"),
        )
        val cfg = AppConfig(mappings = listOf(mapping))
        val press = engine.resolveResult(cfg, ControlId.BTN_A.name, 1f, continuous = false)
        assertEquals(listOf("/a", "/b", "/c"), press.messages.map { it.address })
        assertTrue(press.messages.all { it.args.single() == 1 })
        val release = engine.resolveResult(cfg, ControlId.BTN_A.name, 0f, continuous = false)
        assertEquals(3, release.messages.size)
        assertTrue(release.messages.all { it.args.single() == 0 })

        val toggle = mapping.copy(argKind = ArgKind.TOGGLE, toggleOn = false)
        val tCfg = AppConfig(mappings = listOf(toggle))
        val flip = engine.resolveResult(tCfg, ControlId.BTN_A.name, 1f, continuous = false)
        assertEquals(3, flip.messages.size)
        assertTrue(flip.messages.all { it.args.single() == 1 })
        assertTrue(flip.updatedMapping!!.toggleOn)
    }

    @Test fun legacyBangNameMigratesToMomentaryBehavior() {
        // ConfigCodec migrates BANG→MOMENTARY; engine only sees MOMENTARY.
        val cfg = AppConfig(mappings = listOf(
            ControlMapping(ControlId.BTN_A.name, "/foo", ArgKind.MOMENTARY)
        ))
        assertEquals(1, engine.resolve(cfg, ControlId.BTN_A, 1f)!!.args.single())
        assertEquals(0, engine.resolve(cfg, ControlId.BTN_A, 0f)!!.args.single())
    }

    @Test fun momentaryInvertXorLogicalPayload() {
        val cfg = AppConfig(mappings = listOf(
            ControlMapping(
                ControlId.BTN_A.name, "/foo", ArgKind.MOMENTARY,
                invert = true,
            )
        ))
        assertEquals(0, engine.resolve(cfg, ControlId.BTN_A, 1f)!!.args.single())
        assertEquals(1, engine.resolve(cfg, ControlId.BTN_A, 0f)!!.args.single())
    }

    @Test fun momentaryCustomOnOff() {
        val cfg = AppConfig(mappings = listOf(
            ControlMapping(
                ControlId.BTN_A.name, "/foo", ArgKind.MOMENTARY,
                customValues = true,
                onValue = 127,
                offValue = 3,
                invert = true, // ignored when custom
            )
        ))
        assertEquals(127, engine.resolve(cfg, ControlId.BTN_A, 1f)!!.args.single())
        assertEquals(3, engine.resolve(cfg, ControlId.BTN_A, 0f)!!.args.single())
    }

    @Test fun togglePressFlipsAndPersistsStateIgnoreRelease() {
        val mapping = ControlMapping(
            ControlId.BTN_A.name, "/t", ArgKind.TOGGLE, toggleOn = false,
        )
        val cfg = AppConfig(mappings = listOf(mapping))
        val first = engine.resolveResult(cfg, ControlId.BTN_A.name, 1f, continuous = false)
        assertEquals(1, first.message!!.args.single())
        assertTrue(first.updatedMapping!!.toggleOn)

        val cfgOn = cfg.copy(mappings = listOf(first.updatedMapping!!))
        val release = engine.resolveResult(cfgOn, ControlId.BTN_A.name, 0f, continuous = false)
        assertNull(release.message)
        assertNull(release.updatedMapping)

        val second = engine.resolveResult(cfgOn, ControlId.BTN_A.name, 1f, continuous = false)
        assertEquals(0, second.message!!.args.single())
        assertFalse(second.updatedMapping!!.toggleOn)
    }

    @Test fun toggleInvertXorLogicalState() {
        val mapping = ControlMapping(
            ControlId.BTN_A.name, "/t", ArgKind.TOGGLE,
            invert = true,
            toggleOn = false,
        )
        val cfg = AppConfig(mappings = listOf(mapping))
        // logical false xor invert → effective true → would send 1 if we sent before flip;
        // press flips to true; true xor invert → false → send 0
        val first = engine.resolveResult(cfg, ControlId.BTN_A.name, 1f, continuous = false)
        assertEquals(0, first.message!!.args.single())
        assertTrue(first.updatedMapping!!.toggleOn)
        // Play color follows logical ON (latch xor invert): true xor true → off
        assertFalse(first.updatedMapping!!.toggleLogicalOn())
        val litCfg = cfg.copy(mappings = listOf(first.updatedMapping!!))
        assertFalse(litCfg.playDigitalLit(0, ControlId.BTN_A.name, hardwareDown = false))
    }

    @Test fun playDigitalLitToggleIgnoresHardware() {
        val on = ControlMapping(ControlId.BTN_A.name, "/t", ArgKind.TOGGLE, toggleOn = true)
        val cfg = AppConfig(mappings = listOf(on))
        assertTrue(cfg.playDigitalLit(0, ControlId.BTN_A.name, hardwareDown = false))
        assertTrue(cfg.playDigitalLit(0, ControlId.BTN_A.name, hardwareDown = true))
        val off = on.copy(toggleOn = false)
        assertFalse(cfg.copy(mappings = listOf(off)).playDigitalLit(0, ControlId.BTN_A.name, false))
    }

    @Test fun floatAxisPassthroughWithoutRange() {
        val cfg = AppConfig(mappings = listOf(
            ControlMapping(ControlId.AXIS_LX.name, "/composition/layers/1/video/opacity", ArgKind.FLOAT)
        ))
        val msg = engine.resolve(cfg, ControlId.AXIS_LX, 0.25f)!!
        assertEquals(0.25f, msg.args.single() as Float, 0.0001f)
    }

    @Test fun floatStickRemapsBipolarIntoFreeRange() {
        val cfg = AppConfig(mappings = listOf(
            ControlMapping(
                ControlId.AXIS_LX.name,
                "/opacity",
                ArgKind.FLOAT,
                floatMin = -2f,
                floatMax = 2f,
            )
        ))
        assertEquals(-2f, engine.resolve(cfg, ControlId.AXIS_LX, -1f)!!.args.single() as Float, 0.0001f)
        assertEquals(0f, engine.resolve(cfg, ControlId.AXIS_LX, 0f)!!.args.single() as Float, 0.0001f)
        assertEquals(2f, engine.resolve(cfg, ControlId.AXIS_LX, 1f)!!.args.single() as Float, 0.0001f)
    }

    @Test fun floatTriggerRemapsUnipolarIntoFreeRange() {
        val cfg = AppConfig(mappings = listOf(
            ControlMapping(
                ControlId.AXIS_L2.name,
                "/opacity",
                ArgKind.FLOAT,
                floatMin = 10f,
                floatMax = 90f,
            )
        ))
        assertEquals(10f, engine.resolve(cfg, ControlId.AXIS_L2, 0f)!!.args.single() as Float, 0.0001f)
        assertEquals(50f, engine.resolve(cfg, ControlId.AXIS_L2, 0.5f)!!.args.single() as Float, 0.0001f)
        assertEquals(90f, engine.resolve(cfg, ControlId.AXIS_L2, 1f)!!.args.single() as Float, 0.0001f)
    }

    @Test fun buttonFloatUsesFixedValueOnPress() {
        val cfg = AppConfig(mappings = listOf(
            ControlMapping(
                ControlId.BTN_A.name,
                "/composition/layers/1/video/opacity",
                ArgKind.FLOAT,
                floatValue = 0.42f,
            )
        ))
        val msg = engine.resolve(cfg, ControlId.BTN_A, 1f)!!
        assertEquals(0.42f, msg.args.single() as Float, 0.0001f)
        assertNull(engine.resolve(cfg, ControlId.BTN_A, 0f))
    }

    @Test fun mappingScopedToActivePage() {
        val cfg = AppConfig(
            activePage = 1,
            mappings = listOf(
                ControlMapping(ControlId.BTN_A.name, "/page0", ArgKind.MOMENTARY, page = 0),
                ControlMapping(ControlId.BTN_A.name, "/page1", ArgKind.MOMENTARY, page = 1),
            ),
        )
        assertEquals("/page1", engine.resolve(cfg, ControlId.BTN_A, 1f)!!.address)
        assertEquals("/page0", engine.resolve(cfg.copy(activePage = 0), ControlId.BTN_A, 1f)!!.address)
    }

    @Test fun resolveIdTactileFaderContinuous() {
        val id = "tw-fader-1"
        val cfg = AppConfig(
            pageTypes = mapOf(0 to PageType.TACTILE),
            mappings = listOf(
                ControlMapping(
                    controlId = id,
                    address = "/fader",
                    argKind = ArgKind.FLOAT,
                    floatMin = 0f,
                    floatMax = 100f,
                ),
            ),
        )
        val msg = engine.resolveId(cfg, id, 0.5f, continuous = true)!!
        assertEquals(50f, msg.args.single() as Float, 0.0001f)
    }

    @Test fun resolveIdUsesExplicitPageNotActivePage() {
        val id = "tw-1"
        val cfg = AppConfig(
            activePage = 0,
            bottomPlayPage = 1,
            pageCount = 2,
            pageTypes = mapOf(0 to PageType.PHYSICAL, 1 to PageType.TACTILE),
            mappings = listOf(
                ControlMapping(id, "/top", ArgKind.MOMENTARY, page = 0),
                ControlMapping(id, "/bottom", ArgKind.MOMENTARY, page = 1),
            ),
        )
        assertEquals("/bottom", engine.resolveId(cfg, id, 1f, continuous = false, page = 1)!!.address)
        assertEquals("/top", engine.resolveId(cfg, id, 1f, continuous = false, page = 0)!!.address)
        assertEquals("/top", engine.resolveId(cfg, id, 1f, continuous = false)!!.address)
    }

    @Test fun padHardwarePageKeepsPhysicalWhileTactileActive() {
        val cfg = AppConfig(
            activePage = 1,
            pageCount = 2,
            pageTypes = mapOf(0 to PageType.PHYSICAL, 1 to PageType.TACTILE),
            mappings = listOf(
                ControlMapping(ControlId.BTN_A.name, "/thor/BTN_A", ArgKind.MOMENTARY, page = 0),
                ControlMapping("tw-a", "/thor/BTN_A", ArgKind.MOMENTARY, page = 1),
            ),
        )
        assertEquals(0, cfg.padHardwarePage())
        // Active tactile page: bare resolve misses pad; explicit pad page hits.
        assertNull(engine.resolveId(cfg, ControlId.BTN_A.name, 1f, continuous = false))
        assertEquals(
            "/thor/BTN_A",
            engine.resolveId(
                cfg,
                ControlId.BTN_A.name,
                1f,
                continuous = false,
                page = cfg.padHardwarePage(),
            )!!.address,
        )
        // Tactile widget still resolves on its own page (same OSC address OK).
        assertEquals(
            "/thor/BTN_A",
            engine.resolveId(cfg, "tw-a", 1f, continuous = false, page = 1)!!.address,
        )
    }
}
