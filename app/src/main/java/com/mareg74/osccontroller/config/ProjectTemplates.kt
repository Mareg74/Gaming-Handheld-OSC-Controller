package com.mareg74.osccontroller.config

import com.mareg74.osccontroller.mapping.ControlId
import java.util.UUID

/**
 * Factories for « Nouveau projet » : vide or template OSC basique.
 * Preserves network / curves / calib / display from [base] (usually live config).
 */
object ProjectTemplates {
    private val PROJECT_NUM = Regex("""^Projet\s+(\d+)$""", RegexOption.IGNORE_CASE)

    /** Live dump geom (grid 64) — 3 left faders + 4×4 trigger grid. */
    private data class Geom(val x: Float, val y: Float, val w: Float, val h: Float)

    private val FADER_GEOMS = listOf(
        Geom(0.0f, 0.0f, 0.09375f, 0.97218f),
        Geom(0.10938f, 0.0f, 0.09375f, 0.97218f),
        Geom(0.21875f, 0.0f, 0.09375f, 0.97218f),
    )
    private val FADER_COLORS = listOf("#AA00FF", "#22D3EE", null)

    private val TRIGGER_XS = listOf(0.32812f, 0.5f, 0.67188f, 0.84375f)
    private val TRIGGER_ROWS = listOf(
        Geom(0f, 0.0f, 0.15625f, 0.23332f),
        Geom(0f, 0.27221f, 0.15625f, 0.23332f),
        Geom(0f, 0.54442f, 0.15625f, 0.23332f),
        Geom(0f, 0.81663f, 0.15625f, 0.15555f),
    )
    private val TRIGGER_ROW_COLORS = listOf("#C4B5FD", "#2EC4B6", null, "#E8B84A")

    /** Next default name: `Projet N` based on existing project titles. */
    fun nextDefaultName(existingNames: Collection<String>): String {
        val max = existingNames.mapNotNull { name ->
            PROJECT_NUM.matchEntire(name.trim())?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: 0
        return "Projet ${max + 1}"
    }

    /** Empty mappings, single physical page. */
    fun empty(base: AppConfig = AppConfig()): AppConfig =
        base.copy(
            activePage = 0,
            bottomPlayPage = 0,
            pageCount = 1,
            mappings = emptyList(),
            buttonNotes = emptyMap(),
            pageNames = mapOf(0 to "Pad"),
            pageTypes = mapOf(0 to PageType.PHYSICAL),
            widgets = emptyList(),
        ).withClampedActivePage()

    /**
     * Pad physique (page 0) + page tactile (page 1) — layout live 3 faders + grille 4×4.
     * Pad : `/thor/BTN_…` MOMENTARY, `/thor/AXIS_…` FLOAT.
     * Tactile : rangée 1 TOGGLE `/thor/trigger/1…4`, rangées 2–4 MOMENTARY `/5…16`,
     * faders FLOAT `/thor/fader/1…3`. Grille 64 + [ColorPalette.VIOLET_SCENE].
     */
    fun basicOsc(base: AppConfig = AppConfig()): AppConfig {
        val physicalMaps = ControlId.entries.map { id ->
            val isAxis = id.name.startsWith("AXIS_")
            ControlMapping(
                controlId = id.name,
                address = "/thor/${id.name}",
                argKind = if (isAxis) ArgKind.FLOAT else ArgKind.MOMENTARY,
                page = 0,
                floatValue = 1f,
                floatMin = if (isAxis) {
                    if (id == ControlId.AXIS_L2 || id == ControlId.AXIS_R2) 0f else -1f
                } else null,
                floatMax = if (isAxis) 1f else null,
            )
        }
        val physicalNotes = ControlId.entries.associate { id ->
            noteStorageKey(0, id.name) to id.name
        }

        val widgets = mutableListOf<TactileWidget>()
        val tactileMaps = mutableListOf<ControlMapping>()
        val tactileNotes = mutableMapOf<String, String>()

        FADER_GEOMS.forEachIndexed { index, geom ->
            val n = index + 1
            val wid = UUID.randomUUID().toString()
            val label = "Fader $n"
            widgets += TactileWidget(
                id = wid,
                page = 1,
                kind = TactileKind.FADER,
                label = label,
                x = geom.x,
                y = geom.y,
                w = geom.w,
                h = geom.h,
                fillColor = FADER_COLORS[index],
            )
            tactileMaps += ControlMapping(
                controlId = wid,
                address = "${ThorDefaultOsc.FADER_PREFIX}$n",
                argKind = ArgKind.FLOAT,
                page = 1,
                floatMin = 0f,
                floatMax = 1f,
            )
            tactileNotes[noteStorageKey(1, wid)] = label
        }

        var momentaryIndex = 0
        TRIGGER_ROWS.forEachIndexed { row, rowGeom ->
            TRIGGER_XS.forEachIndexed { col, x ->
                val wid = UUID.randomUUID().toString()
                val isToggleRow = row == 0
                val label: String
                val address: String
                val kind: ArgKind
                if (isToggleRow) {
                    val n = col + 1
                    label = "Toggle $n"
                    address = "${ThorDefaultOsc.TRIGGER_PREFIX}$n"
                    kind = ArgKind.TOGGLE
                } else {
                    momentaryIndex += 1
                    val oscN = momentaryIndex + 4 // /thor/trigger/5…16
                    label = "Momentary $momentaryIndex"
                    address = "${ThorDefaultOsc.TRIGGER_PREFIX}$oscN"
                    kind = ArgKind.MOMENTARY
                }
                widgets += TactileWidget(
                    id = wid,
                    page = 1,
                    kind = TactileKind.TRIGGER,
                    label = label,
                    x = x,
                    y = rowGeom.y,
                    w = rowGeom.w,
                    h = rowGeom.h,
                    fillColor = TRIGGER_ROW_COLORS[row],
                )
                tactileMaps += ControlMapping(
                    controlId = wid,
                    address = address,
                    argKind = kind,
                    page = 1,
                )
                tactileNotes[noteStorageKey(1, wid)] = label
            }
        }

        return base.copy(
            activePage = 0,
            bottomPlayPage = 0,
            pageCount = 2,
            mappings = physicalMaps + tactileMaps,
            buttonNotes = physicalNotes + tactileNotes,
            pageNames = mapOf(0 to "Pad", 1 to "Tactile"),
            pageTypes = mapOf(0 to PageType.PHYSICAL, 1 to PageType.TACTILE),
            widgets = widgets,
            tactileGridColumns = 64,
            colorPalette = ColorPalette.VIOLET_SCENE,
        ).withClampedActivePage()
    }
}
