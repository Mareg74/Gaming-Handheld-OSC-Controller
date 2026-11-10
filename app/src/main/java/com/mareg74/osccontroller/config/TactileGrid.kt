package com.mareg74.osccontroller.config

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Tactile layout grid. Column count is per-project ([AppConfig.tactileGridColumns]);
 * row cell size is derived from canvas aspect (width/height) so cells stay visually square.
 */
object TactileGrid {
    const val DEFAULT_COLUMNS = 16
    val COLUMN_CHOICES: List<Int> = listOf(8, 12, 16, 24, 32, 64)

    fun normalizeColumns(raw: Int): Int =
        COLUMN_CHOICES.minByOrNull { kotlin.math.abs(it - raw) } ?: DEFAULT_COLUMNS

    /** Normalized cell width. */
    fun cellWidth(columns: Int = DEFAULT_COLUMNS): Float {
        val cols = normalizeColumns(columns).coerceAtLeast(1)
        return 1f / cols
    }

    /**
     * Normalized cell height for a canvas with [aspectWH] = widthPx / heightPx.
     * Falls back to a landscape 16:9 assumption when aspect is unknown/invalid.
     */
    fun cellHeight(aspectWH: Float, columns: Int = DEFAULT_COLUMNS): Float {
        val aspect = aspectWH.takeIf { it.isFinite() && it > 0.05f } ?: (16f / 9f)
        return cellWidth(columns) * aspect
    }

    fun snap(value: Float, step: Float): Float {
        if (step <= 0f) return value
        return (value / step).roundToInt() * step
    }

    fun cellsAlong(length: Float, step: Float): Int {
        if (step <= 0f) return 1
        return max(1, (length / step).roundToInt())
    }

    /** Snap position + size to the grid and clamp to canvas. */
    fun snapWidget(
        widget: TactileWidget,
        aspectWH: Float,
        columns: Int = DEFAULT_COLUMNS,
    ): TactileWidget {
        val cols = normalizeColumns(columns)
        val cw = cellWidth(cols)
        val ch = cellHeight(aspectWH, cols)
        val wCells = cellsAlong(widget.w, cw).coerceAtMost(cols)
        val maxHCells = max(1, (1f / ch).toInt().coerceAtLeast(1))
        val hCells = cellsAlong(widget.h, ch).coerceAtMost(maxHCells)
        val nw = (wCells * cw).coerceIn(cw, 1f)
        val nh = (hCells * ch).coerceIn(ch, 1f)
        val nx = snap(widget.x, cw).coerceIn(0f, 1f - nw)
        val ny = snap(widget.y, ch).coerceIn(0f, 1f - nh)
        return widget.copy(x = nx, y = ny, w = nw, h = nh).clamped(aspectWH, cols)
    }

    fun bumpSize(
        widget: TactileWidget,
        deltaWCells: Int,
        deltaHCells: Int,
        aspectWH: Float,
        columns: Int = DEFAULT_COLUMNS,
    ): TactileWidget {
        val cols = normalizeColumns(columns)
        val cw = cellWidth(cols)
        val ch = cellHeight(aspectWH, cols)
        val wCells = (cellsAlong(widget.w, cw) + deltaWCells).coerceAtLeast(1)
        val hCells = (cellsAlong(widget.h, ch) + deltaHCells).coerceAtLeast(1)
        return snapWidget(
            widget.copy(w = wCells * cw, h = hCells * ch),
            aspectWH,
            cols,
        )
    }
}

/** 12 Mapping color presets (idle fill). */
val TACTILE_COLOR_PRESETS: List<String> = listOf(
    "#3DDC84",
    "#2EC4B6",
    "#E8B84A",
    "#22D3EE",
    "#A3E635",
    "#C4B5FD",
    "#4BA3F0",
    "#FB7185",
    "#F97316",
    "#E8EEF5",
    "#64748B",
    "#1E293B",
)

fun defaultFillHex(kind: TactileKind): String = when (kind) {
    TactileKind.TRIGGER -> "#3DDC84"
    TactileKind.FADER -> "#4BA3F0"
}

/** Normalize user/preset hex to `#RRGGBB` or null if invalid. */
fun normalizeFillHex(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    var s = raw.trim().uppercase()
    if (s.startsWith("#")) s = s.substring(1)
    if (s.length == 8) s = s.substring(2) // drop alpha AARRGGBB → RRGGBB
    if (s.length == 3) {
        s = buildString {
            s.forEach { c -> append(c); append(c) }
        }
    }
    if (s.length != 6 || s.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
    return "#$s"
}

fun parseFillArgb(hex: String?): Int? {
    val n = normalizeFillHex(hex) ?: return null
    val rgb = n.substring(1).toIntOrNull(16) ?: return null
    return (0xFF shl 24) or rgb
}

/** Darken ARGB for pressed / toggle ON (factor 0…1 keeps original). */
fun darkenArgb(argb: Int, factor: Float = 0.55f): Int {
    val f = factor.coerceIn(0.05f, 1f)
    val a = (argb ushr 24) and 0xFF
    val r = (((argb ushr 16) and 0xFF) * f).roundToInt().coerceIn(0, 255)
    val g = (((argb ushr 8) and 0xFF) * f).roundToInt().coerceIn(0, 255)
    val b = ((argb and 0xFF) * f).roundToInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

fun hsvToRgbHex(h: Float, s: Float, v: Float): String {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val value = v.coerceIn(0f, 1f)
    val c = value * sat
    val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val m = value - c
    val (rp, gp, bp) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val r = ((rp + m) * 255f).roundToInt().coerceIn(0, 255)
    val g = ((gp + m) * 255f).roundToInt().coerceIn(0, 255)
    val b = ((bp + m) * 255f).roundToInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

fun rgbHexToHsv(hex: String): Triple<Float, Float, Float>? {
    val argb = parseFillArgb(hex) ?: return null
    val r = ((argb ushr 16) and 0xFF) / 255f
    val g = ((argb ushr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == r -> (60f * ((g - b) / d) + 360f) % 360f
        max == g -> (60f * ((b - r) / d) + 120f) % 360f
        else -> (60f * ((r - g) / d) + 240f) % 360f
    }
    val s = if (max == 0f) 0f else d / max
    return Triple(h, s, max)
}
