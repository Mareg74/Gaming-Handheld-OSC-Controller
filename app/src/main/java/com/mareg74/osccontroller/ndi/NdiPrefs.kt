package com.mareg74.osccontroller.ndi

import com.mareg74.osccontroller.config.KeyValuePrefs

class NdiPrefs(private val prefs: KeyValuePrefs) {
    fun load(): NdiPrefsData {
        val raw = prefs.getString(KEY, "")
        if (raw.isBlank()) return NdiPrefsData()
        val map = raw.lineSequence()
            .map { it.trim() }
            .filter { it.contains('=') }
            .associate { line ->
                val i = line.indexOf('=')
                line.substring(0, i) to line.substring(i + 1)
            }
        val settings = NdiSettings(
            quality = NdiSettings.clampQuality(map["quality"]?.toIntOrNull() ?: 100),
            maxWidth = NdiSettings.clampSize(map["maxWidth"]?.toIntOrNull() ?: 0),
            maxHeight = NdiSettings.clampSize(map["maxHeight"]?.toIntOrNull() ?: 0),
            maxFps = NdiSettings.clampFps(map["maxFps"]?.toIntOrNull() ?: 60),
            audioEnabled = map["audio"] == "1",
        )
        return NdiPrefsData(
            lastSourceUrl = map["sourceUrl"]?.ifBlank { null },
            lastSourceName = map["sourceName"]?.ifBlank { null },
            settings = settings,
        )
    }

    fun save(data: NdiPrefsData) {
        val s = data.settings.clamped()
        val body = buildString {
            data.lastSourceUrl?.let { append("sourceUrl=").append(it).append('\n') }
            data.lastSourceName?.let { append("sourceName=").append(it).append('\n') }
            append("quality=").append(s.quality).append('\n')
            append("maxWidth=").append(s.maxWidth).append('\n')
            append("maxHeight=").append(s.maxHeight).append('\n')
            append("maxFps=").append(s.maxFps).append('\n')
            append("audio=").append(if (s.audioEnabled) "1" else "0").append('\n')
        }
        prefs.putString(KEY, body)
    }

    companion object {
        const val KEY = "ndi_prefs_v1"
    }
}
