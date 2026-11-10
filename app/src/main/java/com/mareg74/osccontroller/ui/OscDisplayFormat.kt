package com.mareg74.osccontroller.ui

import java.util.Locale
import kotlin.math.abs

/** Human-readable OSC arg formatting for the Monitor (never scientific). */
object OscDisplayFormat {
    fun formatArgs(args: List<Any>): String =
        args.joinToString(prefix = "[", postfix = "]") { formatArg(it) }

    fun formatArg(arg: Any): String = when (arg) {
        is Float -> formatFloat(arg)
        is Double -> formatFloat(arg.toFloat())
        is Int -> arg.toString()
        is Boolean -> if (arg) "T" else "F"
        is String -> arg
        else -> arg.toString()
    }

    /**
     * Axes / floats in Monitor: always 2 decimals (e.g. `0.42`), never scientific.
     */
    fun formatFloat(v: Float): String {
        if (v.isNaN()) return "NaN"
        if (v.isInfinite()) return if (v > 0f) "Inf" else "-Inf"
        return String.format(Locale.US, "%.2f", v)
    }

    /** True when |a−b| < [threshold] (default axis debounce 0.001). */
    fun floatNear(a: Float, b: Float, threshold: Float = AXIS_DEBOUNCE): Boolean =
        abs(a - b) < threshold

    const val AXIS_DEBOUNCE = 0.001f
}
