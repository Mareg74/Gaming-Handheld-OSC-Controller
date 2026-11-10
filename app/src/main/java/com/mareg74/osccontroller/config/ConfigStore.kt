package com.mareg74.osccontroller.config

interface KeyValuePrefs {
    fun getString(k: String, d: String): String
    fun putString(k: String, v: String)
    fun remove(k: String)
    fun clear()
}

class ConfigStore(private val prefs: KeyValuePrefs) {
    var lastLoadRecovered: Boolean = false
        private set

    fun load(): AppConfig {
        lastLoadRecovered = false
        val raw = prefs.getString(KEY, "")
        if (raw.isBlank()) return AppConfig()
        return try {
            ConfigCodec.parse(raw)
        } catch (_: Exception) {
            lastLoadRecovered = true
            resetToDefaults()
            AppConfig()
        }
    }

    fun save(config: AppConfig) = prefs.putString(KEY, ConfigCodec.serialize(config))

    fun resetToDefaults() {
        prefs.remove(KEY)
        save(AppConfig())
    }

    companion object {
        const val KEY = "app_config_v1"

        /** Legacy digital L2/R2 → analog trigger control ids. */
        fun migrateControlId(id: String): String = when (id) {
            "BTN_L2" -> "AXIS_L2"
            "BTN_R2" -> "AXIS_R2"
            else -> id
        }

        fun fromSharedPreferences(sp: android.content.SharedPreferences): ConfigStore =
            ConfigStore(object : KeyValuePrefs {
                override fun getString(k: String, d: String) = sp.getString(k, d) ?: d
                override fun putString(k: String, v: String) {
                    sp.edit().putString(k, v).apply()
                }
                override fun remove(k: String) {
                    sp.edit().remove(k).apply()
                }
                override fun clear() {
                    sp.edit().clear().apply()
                }
            })
    }
}
