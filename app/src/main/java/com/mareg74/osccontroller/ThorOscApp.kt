package com.mareg74.osccontroller

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.mareg74.osccontroller.catalog.CatalogEntry
import com.mareg74.osccontroller.catalog.ResolumeCatalog
import com.mareg74.osccontroller.config.AppConfig
import com.mareg74.osccontroller.config.ArgKind
import com.mareg74.osccontroller.config.ConfigCodec
import com.mareg74.osccontroller.config.ConfigStore
import com.mareg74.osccontroller.config.ControlMapping
import com.mareg74.osccontroller.config.KeyValuePrefs
import com.mareg74.osccontroller.config.TactileKind
import com.mareg74.osccontroller.ndi.NdiPrefs
import com.mareg74.osccontroller.ndi.NdiReceiverFactory
import com.mareg74.osccontroller.ndi.NdiSession
import com.mareg74.osccontroller.config.ProjectPart
import com.mareg74.osccontroller.config.ProjectStore
import com.mareg74.osccontroller.config.applyProjectParts
import com.mareg74.osccontroller.feedback.FeedbackStore
import com.mareg74.osccontroller.mapping.MappingEngine
import com.mareg74.osccontroller.net.OscUdpReceiver
import com.mareg74.osccontroller.net.OscUdpSender
import com.mareg74.osccontroller.ui.MonitorLog

class ThorOscApp(context: Context) {
    val log = MonitorLog()
    val feedback = FeedbackStore()
    /**
     * Live tactile fader fills shared across Primary + Presentation Compose trees.
     * Same widget id → same visual level when both screens show the same Play page.
     */
    val tactileFaderLevels: SnapshotStateMap<String, Float> = mutableStateMapOf()
    /**
     * Live tactile Toggle lit state (logical ON) shared across both screens.
     * Keyed by widget id only — same widget stays lit/unlit everywhere (QCM 1B 2A).
     */
    val tactileToggleLit: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    /**
     * Quick OSC monitor line under the Play page switcher (label → address [value]).
     * Shared so Primary + Presentation stay in sync on the same page.
     */
    val playOscMonitor = mutableStateOf("")
    /**
     * Bumped whenever tactile widget geometry/list changes so Primary + Presentation
     * Compose trees (Play and Mapping) re-read [config.widgets] without relying only
     * on per-editor local epochs / contentEpoch plumbing.
     */
    val widgetsRevision = mutableIntStateOf(0)

    fun noteWidgetsChanged() {
        widgetsRevision.intValue++
    }

    /** Refresh [tactileToggleLit] from persisted Toggle mappings (widget ids). */
    fun hydrateTactileToggleLit() {
        val widgetIds = config.widgets.map { it.id }.toSet()
        val toggles = config.mappings.filter {
            it.argKind == ArgKind.TOGGLE && it.controlId in widgetIds
        }
        val keep = toggles.map { it.controlId }.toSet()
        tactileToggleLit.keys.filter { it !in keep }.toList().forEach { tactileToggleLit.remove(it) }
        toggles.forEach { m ->
            tactileToggleLit[m.controlId] = m.toggleLogicalOn()
        }
    }

    private fun noteToggleLit(mapping: ControlMapping) {
        if (mapping.argKind != ArgKind.TOGGLE) return
        if (config.widgets.none { it.id == mapping.controlId }) return
        tactileToggleLit[mapping.controlId] = mapping.toggleLogicalOn()
    }

    private val prefs = context.getSharedPreferences("thor_osc", Context.MODE_PRIVATE)
    private val kv = object : KeyValuePrefs {
        override fun getString(k: String, d: String) = prefs.getString(k, d) ?: d
        override fun putString(k: String, v: String) {
            prefs.edit().putString(k, v).apply()
        }
        override fun remove(k: String) {
            prefs.edit().remove(k).apply()
        }
        override fun clear() {
            prefs.edit().clear().apply()
        }
    }
    val ndiPrefs = NdiPrefs(kv)
    val ndiSession = NdiSession(NdiReceiverFactory.create(), ndiPrefs)
    val configStore = ConfigStore.fromSharedPreferences(prefs)
    val projectStore = ProjectStore.fromSharedPreferences(prefs)
    var config = configStore.load()
    val configLoadRecovered: Boolean = configStore.lastLoadRecovered
    val engine = MappingEngine()
    val sender = OscUdpSender(log)
    val receiver = OscUdpReceiver(feedback, log)
    val catalog: List<CatalogEntry> =
        ResolumeCatalog.parse(
            context.assets.open("resolume_arena_catalog.json").bufferedReader().readText()
        )

    init {
        hydrateTactileToggleLit()
    }

    fun persist() = configStore.save(config)

    /**
     * Primary / Presentation drawer destinations — used to mute hardware pad OSC
     * while a Mapping screen is open (QCM: pad works on tactile Play, not Mapping).
     */
    @Volatile var primaryDestination: String = "Play"
    @Volatile var secondaryDestination: String = "Play Triggers"

    fun mappingScreenOpen(): Boolean =
        primaryDestination == "Mapping Triggers" ||
            secondaryDestination == "Mapping Triggers"

    /**
     * Hardware pad/stick OSC. Resolves against [AppConfig.padHardwarePage] so pad
     * keeps working while a tactile Play page is open. Same OSC address as a tactile
     * widget → both may send. Returns null when muted (Mapping) or unmapped.
     */
    fun emitHardwareControl(
        controlId: String,
        value: Float,
        continuous: Boolean,
        bipolarHardware: Boolean = false,
    ): String? {
        if (mappingScreenOpen()) return null
        return emitControl(
            controlId = controlId,
            value = value,
            continuous = continuous,
            bipolarHardware = bipolarHardware,
            page = config.padHardwarePage(),
        )
    }

    /**
     * Emit OSC for a mapped control id (gamepad name or tactile widget id).
     * Persists Toggle latch updates. Returns a short status line, or null if unmapped / no message.
     */
    fun emitControl(
        controlId: String,
        value: Float,
        continuous: Boolean,
        bipolarHardware: Boolean = false,
        page: Int? = null,
    ): String? {
        val result = engine.resolveResult(
            config = config,
            controlId = controlId,
            value = value,
            continuous = continuous,
            bipolarHardware = bipolarHardware,
            page = page,
        )
        result.updatedMapping?.let { upd ->
            config = config.copy(
                mappings = config.mappings.map { m ->
                    if (m.controlId == upd.controlId && m.page == upd.page) upd else m
                },
            )
            noteToggleLit(upd)
            persist()
        }
        val msgs = result.messages
        if (msgs.isEmpty()) return null
        msgs.forEach { sender.send(config, it) }
        val first = msgs.first()
        val arg = first.args.firstOrNull()?.let { " ($it)" }.orEmpty()
        val multi = if (msgs.size > 1) " ×${msgs.size}" else ""
        // Tactile mappings key on widget UUID — show human label, never the raw id.
        val label = controlDisplayLabel(controlId)
        return if (continuous) {
            "$label → ${first.address}$multi (${"%.3f".format(value)})"
        } else {
            "$label → ${first.address}$multi$arg"
        }
    }

    /** Widget label, or gamepad control name when [controlId] is not a tactile widget. */
    fun controlDisplayLabel(controlId: String): String {
        val widget = config.widgets.find { it.id == controlId } ?: return controlId
        val named = widget.label.trim()
        if (named.isNotEmpty()) return named
        return when (widget.kind) {
            TactileKind.FADER -> "Fader"
            TactileKind.TRIGGER -> "Trigger"
        }
    }

    fun restartReceiver() {
        receiver.start(config.listenPort)
    }

    /**
     * Persist the live config and keep an Autosave project snapshot.
     * Call before opening/importing another configuration.
     */
    fun safeguardCurrentConfig() {
        persist()
        projectStore.saveAutosave(config)
    }

    /** Apply selected parts from a saved project, persist, and restart OSC listen if network changed. */
    fun applyProject(projectConfig: AppConfig, parts: Set<ProjectPart>) {
        config = config.applyProjectParts(projectConfig, parts)
        hydrateTactileToggleLit()
        persist()
        if (ProjectPart.OSC_NETWORK in parts) restartReceiver()
    }

    fun exportConfigText(): String = ConfigCodec.serialize(config)

    fun importConfigText(raw: String): Boolean {
        val parsed = runCatching { ConfigCodec.parse(raw.trim()) }.getOrNull() ?: return false
        config = parsed
        hydrateTactileToggleLit()
        persist()
        restartReceiver()
        return true
    }
}
