package com.mareg74.osccontroller.ndi

enum class NdiStatus { IDLE, DISCOVERING, CONNECTING, LIVE, PAUSED, ERROR }

data class NdiSource(val name: String, val url: String)

/**
 * Receiver / display limits.
 *
 * - [quality] 0–49 → NDI bandwidth lowest; 50–100 → highest (full res from source).
 * - [maxWidth]/[maxHeight] 0 = no client downscale (native received size); else cap 160…4096.
 * - [maxFps] 0 = no display throttle; else 1…60.
 * - [audioEnabled] when true, NDI audio is played via AudioTrack.
 */
data class NdiSettings(
    val quality: Int = 100,
    val maxWidth: Int = 0,
    val maxHeight: Int = 0,
    val maxFps: Int = 60,
    val audioEnabled: Boolean = false,
) {
    fun clamped(): NdiSettings = NdiSettings(
        quality = clampQuality(quality),
        maxWidth = clampSize(maxWidth),
        maxHeight = clampSize(maxHeight),
        maxFps = clampFps(maxFps),
        audioEnabled = audioEnabled,
    )

    companion object {
        /** Client-side: do not downscale (keep NDI received resolution). */
        const val SIZE_NATIVE = 0
        /** Display: emit every received frame (no FPS cap). */
        const val FPS_UNLIMITED = 0

        fun clampQuality(q: Int): Int = q.coerceIn(0, 100)

        /** 0 = native; otherwise 160…4096. */
        fun clampSize(v: Int): Int = when {
            v <= 0 -> SIZE_NATIVE
            else -> v.coerceIn(160, 4096)
        }

        /** 0 = unlimited; otherwise 1…60. */
        fun clampFps(f: Int): Int = when {
            f <= 0 -> FPS_UNLIMITED
            else -> f.coerceIn(1, 60)
        }

        /** Matches NDI SDK: only lowest vs highest bandwidth. */
        fun useHighestBandwidth(quality: Int): Boolean = clampQuality(quality) >= 50
    }
}

data class NdiPrefsData(
    val lastSourceUrl: String? = null,
    val lastSourceName: String? = null,
    val settings: NdiSettings = NdiSettings(),
)

/** Optional ARGB8888 frame for UI / fake receiver. */
data class NdiFrame(
    val width: Int,
    val height: Int,
    val argb: IntArray,
    val timestampNs: Long = 0L,
)
