package com.mareg74.osccontroller.input

import android.view.KeyEvent
import android.view.MotionEvent
import com.mareg74.osccontroller.mapping.ControlId
import kotlin.math.hypot
import kotlin.math.pow

object GamepadInput {
    private const val STICK_DEADZONE = 0.18f
    private const val TRIGGER_DEADZONE = 0.04f
    /** Default full-scale gate when no calibration is stored. */
    private const val DEFAULT_FULL_GATE = 0.995f
    const val DEFAULT_STICK_CURVE = 3.2f
    const val DEFAULT_TRIGGER_CURVE = 1.8f
    const val MIN_CALIBRATED_FULL = 0.55f

    fun mapKeyCode(keyCode: Int): ControlId? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> ControlId.BTN_A
        KeyEvent.KEYCODE_BUTTON_B -> ControlId.BTN_B
        KeyEvent.KEYCODE_BUTTON_X -> ControlId.BTN_X
        KeyEvent.KEYCODE_BUTTON_Y -> ControlId.BTN_Y
        KeyEvent.KEYCODE_BUTTON_L1 -> ControlId.BTN_L1
        KeyEvent.KEYCODE_BUTTON_R1 -> ControlId.BTN_R1
        KeyEvent.KEYCODE_BUTTON_THUMBL -> ControlId.BTN_L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> ControlId.BTN_R3
        KeyEvent.KEYCODE_DPAD_UP -> ControlId.BTN_DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> ControlId.BTN_DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> ControlId.BTN_DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> ControlId.BTN_DPAD_RIGHT
        KeyEvent.KEYCODE_BUTTON_START -> ControlId.BTN_START
        KeyEvent.KEYCODE_BUTTON_SELECT -> ControlId.BTN_SELECT
        else -> null
    }

    fun mapAxis(axis: Int): ControlId? = when (axis) {
        MotionEvent.AXIS_X -> ControlId.AXIS_LX
        MotionEvent.AXIS_Y -> ControlId.AXIS_LY
        MotionEvent.AXIS_Z -> ControlId.AXIS_RX
        MotionEvent.AXIS_RZ -> ControlId.AXIS_RY
        MotionEvent.AXIS_LTRIGGER -> ControlId.AXIS_L2
        MotionEvent.AXIS_RTRIGGER -> ControlId.AXIS_R2
        MotionEvent.AXIS_BRAKE -> ControlId.AXIS_L2
        MotionEvent.AXIS_GAS -> ControlId.AXIS_R2
        else -> null
    }

    fun isTrigger(control: ControlId): Boolean =
        control == ControlId.AXIS_L2 || control == ControlId.AXIS_R2

    fun isAnalog(control: ControlId): Boolean =
        control.name.startsWith("AXIS_")

    /** Raw stick magnitude (for calibration sampling). */
    fun rawMagnitude(x: Float, y: Float): Float =
        hypot(x.toDouble(), y.toDouble()).toFloat().coerceAtMost(1f)

    /**
     * Stick vector: circular clamp, deadzone, power curve, calibrated full-scale.
     * @param curve 1 = linear, higher = softer mid-throw (clamped 1…4)
     * @param fullScale peak raw magnitude at physical rail (from calibration).
     *   Values at/above this emit ±1. Default ~1.
     */
    fun normalizeStick(
        x: Float,
        y: Float,
        curve: Float = DEFAULT_STICK_CURVE,
        fullScale: Float = 1f,
    ): Pair<Float, Float> {
        val power = curve.coerceIn(1f, 4f)
        val gate = resolveFullGate(fullScale)
        val vx = x.coerceIn(-1f, 1f)
        val vy = y.coerceIn(-1f, 1f)
        val mag = hypot(vx.toDouble(), vy.toDouble()).toFloat()
        if (mag < STICK_DEADZONE) return 0f to 0f
        val directionScale = 1f / mag
        val dx = vx * directionScale
        val dy = vy * directionScale
        val clamped = mag.coerceAtMost(1f)
        if (clamped >= gate) {
            return dx to dy
        }
        val span = (gate - STICK_DEADZONE).coerceAtLeast(0.01f)
        val resur = ((clamped - STICK_DEADZONE) / span).coerceIn(0f, 1f)
        val curved = resur.pow(power)
        return (dx * curved) to (dy * curved)
    }

    fun normalizeAxis(
        value: Float,
        curve: Float = DEFAULT_STICK_CURVE,
        fullScale: Float = 1f,
    ): Float {
        val (x, _) = normalizeStick(value, 0f, curve, fullScale)
        return x
    }

    fun normalizeTrigger(
        value: Float,
        curve: Float = DEFAULT_TRIGGER_CURVE,
        fullScale: Float = 1f,
    ): Float {
        val power = curve.coerceIn(1f, 3f)
        val gate = resolveFullGate(fullScale)
        val v = value.coerceIn(0f, 1f)
        if (v < TRIGGER_DEADZONE) return 0f
        if (v >= gate) return 1f
        val span = (gate - TRIGGER_DEADZONE).coerceAtLeast(0.01f)
        val resur = ((v - TRIGGER_DEADZONE) / span).coerceIn(0f, 1f)
        return resur.pow(power)
    }

    fun resolveFullGate(fullScale: Float): Float {
        val scale = fullScale.coerceIn(MIN_CALIBRATED_FULL, 1f)
        // Keep a tiny headroom below the measured peak so ±1 needs the rail.
        return if (scale >= 0.995f) DEFAULT_FULL_GATE else scale
    }

    fun hatXToControl(hatX: Float): ControlId? = when {
        hatX < -0.5f -> ControlId.BTN_DPAD_LEFT
        hatX > 0.5f -> ControlId.BTN_DPAD_RIGHT
        else -> null
    }

    fun hatYToControl(hatY: Float): ControlId? = when {
        hatY < -0.5f -> ControlId.BTN_DPAD_UP
        hatY > 0.5f -> ControlId.BTN_DPAD_DOWN
        else -> null
    }
}
