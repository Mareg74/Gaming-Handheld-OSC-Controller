package com.mareg74.osccontroller.input

import android.view.KeyEvent
import android.view.MotionEvent
import com.mareg74.osccontroller.mapping.ControlId
import org.junit.Assert.*
import org.junit.Test

class GamepadInputTest {
    @Test fun buttonsAndDpad() {
        assertEquals(ControlId.BTN_A, GamepadInput.mapKeyCode(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(ControlId.BTN_DPAD_UP, GamepadInput.mapKeyCode(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(ControlId.BTN_DPAD_LEFT, GamepadInput.mapKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(ControlId.BTN_L3, GamepadInput.mapKeyCode(KeyEvent.KEYCODE_BUTTON_THUMBL))
        assertNull(GamepadInput.mapKeyCode(KeyEvent.KEYCODE_BUTTON_L2))
    }

    @Test fun stickCurveKeepsMidBelowFull() {
        assertEquals(0f, GamepadInput.normalizeAxis(0.1f), 0.0001f)
        val mid = GamepadInput.normalizeAxis(0.55f)
        assertTrue("mid=$mid", mid in 0.01f..0.28f)
        val near = GamepadInput.normalizeAxis(0.92f)
        assertTrue("near=$near", near in 0.25f..0.92f)
        val almost = GamepadInput.normalizeAxis(0.99f)
        assertTrue("almost=$almost", almost in 0.5f..0.999f)
        assertTrue(almost < 1f)
        assertEquals(1f, GamepadInput.normalizeAxis(0.996f), 0.0001f)
        assertEquals(1f, GamepadInput.normalizeAxis(1f), 0.0001f)
    }

    @Test fun stickCurveLinearVsSoft() {
        val linear = GamepadInput.normalizeAxis(0.6f, curve = 1f)
        val soft = GamepadInput.normalizeAxis(0.6f, curve = 3.2f)
        assertTrue("linear=$linear soft=$soft", soft < linear)
    }

    @Test fun calibratedFullScaleMapsPeakToOne() {
        val full = 0.8f
        assertEquals(1f, GamepadInput.normalizeAxis(0.8f, curve = 1f, fullScale = full), 0.0001f)
        assertEquals(1f, GamepadInput.normalizeAxis(0.85f, curve = 1f, fullScale = full), 0.0001f)
        val mid = GamepadInput.normalizeAxis(0.5f, curve = 1f, fullScale = full)
        assertTrue("mid=$mid", mid in 0.4f..0.7f)
        assertTrue(mid < 1f)
        val (lx, ly) = GamepadInput.normalizeStick(0.8f, 0f, curve = 1f, fullScale = full)
        assertEquals(1f, lx, 0.0001f)
        assertEquals(0f, ly, 0.0001f)
    }

    @Test fun rawMagnitudeCapsAtOne() {
        assertEquals(0f, GamepadInput.rawMagnitude(0f, 0f), 0.0001f)
        assertEquals(1f, GamepadInput.rawMagnitude(1f, 0f), 0.0001f)
        assertTrue(GamepadInput.rawMagnitude(0.8f, 0.6f) <= 1f)
    }

    @Test fun triggerCurveLinearVsSoft() {
        val linear = GamepadInput.normalizeTrigger(0.5f, curve = 1f)
        val soft = GamepadInput.normalizeTrigger(0.5f, curve = 1.8f)
        assertTrue("linear=$linear soft=$soft", soft < linear)
    }

    @Test fun hatMapping() {
        assertEquals(ControlId.BTN_DPAD_LEFT, GamepadInput.hatXToControl(-1f))
        assertEquals(ControlId.BTN_DPAD_RIGHT, GamepadInput.hatXToControl(1f))
        assertNull(GamepadInput.hatXToControl(0f))
        assertEquals(ControlId.BTN_DPAD_UP, GamepadInput.hatYToControl(-1f))
        assertEquals(ControlId.BTN_DPAD_DOWN, GamepadInput.hatYToControl(1f))
    }

    @Test fun triggersAndOdinAxes() {
        assertEquals(ControlId.AXIS_L2, GamepadInput.mapAxis(MotionEvent.AXIS_BRAKE))
        assertEquals(ControlId.AXIS_R2, GamepadInput.mapAxis(MotionEvent.AXIS_GAS))
        assertEquals(0f, GamepadInput.normalizeTrigger(0.02f), 0.0001f)
        assertEquals(1f, GamepadInput.normalizeTrigger(1f), 0.0001f)
    }
}
