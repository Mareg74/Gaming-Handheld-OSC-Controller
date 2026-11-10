package com.mareg74.osccontroller

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import com.mareg74.osccontroller.input.GamepadInput
import com.mareg74.osccontroller.mapping.ControlId
import com.mareg74.osccontroller.ui.PrimaryScreen
import com.mareg74.osccontroller.ui.SecondaryDisplayHost
import com.mareg74.osccontroller.ui.SplashScreen
import com.mareg74.osccontroller.ui.ThorOscTheme
import kotlin.math.abs
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var app: ThorOscApp
    private val secondaryDisplayHost = SecondaryDisplayHost()
    private val lastFired = mutableStateOf("")
    private val unmappedHint = mutableStateOf("")
    private val controlLevels = mutableStateOf<Map<ControlId, Float>>(emptyMap())
    private val uiTick = mutableStateOf(0)
    /** Last FLOAT value sent per control — skip unchanged axis spam (keeps "Dernier OSC" useful). */
    private val lastAxisSent = mutableMapOf<ControlId, Float>()
    private var lastHatX: ControlId? = null
    private var lastHatY: ControlId? = null
    private val stickCalibrating = mutableStateOf(false)
    private val calibPeakLeft = mutableStateOf(0f)
    private val calibPeakRight = mutableStateOf(0f)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var clearUnmappedRunnable: Runnable? = null
    /** Shared with secondary Presentation so both screens splash together. */
    private val showSplash = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        app = ThorOscApp(applicationContext)
        if (app.configLoadRecovered) {
            Toast.makeText(
                this,
                "Configuration invalide — réinitialisation aux valeurs par défaut",
                Toast.LENGTH_LONG
            ).show()
        }
        setContent {
            LaunchedEffect(Unit) {
                delay(2000)
                showSplash.value = false
                secondaryDisplayHost.setShowSplash(false)
                // Restore secondary decor to theme after splash.
                mainHandler.post { refreshSecondaryDisplay() }
            }
            if (showSplash.value) {
                SplashScreen()
            } else {
                val fired = lastFired.value
                val hint = unmappedHint.value
                val levels = controlLevels.value
                uiTick.value
                val hardwareDual = run {
                    val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    dm.displays.size >= 2
                }
                val dualDisplay = hardwareDual && !app.config.forceSingleDisplay
                ThorOscTheme(
                    themeMode = app.config.themeMode,
                    colorPalette = app.config.colorPalette,
                ) {
                    PrimaryScreen(
                        app = app,
                        dualDisplay = dualDisplay,
                        lastFired = fired,
                        unmappedHint = hint,
                        controlLevels = levels,
                        stickCalibrating = stickCalibrating.value,
                        calibPeakLeft = calibPeakLeft.value,
                        calibPeakRight = calibPeakRight.value,
                        onStartStickCalibration = ::startStickCalibration,
                        onFinishStickCalibration = ::finishStickCalibration,
                        onCancelStickCalibration = ::cancelStickCalibration,
                        onResetStickCalibration = ::resetStickCalibration,
                        onConfigChanged = ::notifyConfigChangedFromPrimary
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        secondaryDisplayHost.onConfigChangedFromSecondary = {
            // Bottom-screen edits: refresh primary without re-attaching Presentation.
            mainHandler.post { uiTick.value++ }
        }
        secondaryDisplayHost.onStartStickCalibration = ::startStickCalibration
        secondaryDisplayHost.onFinishStickCalibration = ::finishStickCalibration
        secondaryDisplayHost.onCancelStickCalibration = ::cancelStickCalibration
        secondaryDisplayHost.onResetStickCalibration = ::resetStickCalibration
        secondaryDisplayHost.syncLevels(controlLevels.value)
        secondaryDisplayHost.syncCalibration(
            stickCalibrating.value,
            calibPeakLeft.value,
            calibPeakRight.value,
        )
        secondaryDisplayHost.setShowSplash(showSplash.value)
        app.restartReceiver()
        refreshSecondaryDisplay()
    }

    /** Primary-driven config changes also soft-sync the bottom Presentation. */
    private fun notifyConfigChangedFromPrimary() {
        uiTick.value++
        // Defer Presentation attach/detach until after this Compose frame settles.
        mainHandler.post { refreshSecondaryDisplay() }
    }

    private fun refreshSecondaryDisplay() {
        if (isFinishing || isDestroyed) return
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val hardwareDual = dm.displays.size >= 2
        secondaryDisplayHost.sync(this, app, hardwareDual)
    }

    override fun onStop() {
        secondaryDisplayHost.onConfigChangedFromSecondary = null
        secondaryDisplayHost.detach()
        app.receiver.stop()
        app.ndiSession.disconnect()
        super.onStop()
    }

    override fun onDestroy() {
        clearUnmappedRunnable?.let { mainHandler.removeCallbacks(it) }
        clearUnmappedRunnable = null
        super.onDestroy()
    }

    /** Sticky unmapped label — never cleared by mapped OSC spam (avoids flicker). */
    private fun showUnmapped(control: ControlId) {
        val next = "non mappé: ${control.name}"
        if (unmappedHint.value != next) {
            unmappedHint.value = next
        }
        clearUnmappedRunnable?.let { mainHandler.removeCallbacks(it) }
        val clear = Runnable {
            if (unmappedHint.value == next) {
                unmappedHint.value = ""
            }
            clearUnmappedRunnable = null
        }
        clearUnmappedRunnable = clear
        mainHandler.postDelayed(clear, UNMAPPED_HINT_HOLD_MS)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Always route DPAD_* to OSC and block Compose focus theft.
        // Odin may omit SOURCE_GAMEPAD; HAT axes are handled in motion events.
        if (isDpadKey(event.keyCode)) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                    handleGamepadKey(event.keyCode, 1f)
                }
                KeyEvent.ACTION_UP -> handleGamepadKey(event.keyCode, 0f)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (handleGamepadKey(keyCode, 1f)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (handleGamepadKey(keyCode, 0f)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun setLevel(control: ControlId, value: Float) {
        val previous = controlLevels.value[control] ?: 0f
        if (abs(previous - value) < 0.008f) return
        controlLevels.value = controlLevels.value + (control to value)
        secondaryDisplayHost.syncLevels(controlLevels.value)
    }

    private fun handleGamepadKey(keyCode: Int, value: Float): Boolean {
        val control = GamepadInput.mapKeyCode(keyCode) ?: return false
        setLevel(control, value)
        // Mapping screens: update levels only — no pad OSC (QCM except Mapping).
        if (app.mappingScreenOpen()) return true
        val line = app.emitHardwareControl(control.name, value, continuous = false)
        if (line != null) {
            lastFired.value = line
            app.playOscMonitor.value = line
            return true
        }
        if (value >= 0.5f) {
            showUnmapped(control)
        }
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (isStickOrGamepadMotion(event)) {
            handleStickMotion(event)
            // Always consume — otherwise AXIS_X/Y drive Android focus navigation.
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event != null && isStickOrGamepadMotion(event)) {
            handleStickMotion(event)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun startStickCalibration() {
        calibPeakLeft.value = 0f
        calibPeakRight.value = 0f
        stickCalibrating.value = true
        secondaryDisplayHost.syncCalibration(true, 0f, 0f)
    }

    private fun finishStickCalibration() {
        if (!stickCalibrating.value) return
        app.config = app.config.copy(
            stickLeftFull = calibPeakLeft.value.coerceIn(
                GamepadInput.MIN_CALIBRATED_FULL,
                1f,
            ),
            stickRightFull = calibPeakRight.value.coerceIn(
                GamepadInput.MIN_CALIBRATED_FULL,
                1f,
            ),
        )
        app.persist()
        stickCalibrating.value = false
        secondaryDisplayHost.syncCalibration(false, calibPeakLeft.value, calibPeakRight.value)
        uiTick.value++
    }

    private fun cancelStickCalibration() {
        stickCalibrating.value = false
        calibPeakLeft.value = 0f
        calibPeakRight.value = 0f
        secondaryDisplayHost.syncCalibration(false, 0f, 0f)
    }

    private fun resetStickCalibration() {
        stickCalibrating.value = false
        calibPeakLeft.value = 0f
        calibPeakRight.value = 0f
        app.config = app.config.copy(stickLeftFull = 1f, stickRightFull = 1f)
        app.persist()
        secondaryDisplayHost.syncCalibration(false, 0f, 0f)
        uiTick.value++
    }

    private fun handleStickMotion(event: MotionEvent) {
        val stickCurve = app.config.stickCurve
        val triggerCurve = app.config.triggerCurve
        val rawLx = event.getAxisValue(MotionEvent.AXIS_X)
        val rawLy = event.getAxisValue(MotionEvent.AXIS_Y)
        val rawRx = event.getAxisValue(MotionEvent.AXIS_Z)
        val rawRy = event.getAxisValue(MotionEvent.AXIS_RZ)

        if (stickCalibrating.value) {
            val leftMag = GamepadInput.rawMagnitude(rawLx, rawLy)
            val rightMag = GamepadInput.rawMagnitude(rawRx, rawRy)
            if (leftMag > calibPeakLeft.value) calibPeakLeft.value = leftMag
            if (rightMag > calibPeakRight.value) calibPeakRight.value = rightMag
            secondaryDisplayHost.syncCalibration(
                true,
                calibPeakLeft.value,
                calibPeakRight.value,
            )
        }

        val (lx, ly) = GamepadInput.normalizeStick(
            rawLx,
            rawLy,
            stickCurve,
            app.config.stickLeftFull,
        )
        setLevel(ControlId.AXIS_LX, lx)
        setLevel(ControlId.AXIS_LY, ly)
        emitAxis(ControlId.AXIS_LX, lx)
        emitAxis(ControlId.AXIS_LY, ly)

        val (rx, ry) = GamepadInput.normalizeStick(
            rawRx,
            rawRy,
            stickCurve,
            app.config.stickRightFull,
        )
        setLevel(ControlId.AXIS_RX, rx)
        setLevel(ControlId.AXIS_RY, ry)
        emitAxis(ControlId.AXIS_RX, rx)
        emitAxis(ControlId.AXIS_RY, ry)

        val l2 = GamepadInput.normalizeTrigger(
            maxOf(
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE),
            ),
            triggerCurve,
        )
        val r2 = GamepadInput.normalizeTrigger(
            maxOf(
                event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS),
            ),
            triggerCurve,
        )
        setLevel(ControlId.AXIS_L2, l2)
        setLevel(ControlId.AXIS_R2, r2)
        emitAxis(ControlId.AXIS_L2, l2)
        emitAxis(ControlId.AXIS_R2, r2)

        updateHat(
            axisValue = event.getAxisValue(MotionEvent.AXIS_HAT_X),
            previous = lastHatX,
            toControl = GamepadInput::hatXToControl,
        ) { lastHatX = it }
        updateHat(
            axisValue = event.getAxisValue(MotionEvent.AXIS_HAT_Y),
            previous = lastHatY,
            toControl = GamepadInput::hatYToControl,
        ) { lastHatY = it }
    }

    private fun updateHat(
        axisValue: Float,
        previous: ControlId?,
        toControl: (Float) -> ControlId?,
        save: (ControlId?) -> Unit,
    ) {
        val current = toControl(axisValue)
        if (current == previous) return
        if (previous != null) emitButton(previous, 0f)
        if (current != null) emitButton(current, 1f)
        save(current)
    }

    private fun emitButton(control: ControlId, value: Float) {
        setLevel(control, value)
        if (app.mappingScreenOpen()) return
        val line = app.emitHardwareControl(control.name, value, continuous = false)
        if (line != null) {
            lastFired.value = line
            app.playOscMonitor.value = line
            return
        }
        if (value >= 0.5f) {
            showUnmapped(control)
        }
    }

    private fun emitAxis(control: ControlId, value: Float) {
        val previous = lastAxisSent[control]
        if (previous == null) {
            lastAxisSent[control] = value
            if (abs(value) < AXIS_EPSILON) return
        } else if (abs(previous - value) < AXIS_EPSILON) {
            return
        }
        lastAxisSent[control] = value
        if (app.mappingScreenOpen()) return
        val bipolar = !com.mareg74.osccontroller.mapping.MappingEngine.isTriggerAxis(control)
        val line = app.emitHardwareControl(
            control.name,
            value,
            continuous = true,
            bipolarHardware = bipolar,
        )
        if (line == null) {
            if (abs(value) >= UNMAPPED_AXIS_HINT) {
                showUnmapped(control)
            }
            return
        }
        lastFired.value = line
        app.playOscMonitor.value = line
    }

    private companion object {
        const val AXIS_EPSILON = 0.004f
        const val UNMAPPED_AXIS_HINT = 0.2f
        const val UNMAPPED_HINT_HOLD_MS = 1400L

        fun isStickOrGamepadMotion(event: MotionEvent): Boolean =
            (event.source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0 ||
                (event.source and InputDevice.SOURCE_GAMEPAD) != 0 ||
                (event.source and InputDevice.SOURCE_DPAD) != 0

        fun isDpadKey(keyCode: Int): Boolean = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            -> true
            else -> false
        }
    }
}
