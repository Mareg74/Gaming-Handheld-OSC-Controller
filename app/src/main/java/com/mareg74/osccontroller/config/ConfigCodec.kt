package com.mareg74.osccontroller.config

/** Line-based encode/decode shared by live config and saved projects. */
object ConfigCodec {
    const val MONITOR_LINE_SIZE_MIN = 0
    const val MONITOR_LINE_SIZE_MAX = 4

    fun serialize(c: AppConfig): String {
        val maps = c.mappings.joinToString(";") {
            val extras = it.extraAddresses
                .map { a -> a.trim() }
                .filter { a -> a.isNotEmpty() }
                .take(MAX_MAPPING_ADDRESSES - 1)
                .joinToString(",") { a -> a.replace(',', ' ').replace('|', '/').replace(';', ',') }
            listOf(
                it.controlId,
                it.address,
                it.argKind.name,
                it.page,
                it.floatValue,
                it.floatMin ?: "",
                it.floatMax ?: "",
                if (it.customValues) "1" else "0",
                it.onValue,
                it.offValue,
                if (it.invert) "1" else "0",
                if (it.toggleOn) "1" else "0",
                extras,
            ).joinToString("|")
        }
        val notes = c.buttonNotes.entries.joinToString(";") { (key, value) ->
            val parsed = parseNoteStorageKey(key)
            val page = parsed?.first ?: 0
            val control = parsed?.second ?: key
            "$page|$control|${value.replace(';', ',')}"
        }
        val forceSingle = if (c.forceSingleDisplay) "1" else "0"
        val pageNames = c.pageNames.entries
            .sortedBy { it.key }
            .joinToString(";") { (idx, name) ->
                "$idx|${name.replace(';', ',').replace('|', '/')}"
            }
        val pageTypes = c.pageTypes.entries
            .sortedBy { it.key }
            .joinToString(";") { (idx, type) -> "$idx|${type.name}" }
        val widgets = c.widgets.joinToString(";") { w ->
            val label = w.label.replace(';', ',').replace('|', '/')
            val fill = normalizeFillHex(w.fillColor).orEmpty()
            "${w.id}|${w.page}|${w.kind.name}|$label|${w.x}|${w.y}|${w.w}|${w.h}|$fill"
        }
        return listOf(
            c.targetIp,
            c.targetPort,
            c.listenPort,
            c.activePage,
            maps,
            notes,
            forceSingle,
            c.themeMode.name,
            c.stickCurve,
            c.triggerCurve,
            c.stickLeftFull,
            c.stickRightFull,
            pageNames,
            c.pageCount,
            pageTypes,
            widgets,
            c.bottomPlayPage,
            serializeMonitorPrefs(c),
            c.colorPalette.name,
            TactileGrid.normalizeColumns(c.tactileGridColumns).toString(),
            c.customColorHistory
                .mapNotNull { normalizeFillHex(it) }
                .distinctBy { it.uppercase() }
                .take(AppConfig.CUSTOM_COLOR_HISTORY_MAX)
                .joinToString(","),
        ).joinToString("\n")
    }

    private fun serializeMonitorPrefs(c: AppConfig): String {
        val addrs = c.monitorFilterAddresses
            .sorted()
            .joinToString(",") { it.replace(',', ' ').replace('|', '/') }
        val contains = c.monitorFilterContains.replace('\n', ' ').replace('|', '/')
        return listOf(
            if (c.monitorShowIn) "1" else "0",
            if (c.monitorShowOut) "1" else "0",
            if (c.monitorMappedOnly) "1" else "0",
            contains,
            addrs,
            c.monitorLineSize.coerceIn(MONITOR_LINE_SIZE_MIN, MONITOR_LINE_SIZE_MAX).toString(),
        ).joinToString("|")
    }

    fun parse(raw: String): AppConfig {
        val lines = raw.split("\n")
        val mappings = if (lines.size > 4 && lines[4].isNotBlank()) {
            lines[4].split(";").map {
                val p = it.split("|")
                val page = if (p.size > 3) p[3].toInt() else 0
                val floatValue = p.getOrNull(4)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
                val floatMin = p.getOrNull(5)?.takeIf { it.isNotBlank() }?.toFloatOrNull()
                val floatMax = p.getOrNull(6)?.takeIf { it.isNotBlank() }?.toFloatOrNull()
                val customValues = p.getOrNull(7)?.trim() == "1"
                val onValue = p.getOrNull(8)?.toIntOrNull() ?: 1
                val offValue = p.getOrNull(9)?.toIntOrNull() ?: 0
                val invert = p.getOrNull(10)?.trim() == "1"
                val toggleOn = p.getOrNull(11)?.trim() == "1"
                val extras = p.getOrNull(12).orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .take(MAX_MAPPING_ADDRESSES - 1)
                ControlMapping(
                    controlId = ConfigStore.migrateControlId(p[0]),
                    address = p[1],
                    argKind = parseArgKind(p[2]),
                    page = page,
                    extraAddresses = extras,
                    floatValue = floatValue,
                    floatMin = floatMin,
                    floatMax = floatMax,
                    customValues = customValues,
                    onValue = onValue,
                    offValue = offValue,
                    invert = invert,
                    toggleOn = toggleOn,
                )
            }
        } else emptyList()
        val notes = if (lines.size > 5 && lines[5].isNotBlank()) {
            lines[5].split(";").mapNotNull { entry ->
                val p = entry.split("|")
                when {
                    p.size >= 3 && p[0].toIntOrNull() != null -> {
                        val page = p[0].toInt()
                        val control = ConfigStore.migrateControlId(p[1])
                        val note = p.drop(2).joinToString("|")
                        noteStorageKey(page, control) to note
                    }
                    p.size >= 2 -> {
                        // Legacy: controlId|note → page 0
                        noteStorageKey(0, ConfigStore.migrateControlId(p[0])) to p[1]
                    }
                    else -> null
                }
            }.toMap()
        } else emptyMap()
        val forceSingleDisplay = lines.getOrNull(6)?.trim() == "1"
        val themeMode = lines.getOrNull(7)?.trim()?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrNull()
        } ?: ThemeMode.SYSTEM
        val stickCurve = lines.getOrNull(8)?.toFloatOrNull()?.coerceIn(1f, 4f) ?: 3.2f
        val triggerCurve = lines.getOrNull(9)?.toFloatOrNull()?.coerceIn(1f, 3f) ?: 1.8f
        val stickLeftFull = lines.getOrNull(10)?.toFloatOrNull()?.coerceIn(0.55f, 1f) ?: 1f
        val stickRightFull = lines.getOrNull(11)?.toFloatOrNull()?.coerceIn(0.55f, 1f) ?: 1f
        val pageNames = if (lines.size > 12 && lines[12].isNotBlank()) {
            lines[12].split(";").mapNotNull {
                val p = it.split("|", limit = 2)
                val idx = p.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                idx to p.getOrElse(1) { "" }.trim()
            }.filter { it.second.isNotEmpty() }.toMap()
        } else emptyMap()
        val activePage = lines[3].toInt()
        val inferredCount = maxOf(
            1,
            activePage + 1,
            (mappings.maxOfOrNull { it.page } ?: -1) + 1,
            (pageNames.keys.maxOrNull() ?: -1) + 1,
        )
        val pageCount = lines.getOrNull(13)?.toIntOrNull()?.coerceAtLeast(1) ?: inferredCount
        val pageTypes = if (lines.size > 14 && lines[14].isNotBlank()) {
            lines[14].split(";").mapNotNull {
                val p = it.split("|", limit = 2)
                val idx = p.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val type = runCatching { PageType.valueOf(p.getOrElse(1) { "" }.trim()) }.getOrNull()
                    ?: return@mapNotNull null
                idx to type
            }.toMap()
        } else emptyMap()
        val widgets = if (lines.size > 15 && lines[15].isNotBlank()) {
            lines[15].split(";").mapNotNull { entry ->
                val p = entry.split("|")
                if (p.size < 8) return@mapNotNull null
                val kind = runCatching { TactileKind.valueOf(p[2]) }.getOrNull() ?: return@mapNotNull null
                TactileWidget(
                    id = p[0],
                    page = p[1].toIntOrNull() ?: return@mapNotNull null,
                    kind = kind,
                    label = p[3],
                    x = p[4].toFloatOrNull() ?: 0.05f,
                    y = p[5].toFloatOrNull() ?: 0.05f,
                    w = p[6].toFloatOrNull() ?: 0.22f,
                    h = p[7].toFloatOrNull() ?: 0.18f,
                    fillColor = p.getOrNull(8)?.takeIf { it.isNotBlank() },
                ).clamped()
            }
        } else emptyList()
        val bottomPlayPage = lines.getOrNull(16)?.toIntOrNull()
            ?.coerceIn(0, pageCount - 1)
            ?: activePage.coerceIn(0, pageCount - 1)
        val mon = parseMonitorPrefs(lines.getOrNull(17))
        val colorPalette = lines.getOrNull(18)?.trim()?.let {
            runCatching { ColorPalette.valueOf(it) }.getOrNull()
        } ?: ColorPalette.TEAL_SCENE
        val tactileGridColumns = lines.getOrNull(19)?.trim()?.toIntOrNull()
            ?.let { TactileGrid.normalizeColumns(it) }
            ?: TactileGrid.DEFAULT_COLUMNS
        val customColorHistory = lines.getOrNull(20).orEmpty()
            .split(",")
            .mapNotNull { normalizeFillHex(it.trim()) }
            .distinctBy { it.uppercase() }
            .take(AppConfig.CUSTOM_COLOR_HISTORY_MAX)
        return AppConfig(
            targetIp = lines[0],
            targetPort = lines[1].toInt(),
            listenPort = lines[2].toInt(),
            activePage = activePage.coerceIn(0, pageCount - 1),
            bottomPlayPage = bottomPlayPage,
            pageCount = pageCount,
            mappings = mappings,
            buttonNotes = notes,
            forceSingleDisplay = forceSingleDisplay,
            themeMode = themeMode,
            colorPalette = colorPalette,
            stickCurve = stickCurve,
            triggerCurve = triggerCurve,
            stickLeftFull = stickLeftFull,
            stickRightFull = stickRightFull,
            pageNames = pageNames,
            pageTypes = pageTypes,
            widgets = widgets,
            tactileGridColumns = tactileGridColumns,
            monitorShowIn = mon.showIn,
            monitorShowOut = mon.showOut,
            monitorMappedOnly = mon.mappedOnly,
            monitorFilterContains = mon.contains,
            monitorFilterAddresses = mon.addresses,
            monitorLineSize = mon.lineSize,
            customColorHistory = customColorHistory,
        )
    }

    private data class MonitorPrefsParse(
        val showIn: Boolean = true,
        val showOut: Boolean = true,
        val mappedOnly: Boolean = false,
        val contains: String = "",
        val addresses: Set<String> = emptySet(),
        val lineSize: Int = 1,
    )

    private fun parseMonitorPrefs(raw: String?): MonitorPrefsParse {
        if (raw.isNullOrBlank()) return MonitorPrefsParse()
        val p = raw.split("|")
        return MonitorPrefsParse(
            showIn = p.getOrNull(0)?.trim() != "0",
            showOut = p.getOrNull(1)?.trim() != "0",
            mappedOnly = p.getOrNull(2)?.trim() == "1",
            contains = p.getOrNull(3).orEmpty(),
            addresses = p.getOrNull(4).orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet(),
            lineSize = p.getOrNull(5)?.toIntOrNull()
                ?.coerceIn(MONITOR_LINE_SIZE_MIN, MONITOR_LINE_SIZE_MAX)
                ?: 1,
        )
    }
}
