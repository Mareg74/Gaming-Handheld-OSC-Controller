package com.mareg74.osccontroller.ui

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.graphics.Color as AndroidColor
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.core.view.WindowCompat
import com.mareg74.osccontroller.ThorOscApp
import com.mareg74.osccontroller.config.ColorPalette
import com.mareg74.osccontroller.config.ThemeMode
import com.mareg74.osccontroller.mapping.ControlId

class SecondaryDisplayHost {
    private var presentation: Presentation? = null
    private val themeState = mutableStateOf(ThemeMode.SYSTEM)
    private val paletteState = mutableStateOf(ColorPalette.TEAL_SCENE)
    private val contentEpoch = mutableIntStateOf(0)
    private val showSplashState = mutableStateOf(false)
    private val levelsState = mutableStateOf<Map<ControlId, Float>>(emptyMap())
    private val stickCalibratingState = mutableStateOf(false)
    private val calibPeakLeftState = mutableFloatStateOf(0f)
    private val calibPeakRightState = mutableFloatStateOf(0f)

    /** Primary UI refresh when config is edited on the bottom screen. */
    var onConfigChangedFromSecondary: (() -> Unit)? = null
    var onStartStickCalibration: () -> Unit = {}
    var onFinishStickCalibration: () -> Unit = {}
    var onCancelStickCalibration: () -> Unit = {}
    var onResetStickCalibration: () -> Unit = {}

    /**
     * Attach if needed, or softly sync theme/page without tearing down the Presentation
     * (full recreate causes a visible flicker on the bottom screen).
     */
    fun sync(activity: Activity, app: ThorOscApp, hardwareDual: Boolean) {
        val wantSecondary = hardwareDual && !app.config.forceSingleDisplay
        if (!wantSecondary) {
            detach()
            return
        }
        themeState.value = app.config.themeMode
        paletteState.value = app.config.colorPalette
        contentEpoch.intValue++
        if (presentation == null) {
            attach(activity, app)
        } else {
            presentation?.window?.decorView?.setBackgroundColor(
                decorColor(activity, app.config.themeMode)
            )
        }
    }

    fun setShowSplash(show: Boolean) {
        showSplashState.value = show
        if (show) {
            presentation?.window?.decorView?.setBackgroundColor(AndroidColor.BLACK)
        }
    }

    fun syncLevels(levels: Map<ControlId, Float>) {
        levelsState.value = levels
    }

    fun syncCalibration(calibrating: Boolean, peakLeft: Float, peakRight: Float) {
        stickCalibratingState.value = calibrating
        calibPeakLeftState.floatValue = peakLeft
        calibPeakRightState.floatValue = peakRight
    }

    fun attach(activity: Activity, app: ThorOscApp) {
        themeState.value = app.config.themeMode
        paletteState.value = app.config.colorPalette
        detach()
        val dm = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val secondary = dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY } ?: return
        val lifecycleOwner = activity as LifecycleOwner
        val savedStateOwner = activity as SavedStateRegistryOwner
        // Presentation Compose tree is not hosted by the Activity window — wire the
        // Activity result registry so tabs like Projets (import/export SAF) don't crash.
        val componentActivity = activity as ComponentActivity
        val p = object : Presentation(activity, secondary) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                window?.let { w ->
                    WindowCompat.setDecorFitsSystemWindows(w, false)
                    w.statusBarColor = AndroidColor.TRANSPARENT
                    w.navigationBarColor = AndroidColor.TRANSPARENT
                    val bg = if (showSplashState.value) {
                        AndroidColor.BLACK
                    } else {
                        decorColor(context, app.config.themeMode)
                    }
                    w.decorView.setBackgroundColor(bg)
                }
                setContentView(
                    ComposeView(context).apply {
                        setViewTreeLifecycleOwner(lifecycleOwner)
                        setViewTreeSavedStateRegistryOwner(savedStateOwner)
                        setContent {
                            val splash = showSplashState.value
                            if (splash) {
                                SplashScreen()
                            } else {
                                val mode = themeState.value
                                val palette = paletteState.value
                                contentEpoch.intValue // recompose when config changes
                                val levels = levelsState.value
                                val calibrating = stickCalibratingState.value
                                val peakLeft = calibPeakLeftState.floatValue
                                val peakRight = calibPeakRightState.floatValue
                                // Presentation has no ActivityResultRegistryOwner in its view tree;
                                // Projets uses rememberLauncherForActivityResult and would crash without this.
                                CompositionLocalProvider(
                                    LocalActivityResultRegistryOwner provides componentActivity,
                                ) {
                                    ThorOscTheme(themeMode = mode, colorPalette = palette) {
                                        SecondaryScreen(
                                            app = app,
                                            controlLevels = levels,
                                            stickCalibrating = calibrating,
                                            calibPeakLeft = peakLeft,
                                            calibPeakRight = peakRight,
                                            onStartStickCalibration = onStartStickCalibration,
                                            onFinishStickCalibration = onFinishStickCalibration,
                                            onCancelStickCalibration = onCancelStickCalibration,
                                            onResetStickCalibration = onResetStickCalibration,
                                            onConfigChanged = {
                                                contentEpoch.intValue++
                                                onConfigChangedFromSecondary?.invoke()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
        presentation = p
        try {
            p.show()
        } catch (_: Exception) {
            presentation = null
        }
    }

    fun detach() {
        val p = presentation ?: return
        presentation = null
        try {
            // Drop Compose content before dismiss — avoids crashes when the
            // Presentation still shares the Activity LifecycleOwner.
            p.setContentView(android.view.View(p.context))
        } catch (_: Exception) {
        }
        try {
            p.dismiss()
        } catch (_: Exception) {
        }
    }

    private companion object {
        fun decorColor(context: Context, mode: ThemeMode): Int {
            val dark = when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM ->
                    (context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            return if (dark) {
                AndroidColor.parseColor("#0B0F14")
            } else {
                AndroidColor.parseColor("#F8FAFC")
            }
        }
    }
}
