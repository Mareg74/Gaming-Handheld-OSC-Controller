package com.mareg74.osccontroller.config

import java.util.UUID

data class ConfigProjectMeta(
    val id: String,
    val name: String,
    val updatedAtMs: Long,
)

data class ConfigProject(
    val meta: ConfigProjectMeta,
    val config: AppConfig,
)

/** Which slices of a saved project to apply onto the live config. */
enum class ProjectPart {
    OSC_NETWORK,
    MAPPING,
    CURVES,
    CALIBRATION,
    DISPLAY,
}

fun AppConfig.applyProjectParts(source: AppConfig, parts: Set<ProjectPart>): AppConfig {
    if (parts.isEmpty()) return this
    var next = this
    if (ProjectPart.OSC_NETWORK in parts) {
        next = next.copy(
            targetIp = source.targetIp,
            targetPort = source.targetPort,
            listenPort = source.listenPort,
        )
    }
    if (ProjectPart.MAPPING in parts) {
        next = next.copy(
            activePage = source.activePage,
            bottomPlayPage = source.bottomPlayPage,
            pageCount = source.pageCount,
            mappings = source.mappings,
            buttonNotes = source.buttonNotes,
            pageNames = source.pageNames,
            pageTypes = source.pageTypes,
            widgets = source.widgets,
            tactileGridColumns = source.tactileGridColumns,
            customColorHistory = source.customColorHistory,
        ).withClampedActivePage()
    }
    if (ProjectPart.CURVES in parts) {
        next = next.copy(
            stickCurve = source.stickCurve,
            triggerCurve = source.triggerCurve,
        )
    }
    if (ProjectPart.CALIBRATION in parts) {
        next = next.copy(
            stickLeftFull = source.stickLeftFull,
            stickRightFull = source.stickRightFull,
        )
    }
    if (ProjectPart.DISPLAY in parts) {
        next = next.copy(
            forceSingleDisplay = source.forceSingleDisplay,
            themeMode = source.themeMode,
            colorPalette = source.colorPalette,
        )
    }
    return next
}

class ProjectStore(private val prefs: KeyValuePrefs) {
    fun list(): List<ConfigProjectMeta> {
        val raw = prefs.getString(INDEX_KEY, "")
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .mapNotNull { line ->
                val p = line.split("|", limit = 3)
                if (p.size < 3) return@mapNotNull null
                val id = p[0].trim()
                val name = p[1].trim()
                val ts = p[2].toLongOrNull() ?: return@mapNotNull null
                if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
                ConfigProjectMeta(id, name, ts)
            }
            .sortedByDescending { it.updatedAtMs }
            .toList()
    }

    fun get(id: String): ConfigProject? {
        val meta = list().find { it.id == id } ?: return null
        val body = prefs.getString(bodyKey(id), "")
        if (body.isBlank()) return null
        return try {
            ConfigProject(meta, ConfigCodec.parse(body))
        } catch (_: Exception) {
            null
        }
    }

    /** Create or overwrite a project with the given name from [config]. */
    fun save(name: String, config: AppConfig, existingId: String? = null): ConfigProjectMeta {
        val trimmed = name.trim().ifBlank { "Projet" }
        val id = existingId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val meta = ConfigProjectMeta(id, trimmed, now)
        prefs.putString(bodyKey(id), ConfigCodec.serialize(config))
        val others = list().filter { it.id != id }
        writeIndex(others + meta)
        return meta
    }

    fun rename(id: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        val current = list()
        val target = current.find { it.id == id } ?: return false
        writeIndex(
            current.map {
                if (it.id == id) it.copy(name = trimmed, updatedAtMs = System.currentTimeMillis())
                else it
            }
        )
        // Touch body timestamp via re-save of existing body if present
        val body = prefs.getString(bodyKey(id), "")
        if (body.isNotBlank()) prefs.putString(bodyKey(id), body)
        return target.name != trimmed || true
    }

    fun delete(id: String) {
        prefs.remove(bodyKey(id))
        writeIndex(list().filter { it.id != id })
        if (activeId() == id) clearActiveId()
    }

    /** Id of the project currently open / saved as live, or null if none. */
    fun activeId(): String? = prefs.getString(ACTIVE_KEY, "").ifBlank { null }

    fun setActiveId(id: String?) {
        val trimmed = id?.trim().orEmpty()
        if (trimmed.isEmpty()) prefs.remove(ACTIVE_KEY)
        else prefs.putString(ACTIVE_KEY, trimmed)
    }

    fun clearActiveId() = setActiveId(null)

    fun isActive(id: String): Boolean = activeId() == id

    /** Snapshot of the live config so open/import never loses the previous state. */
    fun saveAutosave(config: AppConfig): ConfigProjectMeta {
        val existing = list().find { it.name.equals(AUTOSAVE_NAME, ignoreCase = true) }
        return save(AUTOSAVE_NAME, config, existingId = existing?.id)
    }

    private fun writeIndex(items: List<ConfigProjectMeta>) {
        val lines = items
            .sortedByDescending { it.updatedAtMs }
            .joinToString("\n") { "${it.id}|${it.name.replace('|', '/')}|${it.updatedAtMs}" }
        prefs.putString(INDEX_KEY, lines)
    }

    companion object {
        const val INDEX_KEY = "proj_index_v1"
        const val ACTIVE_KEY = "proj_active_id_v1"
        const val AUTOSAVE_NAME = "Autosave"
        fun bodyKey(id: String) = "proj_body_$id"

        fun fromSharedPreferences(sp: android.content.SharedPreferences): ProjectStore =
            ProjectStore(object : KeyValuePrefs {
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
