package com.mareg74.osccontroller.config

/**
 * Digital button modes send OSC **Int** payloads (Resolume expects 0/1).
 * Legacy configs stored `BANG` — [parseArgKind] migrates to [MOMENTARY].
 */
enum class ArgKind { MOMENTARY, TOGGLE, FLOAT }

/** Parse stored/catalog arg kinds; maps legacy `BANG` → [ArgKind.MOMENTARY]. */
fun parseArgKind(raw: String): ArgKind = when (raw.trim()) {
    "BANG" -> ArgKind.MOMENTARY
    else -> ArgKind.valueOf(raw.trim())
}

fun ArgKind.chipLabel(): String = when (this) {
    ArgKind.MOMENTARY -> "Momentary"
    ArgKind.TOGGLE -> "Toggle"
    ArgKind.FLOAT -> "FLOAT"
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Material3 color families selectable in Réglages → Affichage. */
enum class ColorPalette {
    TEAL_SCENE,
    PUPITRE_AMBER,
    CYAN_BROADCAST,
    LIME_CLUB,
    VIOLET_SCENE,
    ;

    fun labelFr(): String = when (this) {
        TEAL_SCENE -> "Teal scène"
        PUPITRE_AMBER -> "Pupitre amber"
        CYAN_BROADCAST -> "Cyan broadcast"
        LIME_CLUB -> "Lime club"
        VIOLET_SCENE -> "Violet scène"
    }
}

/** Physical gamepad page vs freeform tactile widgets. */
enum class PageType { PHYSICAL, TACTILE }

enum class TactileKind { TRIGGER, FADER }

/**
 * Free-layout control on a [PageType.TACTILE] page.
 * OSC binding lives in [ControlMapping] with [ControlMapping.controlId] == [id].
 * Geometry is normalized 0…1 relative to the Play/Mapping canvas.
 */
data class TactileWidget(
    val id: String,
    val page: Int,
    val kind: TactileKind,
    val label: String = "",
    val x: Float = 0.05f,
    val y: Float = 0.05f,
    val w: Float = 0.22f,
    val h: Float = 0.18f,
    /**
     * Idle fill as `#RRGGBB`. Null/blank → type default (trigger green / fader blue).
     * Pressed / toggle ON are auto-darkened from this fill.
     */
    val fillColor: String? = null,
) {
    /** Effective idle fill hex (`#RRGGBB`). */
    fun resolvedFillHex(): String =
        normalizeFillHex(fillColor) ?: defaultFillHex(kind)

    /**
     * Clamp to canvas. Min size = 1×1 grid cell ([columns] cols; row from [aspectWH]).
     * [aspectWH] = canvas width/height; default 16:9 when unknown.
     */
    fun clamped(
        aspectWH: Float = 16f / 9f,
        columns: Int = TactileGrid.DEFAULT_COLUMNS,
    ): TactileWidget {
        val cols = TactileGrid.normalizeColumns(columns)
        val cw = TactileGrid.cellWidth(cols)
        val ch = TactileGrid.cellHeight(aspectWH, cols)
        val nw = w.coerceIn(cw, 1f)
        val nh = h.coerceIn(ch, 1f)
        return copy(
            x = x.coerceIn(0f, 1f - nw),
            y = y.coerceIn(0f, 1f - nh),
            w = nw,
            h = nh,
            label = label.trim(),
            fillColor = normalizeFillHex(fillColor),
        )
    }
}

/** Max OSC addresses per control (primary + extras). */
const val MAX_MAPPING_ADDRESSES = 4

data class ControlMapping(
    val controlId: String,
    val address: String,
    val argKind: ArgKind,
    val page: Int = 0,
    /**
     * Extra OSC addresses fired with the same arg semantics as [address]
     * (Momentary off-all / Toggle flip-all). Total with [address] ≤ [MAX_MAPPING_ADDRESSES].
     */
    val extraAddresses: List<String> = emptyList(),
    /**
     * Fixed FLOAT payload for digital buttons mapped as FLOAT (Play press + Test).
     * Ignored for analog axes. Always kept in 0…1.
     */
    val floatValue: Float = 1f,
    /**
     * Optional output range for analog axes (sticks / triggers) mapped as FLOAT.
     * When both are set, hardware stick −1…1 or trigger 0…1 is remapped into [floatMin, floatMax]
     * (free range, may be outside 0…1). When null, the hardware value is sent as-is.
     */
    val floatMin: Float? = null,
    val floatMax: Float? = null,
    /**
     * When true, Momentary/Toggle use [onValue]/[offValue] ints.
     * Invert UI is hidden; [invert] is ignored for payload.
     */
    val customValues: Boolean = false,
    val onValue: Int = 1,
    val offValue: Int = 0,
    /**
     * INT 0/1 only: XOR logical on/off for payload (and toggle latch semantics).
     * Ignored when [customValues] is true.
     */
    val invert: Boolean = false,
    /** Persisted Toggle latch (logical, before invert). Saved in config/projet. */
    val toggleOn: Boolean = false,
) {
    /** Primary + extras, trimmed, non-empty, capped. */
    fun allAddresses(): List<String> =
        (listOf(address) + extraAddresses)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_MAPPING_ADDRESSES)

    /** Effective invert flag (never when custom). */
    fun invertActive(): Boolean = invert && !customValues

    /**
     * Logical ON for Toggle UI / payload (latch xor invert).
     * Play color should follow this, not hardware press flash.
     */
    fun toggleLogicalOn(): Boolean = toggleOn xor invertActive()

    /** OSC int for a logical on/off after optional invert. */
    fun digitalInt(logicalOn: Boolean): Int {
        val effectiveOn = logicalOn xor invertActive()
        return if (customValues) {
            if (effectiveOn) onValue else offValue
        } else {
            if (effectiveOn) 1 else 0
        }
    }
}

/** Mapping for [controlId] on [page], or null. */
fun AppConfig.mappingOn(page: Int, controlId: String): ControlMapping? =
    mappings.firstOrNull { it.controlId == controlId && it.page == page }

/**
 * Play highlight: Toggle stays lit from logical state; other modes use hardware down.
 */
fun AppConfig.playDigitalLit(page: Int, controlId: String, hardwareDown: Boolean): Boolean {
    val m = mappingOn(page, controlId) ?: return hardwareDown
    return when (m.argKind) {
        ArgKind.TOGGLE -> m.toggleLogicalOn()
        else -> hardwareDown
    }
}

data class AppConfig(
    val targetIp: String = "127.0.0.1",
    val targetPort: Int = 7000,
    val listenPort: Int = 7001,
    val activePage: Int = 0,
    /**
     * Play page on the bottom screen (independent from [activePage] / top Play / gamepad).
     */
    val bottomPlayPage: Int = 0,
    /** Number of mapping pages (contiguous indices 0..<pageCount). Always ≥ 1. */
    val pageCount: Int = 1,
    val mappings: List<ControlMapping> = emptyList(),
    /**
     * Per-page Play/Mapping legends. Keys are [noteStorageKey] (`"$page|$controlId"`).
     * Legacy flat keys without page are migrated to page 0 on load.
     */
    val buttonNotes: Map<String, String> = emptyMap(),
    /** When true, hide Presentation and show Monitor as a primary tab. */
    val forceSingleDisplay: Boolean = false,
    /** Follow system, or force light / dark. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Color family (independent of [themeMode] light/dark). Default A · Teal scène. */
    val colorPalette: ColorPalette = ColorPalette.TEAL_SCENE,
    /**
     * Stick response power curve. 1 = linear, higher = softer mid-throw.
     * Typical range 1.0…4.0 (default 3.2).
     */
    val stickCurve: Float = 3.2f,
    /**
     * Trigger response power curve. 1 = linear, higher = softer pull.
     * Typical range 1.0…3.0 (default 1.8).
     */
    val triggerCurve: Float = 1.8f,
    /**
     * Peak raw magnitude measured at physical left-stick rail (0.55…1).
     * Remaps so this value becomes OSC ±1. Default 1 = no remap.
     */
    val stickLeftFull: Float = 1f,
    /**
     * Peak raw magnitude measured at physical right-stick rail (0.55…1).
     */
    val stickRightFull: Float = 1f,
    /** Optional display names for mapping pages (index → label). */
    val pageNames: Map<Int, String> = emptyMap(),
    /** Missing keys default to [PageType.PHYSICAL]. */
    val pageTypes: Map<Int, PageType> = emptyMap(),
    /** Widgets for tactile pages. */
    val widgets: List<TactileWidget> = emptyList(),
    /**
     * Mapping tactile snap grid density (columns). One of 8 / 12 / 16 / 24 / 32 / 64.
     * Persisted per project with the config snapshot.
     */
    val tactileGridColumns: Int = TactileGrid.DEFAULT_COLUMNS,
    /** Monitor: show OSC received (Resolume → Thor). */
    val monitorShowIn: Boolean = true,
    /** Monitor: show OSC sent (Thor → Resolume). */
    val monitorShowOut: Boolean = true,
    /** Monitor: only addresses present in [mappings]. */
    val monitorMappedOnly: Boolean = false,
    /** Monitor: free-text contains filter (address + args). */
    val monitorFilterContains: String = "",
    /** Monitor: multi-select address chips (empty = no chip filter). */
    val monitorFilterAddresses: Set<String> = emptySet(),
    /**
     * Monitor log line text size step (0…4). Default 1 ≈ current 10/11sp.
     */
    val monitorLineSize: Int = 1,
    /**
     * Last custom fill colors picked in Mapping (most recent first). Cap [CUSTOM_COLOR_HISTORY_MAX].
     * Persisted with the project config snapshot.
     */
    val customColorHistory: List<String> = emptyList(),
) {
    companion object {
        const val CUSTOM_COLOR_HISTORY_MAX = 6
    }
    fun mappedOscAddresses(): Set<String> =
        mappings.flatMap { it.allAddresses() }.toSet()

    fun pageLabel(page: Int): String {
        val custom = pageNames[page]?.trim().orEmpty()
        return if (custom.isNotEmpty()) custom else "Page $page"
    }

    fun pageType(page: Int): PageType = pageTypes[page] ?: PageType.PHYSICAL

    fun widgetsOn(page: Int): List<TactileWidget> = widgets.filter { it.page == page }

    fun tactilePageIndices(): List<Int> =
        (0..lastPageIndex()).filter { pageType(it) == PageType.TACTILE }

    fun physicalPageIndices(): List<Int> =
        (0..lastPageIndex()).filter { pageType(it) == PageType.PHYSICAL }

    /**
     * Page used for hardware pad/stick OSC.
     * When Play shows a tactile page, pad mappings still live on a PHYSICAL page
     * (nearest below [activePage], else first physical). QCM 2A.
     */
    fun padHardwarePage(): Int {
        if (pageType(activePage) == PageType.PHYSICAL) return activePage
        for (i in activePage - 1 downTo 0) {
            if (pageType(i) == PageType.PHYSICAL) return i
        }
        return physicalPageIndices().firstOrNull() ?: 0
    }

    fun lastPageIndex(): Int = (pageCount - 1).coerceAtLeast(0)

    fun withClampedActivePage(): AppConfig =
        copy(
            activePage = activePage.coerceIn(0, lastPageIndex()),
            bottomPlayPage = bottomPlayPage.coerceIn(0, lastPageIndex()),
        )

    /** Append an empty page and select it. Optional [name] overrides the default label. */
    fun addPage(name: String? = null, type: PageType = PageType.PHYSICAL): AppConfig {
        val newIndex = pageCount
        val label = name?.trim().orEmpty().ifBlank { "Page $newIndex" }
        return copy(
            pageCount = pageCount + 1,
            activePage = newIndex,
            pageNames = pageNames + (newIndex to label),
            pageTypes = pageTypes + (newIndex to type),
        )
    }

    /** Duplicate [from] mappings + name (+ legends) onto a new page at the end. */
    fun copyPage(from: Int = activePage): AppConfig {
        val src = from.coerceIn(0, lastPageIndex())
        val newIndex = pageCount
        val type = pageType(src)
        val idMap = mutableMapOf<String, String>()
        val copiedWidgets = if (type == PageType.TACTILE) {
            widgetsOn(src).map { w ->
                val newId = java.util.UUID.randomUUID().toString()
                idMap[w.id] = newId
                w.copy(id = newId, page = newIndex)
            }
        } else {
            emptyList()
        }
        val copiedMaps = mappings.filter { it.page == src }.map { m ->
            m.copy(
                page = newIndex,
                controlId = idMap[m.controlId] ?: m.controlId,
            )
        }
        val copiedNotes = buttonNotes.mapNotNull { (key, value) ->
            val (page, controlId) = parseNoteStorageKey(key) ?: return@mapNotNull null
            if (page != src) return@mapNotNull null
            val newControl = idMap[controlId] ?: controlId
            noteStorageKey(newIndex, newControl) to value
        }.toMap()
        val baseName = pageNames[src]?.trim().orEmpty().ifBlank { "Page $src" }
        return copy(
            pageCount = pageCount + 1,
            activePage = newIndex,
            mappings = mappings + copiedMaps,
            buttonNotes = buttonNotes + copiedNotes,
            pageNames = pageNames + (newIndex to "$baseName (copie)"),
            pageTypes = pageTypes + (newIndex to type),
            widgets = widgets + copiedWidgets,
        )
    }

    /**
     * Delete [index], shift higher pages down. No-op if only one page remains.
     */
    fun deletePage(index: Int = activePage): AppConfig {
        if (pageCount <= 1) return this
        val victim = index.coerceIn(0, lastPageIndex())
        val newMaps = mappings.mapNotNull { m ->
            when {
                m.page == victim -> null
                m.page > victim -> m.copy(page = m.page - 1)
                else -> m
            }
        }
        val newNames = pageNames
            .filterKeys { it != victim }
            .mapKeys { (k, _) -> if (k > victim) k - 1 else k }
        val newTypes = pageTypes
            .filterKeys { it != victim }
            .mapKeys { (k, _) -> if (k > victim) k - 1 else k }
        val newWidgets = widgets.mapNotNull { w ->
            when {
                w.page == victim -> null
                w.page > victim -> w.copy(page = w.page - 1)
                else -> w
            }
        }
        val newNotes = buttonNotes.mapNotNull { (key, value) ->
            val parsed = parseNoteStorageKey(key)
            if (parsed == null) {
                // Legacy flat key — treat as page 0.
                if (victim == 0) null else key to value
            } else {
                val (page, controlId) = parsed
                when {
                    page == victim -> null
                    page > victim -> noteStorageKey(page - 1, controlId) to value
                    else -> key to value
                }
            }
        }.toMap()
        val newActive = when {
            activePage > victim -> activePage - 1
            activePage == victim -> (victim - 1).coerceAtLeast(0)
            else -> activePage
        }
        val newBottom = when {
            bottomPlayPage > victim -> bottomPlayPage - 1
            bottomPlayPage == victim -> (victim - 1).coerceAtLeast(0)
            else -> bottomPlayPage
        }
        return copy(
            pageCount = pageCount - 1,
            activePage = newActive.coerceIn(0, pageCount - 2),
            bottomPlayPage = newBottom.coerceIn(0, pageCount - 2),
            mappings = newMaps,
            buttonNotes = newNotes,
            pageNames = newNames,
            pageTypes = newTypes,
            widgets = newWidgets,
        )
    }

    fun upsertWidget(widget: TactileWidget): AppConfig {
        val next = widget.clamped(columns = tactileGridColumns)
        val without = widgets.filterNot { it.id == next.id }
        return copy(widgets = without + next)
    }

    /** Prepend [hex] to custom history (deduped, max [CUSTOM_COLOR_HISTORY_MAX]). */
    fun pushCustomColor(hex: String): AppConfig {
        val n = normalizeFillHex(hex) ?: return this
        val next = listOf(n) + customColorHistory.filter {
            !it.equals(n, ignoreCase = true)
        }
        return copy(customColorHistory = next.take(CUSTOM_COLOR_HISTORY_MAX))
    }

    fun withTactileGridColumns(columns: Int): AppConfig =
        copy(tactileGridColumns = TactileGrid.normalizeColumns(columns))

    fun removeWidget(widgetId: String): AppConfig =
        copy(
            widgets = widgets.filterNot { it.id == widgetId },
            mappings = mappings.filterNot { it.controlId == widgetId },
            buttonNotes = buttonNotes.filterKeys { key ->
                parseNoteStorageKey(key)?.second != widgetId
            },
        )

    /** Legend for [controlId] on [page], or empty. */
    fun noteFor(page: Int, controlId: String): String =
        buttonNotes[noteStorageKey(page, controlId)].orEmpty()

    /** Set/clear legend for [controlId] on [page]. */
    fun withNote(page: Int, controlId: String, note: String): AppConfig {
        val key = noteStorageKey(page, controlId)
        val next = if (note.isBlank()) buttonNotes - key else buttonNotes + (key to note.trim())
        return copy(buttonNotes = next)
    }
}

/** Storage key for per-page button legends. */
fun noteStorageKey(page: Int, controlId: String): String = "$page|$controlId"

/** Parse [noteStorageKey]; null if not in `page|controlId` form. */
fun parseNoteStorageKey(key: String): Pair<Int, String>? {
    val sep = key.indexOf('|')
    if (sep <= 0) return null
    val page = key.substring(0, sep).toIntOrNull() ?: return null
    val controlId = key.substring(sep + 1)
    if (controlId.isEmpty()) return null
    return page to controlId
}
