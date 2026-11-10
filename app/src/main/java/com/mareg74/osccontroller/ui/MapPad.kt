package com.mareg74.osccontroller.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.mareg74.osccontroller.ThorOscApp
import androidx.compose.material3.Checkbox
import com.mareg74.osccontroller.config.ArgKind
import com.mareg74.osccontroller.config.ControlMapping
import com.mareg74.osccontroller.config.PageType
import com.mareg74.osccontroller.config.chipLabel
import com.mareg74.osccontroller.mapping.ControlId
import com.mareg74.osccontroller.mapping.MappingEngine
import kotlin.math.abs

@Composable private fun mapPanel(): Color = MaterialTheme.colorScheme.surfaceContainerHighest
@Composable private fun mapInk(): Color = MaterialTheme.colorScheme.onSurface
@Composable private fun mapMuted(): Color = MaterialTheme.colorScheme.onSurfaceVariant
private val ColorA = Color(0xFF3DDC84)
private val ColorB = Color(0xFFE85D5D)
private val ColorX = Color(0xFF4BA3F0)
private val ColorY = Color(0xFFF0C14A)
private val ColorShoulder = Color(0xFF2EC4B6)
private val ColorDpad = Color(0xFF7AD7F0)
private val ColorStickL = Color(0xFFF4A261)
private val ColorStickR = Color(0xFFE76F51)
private val ColorMeta = Color(0xFFB8C0CC)
private val ColorSelected = Color(0xFFFFD166)

/**
 * Map tab: touch the Thor pad to pick a control, edit mapping in the side panel.
 */
@Composable
fun MapTab(
    app: ThorOscApp,
    controlLevels: Map<ControlId, Float>,
    onConfigChanged: () -> Unit,
) {
    var selected by remember { mutableStateOf(ControlId.BTN_A) }
    var listenHardware by remember { mutableStateOf(true) }
    var addressLines by remember { mutableStateOf(listOf("")) }
    var pendingDeleteLine by remember { mutableStateOf<Int?>(null) }
    var argKind by remember { mutableStateOf(ArgKind.MOMENTARY) }
    var legendNote by remember { mutableStateOf("") }
    var floatValueText by remember { mutableStateOf("1") }
    var floatMinText by remember { mutableStateOf("0") }
    var floatMaxText by remember { mutableStateOf("1") }
    var customValues by remember { mutableStateOf(false) }
    var onValueText by remember { mutableStateOf("1") }
    var offValueText by remember { mutableStateOf("0") }
    var invert by remember { mutableStateOf(false) }
    var toggleOn by remember { mutableStateOf(false) }
    var catalogQuery by remember { mutableStateOf("") }
    /** Label proposé après un pick catalogue (option A) — reset au changement contrôle/page. */
    var catalogSuggestedLabel by remember { mutableStateOf<String?>(null) }
    var showNewPageDialog by remember { mutableStateOf(false) }
    var learnCopyLineIndex by remember { mutableStateOf<Int?>(null) }
    var newPageName by remember { mutableStateOf("") }
    var newPageType by remember { mutableStateOf(PageType.PHYSICAL) }
    var prevLevels by remember { mutableStateOf<Map<ControlId, Float>>(emptyMap()) }
    var page by remember { mutableIntStateOf(app.config.activePage) }
    LaunchedEffect(app.config.activePage) {
        page = app.config.activePage
    }
    LaunchedEffect(selected, page) {
        catalogSuggestedLabel = null
    }

    val mappedIds = remember(app.config.mappings, page) {
        app.config.mappings.filter { it.page == page }.map { it.controlId }.toSet()
    }

    fun setPage(newPage: Int) {
        val clamped = newPage.coerceIn(0, app.config.lastPageIndex())
        page = clamped
        app.config = app.config.copy(activePage = clamped)
        app.persist()
        onConfigChanged()
    }

    LaunchedEffect(selected, page, app.config.mappings, app.config.buttonNotes) {
        val m = app.config.mappings.firstOrNull {
            it.controlId == selected.name && it.page == page
        }
        addressLines = m.toEditableAddressLines()
        argKind = m?.argKind ?: defaultArgKind(selected)
        legendNote = app.config.noteFor(page, selected.name)
        floatValueText = formatFloatValue(m?.floatValue ?: 1f)
        floatMinText = formatFreeFloat(m?.floatMin ?: MappingEngine.defaultAxisMin(selected))
        floatMaxText = formatFreeFloat(m?.floatMax ?: MappingEngine.defaultAxisMax(selected))
        customValues = m?.customValues ?: false
        onValueText = (m?.onValue ?: 1).toString()
        offValueText = (m?.offValue ?: 0).toString()
        invert = m?.invert ?: false
        toggleOn = m?.toggleOn ?: false
    }

    // Hardware learn: rising edge selects the control.
    LaunchedEffect(controlLevels, listenHardware) {
        if (!listenHardware) {
            prevLevels = controlLevels
            return@LaunchedEffect
        }
        val hit = ControlId.entries.firstOrNull { id ->
            val now = abs(controlLevels[id] ?: 0f)
            val was = abs(prevLevels[id] ?: 0f)
            val threshold = if (id.name.startsWith("AXIS_")) 0.35f else 0.55f
            now >= threshold && was < threshold * 0.5f
        }
        prevLevels = controlLevels
        if (hit != null && hit != selected) {
            selected = hit
        }
    }

    val catalogHits = remember(catalogQuery, app.catalog) {
        val q = catalogQuery.trim().lowercase()
        if (q.isEmpty()) {
            app.catalog.take(80)
        } else {
            app.catalog.filter {
                it.label.lowercase().contains(q) || it.address.lowercase().contains(q)
            }.take(120)
        }
    }

    val context = LocalContext.current

    fun persistLegend(next: String) {
        legendNote = next
        app.config = app.config.withNote(page, selected.name, next)
        app.persist()
        onConfigChanged()
    }

    fun createPage(name: String, type: PageType = PageType.PHYSICAL) {
        app.config = app.config.addPage(name, type)
        page = app.config.activePage
        app.persist()
        onConfigChanged()
        Toast.makeText(
            context,
            "Page « ${app.config.pageLabel(page)} » créée",
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun pickCatalog(entry: com.mareg74.osccontroller.catalog.CatalogEntry) {
        addressLines = listOf(entry.address)
        argKind = entry.argKind
        // Ne pas remplir la barre de recherche — réservée à la saisie manuelle.
        catalogSuggestedLabel = entry.label
    }

    fun draftDigitalFields(): Pair<Int, Int>? {
        val on = onValueText.trim().toIntOrNull()
        val off = offValueText.trim().toIntOrNull()
        if (customValues && (on == null || off == null)) {
            Toast.makeText(context, "On/Off INT invalides", Toast.LENGTH_SHORT).show()
            return null
        }
        return (on ?: 1) to (off ?: 0)
    }

    fun saveMapping() {
        val saved = addressLines.toSavedAddresses() ?: run {
            Toast.makeText(context, "Indique une adresse OSC", Toast.LENGTH_SHORT).show()
            return
        }
        val without = app.config.mappings.filter {
            !(it.controlId == selected.name && it.page == page)
        }
        val isAxis = selected.name.startsWith("AXIS_")
        val kind = if (isAxis) ArgKind.FLOAT else argKind
        val fv = if (!isAxis && kind == ArgKind.FLOAT) {
            parseFloat01(floatValueText) ?: 1f
        } else {
            1f
        }
        val fMin: Float?
        val fMax: Float?
        if (isAxis && kind == ArgKind.FLOAT) {
            val parsedMin = parseFreeFloat(floatMinText)
            val parsedMax = parseFreeFloat(floatMaxText)
            if (parsedMin == null || parsedMax == null) {
                Toast.makeText(context, "Min/Max FLOAT invalides", Toast.LENGTH_SHORT).show()
                return
            }
            if (parsedMin > parsedMax) {
                Toast.makeText(context, "Min doit être ≤ Max", Toast.LENGTH_SHORT).show()
                return
            }
            fMin = parsedMin
            fMax = parsedMax
        } else {
            fMin = null
            fMax = null
        }
        val digital = if (kind == ArgKind.MOMENTARY || kind == ArgKind.TOGGLE) {
            draftDigitalFields() ?: return
        } else 1 to 0
        val useCustom = (kind == ArgKind.MOMENTARY || kind == ArgKind.TOGGLE) && customValues
        app.config = app.config.copy(
            mappings = without + ControlMapping(
                controlId = selected.name,
                address = saved.first,
                argKind = kind,
                page = page,
                extraAddresses = saved.second,
                floatValue = fv,
                floatMin = fMin,
                floatMax = fMax,
                customValues = useCustom,
                onValue = digital.first,
                offValue = digital.second,
                invert = !useCustom && invert,
                toggleOn = toggleOn,
            ),
        )
        addressLines = (listOf(saved.first) + saved.second)
        floatValueText = formatFloatValue(fv)
        floatMinText = formatFreeFloat(fMin ?: MappingEngine.defaultAxisMin(selected))
        floatMaxText = formatFreeFloat(fMax ?: MappingEngine.defaultAxisMax(selected))
        app.persist()
        onConfigChanged()
        Toast.makeText(context, "Mapping appliqué", Toast.LENGTH_SHORT).show()
    }

    fun clearMapping() {
        app.config = app.config.copy(
            mappings = app.config.mappings.filter {
                !(it.controlId == selected.name && it.page == page)
            },
        )
        addressLines = listOf("")
        argKind = defaultArgKind(selected)
        floatValueText = "1"
        floatMinText = formatFreeFloat(MappingEngine.defaultAxisMin(selected))
        floatMaxText = formatFreeFloat(MappingEngine.defaultAxisMax(selected))
        customValues = false
        onValueText = "1"
        offValueText = "0"
        invert = false
        toggleOn = false
        app.persist()
        onConfigChanged()
    }

    fun testOsc() {
        val saved = addressLines.toSavedAddresses() ?: run {
            Toast.makeText(context, "Indique une adresse OSC", Toast.LENGTH_SHORT).show()
            return
        }
        val isAxis = selected.name.startsWith("AXIS_")
        val kind = if (isAxis) ArgKind.FLOAT else argKind
        val digital = if (kind == ArgKind.MOMENTARY || kind == ArgKind.TOGGLE) {
            draftDigitalFields() ?: return
        } else 1 to 0
        val useCustom = (kind == ArgKind.MOMENTARY || kind == ArgKind.TOGGLE) && customValues
        val fMin: Float?
        val fMax: Float?
        val fv: Float
        if (kind == ArgKind.FLOAT) {
            if (isAxis) {
                fMin = parseFreeFloat(floatMinText)
                fMax = parseFreeFloat(floatMaxText)
                if (fMin == null || fMax == null) {
                    Toast.makeText(context, "Min/Max FLOAT invalides", Toast.LENGTH_SHORT).show()
                    return
                }
                if (fMin > fMax) {
                    Toast.makeText(context, "Min doit être ≤ Max", Toast.LENGTH_SHORT).show()
                    return
                }
                fv = 1f
            } else {
                fMin = null
                fMax = null
                fv = parseFloat01(floatValueText) ?: run {
                    Toast.makeText(context, "Valeur FLOAT invalide (0…1)", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        } else {
            fMin = null
            fMax = null
            fv = 1f
        }
        val draft = ControlMapping(
            controlId = selected.name,
            address = saved.first,
            argKind = kind,
            page = page,
            extraAddresses = saved.second,
            floatValue = fv,
            floatMin = fMin,
            floatMax = fMax,
            customValues = useCustom,
            onValue = digital.first,
            offValue = digital.second,
            invert = !useCustom && invert,
            toggleOn = toggleOn,
        )
        val cfg = app.config.copy(
            mappings = app.config.mappings.filterNot {
                it.controlId == selected.name && it.page == page
            } + draft,
        )
        val continuous = isAxis
        val bipolar = continuous && !MappingEngine.isTriggerAxis(selected)
        val result = app.engine.resolveResult(
            config = cfg,
            controlId = selected.name,
            value = 1f,
            continuous = continuous,
            bipolarHardware = bipolar,
            page = page,
        )
        val msgs = result.messages
        if (msgs.isEmpty()) {
            Toast.makeText(context, "Rien à envoyer", Toast.LENGTH_SHORT).show()
            return
        }
        result.updatedMapping?.let { upd ->
            toggleOn = upd.toggleOn
            // Persist latch if this control is already mapped (or after Test with draft).
            app.config = app.config.copy(
                mappings = app.config.mappings.map { m ->
                    if (m.controlId == upd.controlId && m.page == upd.page) {
                        m.copy(toggleOn = upd.toggleOn)
                    } else m
                }.let { list ->
                    if (list.any { it.controlId == selected.name && it.page == page }) list
                    else list // draft-only test: don't invent a save
                },
            )
            if (app.config.mappings.any { it.controlId == selected.name && it.page == page }) {
                app.persist()
                onConfigChanged()
            }
        }
        val ok = msgs.all { app.sender.send(app.config, it) }
        val first = msgs.first()
        val arg = first.args.firstOrNull()?.toString().orEmpty()
        val multi = if (msgs.size > 1) " ×${msgs.size}" else ""
        Toast.makeText(
            context,
            if (ok) "Test OSC → ${first.address}$multi${if (arg.isNotEmpty()) " ($arg)" else ""}"
            else "Échec envoi (IP/port invalide ?)",
            Toast.LENGTH_SHORT,
        ).show()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
    if (app.config.pageType(page) == PageType.TACTILE) {
        TactileMapContent(
            app = app,
            page = page,
            onConfigChanged = onConfigChanged,
        )
    } else {
    // Always one column: header + pad + fields scroll; Appliquer/Effacer pinned.
    val isAxis = selected.name.startsWith("AXIS_")
    val showFixedFloatField = !isAxis && argKind == ArgKind.FLOAT
    val showAxisRangeFields = isAxis && argKind == ArgKind.FLOAT
    val showDigitalMode = !isAxis && (argKind == ArgKind.MOMENTARY || argKind == ArgKind.TOGGLE)
    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapPadHeader(
                page = page,
                pageLabel = app.config.pageLabel(page),
                lastPageIndex = app.config.lastPageIndex(),
                pageTitles = app.config.pageTitleList(),
                pageIsTactile = app.config.pageTactileFlags(),
                listenHardware = listenHardware,
                onListenChange = { listenHardware = it },
                onPageChange = ::setPage,
                onAddPage = {
                    newPageName = ""
                    newPageType = PageType.PHYSICAL
                    showNewPageDialog = true
                },
            )
            MapVisualPad(
                selected = selected,
                mappedIds = mappedIds,
                onSelect = { selected = it },
                modifier = Modifier.fillMaxWidth(),
            )
            MapEditorPanel(
                selected = selected,
                legendNote = legendNote,
                onLegendChange = ::persistLegend,
                suggestedCatalogLabel = catalogSuggestedLabel,
                onApplySuggestedLabel = {
                    catalogSuggestedLabel?.let { persistLegend(it) }
                },
                addressLines = addressLines,
                onAddressLinesChange = { addressLines = it },
                onOpenLearnCopy = { learnCopyLineIndex = it },
                onRequestDeleteLine = { pendingDeleteLine = it },
                argKind = argKind,
                onArgKindChange = { argKind = it },
                floatValueText = floatValueText,
                onFloatValueChange = { raw ->
                    val next = filterFloat01Input(raw)
                    if (next != null) floatValueText = next
                },
                showFixedFloatField = showFixedFloatField,
                floatMinText = floatMinText,
                onFloatMinChange = { raw ->
                    val next = filterFreeFloatInput(raw)
                    if (next != null) floatMinText = next
                },
                floatMaxText = floatMaxText,
                onFloatMaxChange = { raw ->
                    val next = filterFreeFloatInput(raw)
                    if (next != null) floatMaxText = next
                },
                showAxisRangeFields = showAxisRangeFields,
                showDigitalMode = showDigitalMode,
                customValues = customValues,
                onCustomValuesChange = { customValues = it },
                onValueText = onValueText,
                onOnValueChange = { raw ->
                    if (raw.isEmpty() || raw.matches(Regex("-?\\d*"))) onValueText = raw
                },
                offValueText = offValueText,
                onOffValueChange = { raw ->
                    if (raw.isEmpty() || raw.matches(Regex("-?\\d*"))) offValueText = raw
                },
                invert = invert,
                onInvertChange = { invert = it },
                catalogQuery = catalogQuery,
                onCatalogQueryChange = { catalogQuery = it },
                catalogHits = catalogHits,
                onPickCatalog = ::pickCatalog,
                isMapped = selected.name in mappedIds,
                onSave = ::saveMapping,
                onClear = ::clearMapping,
                onTest = ::testOsc,
                scrollableContent = false,
                showFooter = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MapApplyFooter(
            enabled = addressLines.any { it.isNotBlank() } &&
                (!showFixedFloatField || parseFloat01(floatValueText) != null) &&
                (!showAxisRangeFields || axisRangeValid(floatMinText, floatMaxText)),
            isMapped = selected.name in mappedIds,
            onSave = ::saveMapping,
            onClear = ::clearMapping,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .padding(top = 8.dp),
        )
    }
    } // end physical MapTab branch

    if (showNewPageDialog) {
        // In-tree overlay: AlertDialog/Window crashes inside secondary Presentation.
        NewPageOverlay(
            name = newPageName,
            onNameChange = { newPageName = it },
            pageType = newPageType,
            onPageTypeChange = { newPageType = it },
            placeholder = "Page ${app.config.pageCount}",
            onConfirm = {
                createPage(newPageName, newPageType)
                showNewPageDialog = false
            },
            onDismiss = { showNewPageDialog = false },
        )
    }
    learnCopyLineIndex?.let { lineIdx ->
        InTreeModal(onDismiss = { learnCopyLineIndex = null }) {
            OscLearnCopyCard(
                log = app.log,
                onPickAddress = { addr ->
                    addressLines = addressLines.toMutableList().also { list ->
                        if (lineIdx in list.indices) list[lineIdx] = addr
                    }
                    learnCopyLineIndex = null
                    Toast.makeText(context, "Adresse : $addr", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { learnCopyLineIndex = null },
            )
        }
    }
    pendingDeleteLine?.let { idx ->
        InTreeModal(onDismiss = { pendingDeleteLine = null }) {
            ConfirmDeleteAddressCard(
                address = addressLines.getOrNull(idx).orEmpty(),
                onConfirm = {
                    addressLines = addressLines.toMutableList().also { it.removeAt(idx) }
                        .ifEmpty { listOf("") }
                    pendingDeleteLine = null
                },
                onDismiss = { pendingDeleteLine = null },
            )
        }
    }
    }

}

@Composable
private fun MapPadHeader(
    page: Int,
    pageLabel: String,
    lastPageIndex: Int,
    pageTitles: List<String>,
    pageIsTactile: List<Boolean>,
    listenHardware: Boolean,
    onListenChange: (Boolean) -> Unit,
    onPageChange: (Int) -> Unit,
    onAddPage: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageSwitcher(
            page = page,
            pageLabel = pageLabel,
            lastPageIndex = lastPageIndex,
            onPageChange = onPageChange,
            onAddPage = onAddPage,
            pageTitles = pageTitles,
            pageIsTactile = pageIsTactile,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Écouter manette",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = listenHardware, onCheckedChange = onListenChange)
    }
}

/** Modal without a new Window — safe on secondary Presentation. */
@Composable
private fun NewPageNameOverlay(
    name: String,
    onNameChange: (String) -> Unit,
    placeholder: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Nouvelle page",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Nom de la page") },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Button(onClick = onConfirm) { Text("Créer") }
                }
            }
        }
    }
}

@Composable
private fun MapVisualPad(
    selected: ControlId,
    mappedIds: Set<String>,
    onSelect: (ControlId) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        // In a page scroll (unbounded height) wrap content so D-pad/RS are not clipped.
        // When parent gives a max height (wide side column), expand with weight.
        val heightBounded = maxHeight != Dp.Infinity && maxHeight != Dp.Unspecified
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (heightBounded) Modifier.fillMaxHeight() else Modifier)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        // Top: L1 L2 SEL START R1 R2
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MapChip(ControlId.BTN_L1, "L1", ColorShoulder, selected, mappedIds, onSelect)
            MapChip(ControlId.AXIS_L2, "L2", ColorShoulder, selected, mappedIds, onSelect, wide = true)
            Spacer(Modifier.weight(1f))
            MapChip(ControlId.BTN_SELECT, "SEL", ColorMeta, selected, mappedIds, onSelect)
            MapChip(ControlId.BTN_START, "START", ColorMeta, selected, mappedIds, onSelect)
            Spacer(Modifier.weight(1f))
            MapChip(ControlId.BTN_R1, "R1", ColorShoulder, selected, mappedIds, onSelect)
            MapChip(ControlId.AXIS_R2, "R2", ColorShoulder, selected, mappedIds, onSelect, wide = true)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .then(if (heightBounded) Modifier.weight(1f) else Modifier),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: stick + axes chips + dpad
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MapStickCluster(
                    label = "LS",
                    accent = ColorStickL,
                    axes = listOf(
                        ControlId.AXIS_LX to "LX",
                        ControlId.AXIS_LY to "LY",
                        ControlId.BTN_L3 to "L3",
                    ),
                    selected = selected,
                    mappedIds = mappedIds,
                    onSelect = onSelect,
                )
                MapDpad(selected, mappedIds, onSelect)
            }

            Text(
                "MAP",
                color = mapMuted().copy(alpha = 0.55f),
                letterSpacing = 4.sp,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            // Right: face + stick
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MapFace(selected, mappedIds, onSelect)
                MapStickCluster(
                    label = "RS",
                    accent = ColorStickR,
                    axes = listOf(
                        ControlId.AXIS_RX to "RX",
                        ControlId.AXIS_RY to "RY",
                        ControlId.BTN_R3 to "R3",
                    ),
                    selected = selected,
                    mappedIds = mappedIds,
                    onSelect = onSelect,
                )
            }
        }
        }
    }
}

@Composable
private fun MapStickCluster(
    label: String,
    accent: Color,
    axes: List<Pair<ControlId, String>>,
    selected: ControlId,
    mappedIds: Set<String>,
    onSelect: (ControlId) -> Unit,
) {
    val anySelected = axes.any { it.first == selected }
    val anyMapped = axes.any { it.first.name in mappedIds }
    val panel = mapPanel()
    val ink = mapInk()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(if (anySelected) accent.copy(alpha = 0.35f) else panel)
                .border(
                    width = if (anySelected) 3.dp else 1.5.dp,
                    color = when {
                        anySelected -> ColorSelected
                        anyMapped -> accent
                        else -> accent.copy(alpha = 0.35f)
                    },
                    shape = CircleShape,
                )
                .clickable { onSelect(axes.first().first) },
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = ink, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            axes.forEach { (id, tag) ->
                MapMiniChip(id, tag, accent, selected, mappedIds, onSelect)
            }
        }
    }
}

@Composable
private fun MapFace(
    selected: ControlId,
    mappedIds: Set<String>,
    onSelect: (ControlId) -> Unit,
) {
    Box(Modifier.size(118.dp), contentAlignment = Alignment.Center) {
        MapRound(
            ControlId.BTN_X, "X", ColorX, selected, mappedIds, onSelect,
            Modifier.align(Alignment.TopCenter),
        )
        MapRound(
            ControlId.BTN_Y, "Y", ColorY, selected, mappedIds, onSelect,
            Modifier.align(Alignment.CenterStart),
        )
        MapRound(
            ControlId.BTN_A, "A", ColorA, selected, mappedIds, onSelect,
            Modifier.align(Alignment.CenterEnd),
        )
        MapRound(
            ControlId.BTN_B, "B", ColorB, selected, mappedIds, onSelect,
            Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MapDpad(
    selected: ControlId,
    mappedIds: Set<String>,
    onSelect: (ControlId) -> Unit,
    buttonSize: Dp = 34.dp,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MapRound(ControlId.BTN_DPAD_UP, "▲", ColorDpad, selected, mappedIds, onSelect, size = buttonSize)
        Row(verticalAlignment = Alignment.CenterVertically) {
            MapRound(ControlId.BTN_DPAD_LEFT, "◀", ColorDpad, selected, mappedIds, onSelect, size = buttonSize)
            Spacer(Modifier.width(buttonSize + 4.dp))
            MapRound(ControlId.BTN_DPAD_RIGHT, "▶", ColorDpad, selected, mappedIds, onSelect, size = buttonSize)
        }
        MapRound(ControlId.BTN_DPAD_DOWN, "▼", ColorDpad, selected, mappedIds, onSelect, size = buttonSize)
    }
}

@Composable
private fun MapChip(
    id: ControlId,
    label: String,
    accent: Color,
    selected: ControlId,
    mappedIds: Set<String>,
    onSelect: (ControlId) -> Unit,
    wide: Boolean = false,
) {
    val isSel = selected == id
    val mapped = id.name in mappedIds
    val panel = mapPanel()
    val ink = mapInk()
    Box(
        Modifier
            .width(if (wide) 64.dp else 52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSel) accent.copy(alpha = 0.85f) else panel)
            .border(
                width = if (isSel) 2.dp else 1.dp,
                color = when {
                    isSel -> ColorSelected
                    mapped -> accent
                    else -> accent.copy(alpha = 0.4f)
                },
                shape = RoundedCornerShape(8.dp),
            )
            .clickable { onSelect(id) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (mapped && !isSel) "✓ $label" else label,
            color = if (isSel) Color.White else ink,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun MapMiniChip(
    id: ControlId,
    label: String,
    accent: Color,
    selected: ControlId,
    mappedIds: Set<String>,
    onSelect: (ControlId) -> Unit,
) {
    val isSel = selected == id
    val mapped = id.name in mappedIds
    val panel = mapPanel()
    val ink = mapInk()
    Box(
        Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(if (isSel) accent.copy(alpha = 0.9f) else panel.copy(alpha = 0.8f))
            .border(
                1.dp,
                when {
                    isSel -> ColorSelected
                    mapped -> accent
                    else -> accent.copy(alpha = 0.35f)
                },
                RoundedCornerShape(50),
            )
            .clickable { onSelect(id) }
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            color = if (isSel) Color.White else ink,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MapRound(
    id: ControlId,
    label: String,
    accent: Color,
    selected: ControlId,
    mappedIds: Set<String>,
    onSelect: (ControlId) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val isSel = selected == id
    val mapped = id.name in mappedIds
    val panel = mapPanel()
    val ink = mapInk()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isSel) accent.copy(alpha = 0.95f) else panel)
            .border(
                width = if (isSel) 3.dp else 1.5.dp,
                color = when {
                    isSel -> ColorSelected
                    mapped -> accent
                    else -> accent.copy(alpha = 0.4f)
                },
                shape = CircleShape,
            )
            .clickable { onSelect(id) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (isSel) Color.White else ink,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.28f).sp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapEditorPanel(
    selected: ControlId,
    legendNote: String,
    onLegendChange: (String) -> Unit,
    suggestedCatalogLabel: String?,
    onApplySuggestedLabel: () -> Unit,
    addressLines: List<String>,
    onAddressLinesChange: (List<String>) -> Unit,
    onOpenLearnCopy: (Int) -> Unit,
    onRequestDeleteLine: (Int) -> Unit,
    argKind: ArgKind,
    onArgKindChange: (ArgKind) -> Unit,
    floatValueText: String,
    onFloatValueChange: (String) -> Unit,
    showFixedFloatField: Boolean,
    floatMinText: String,
    onFloatMinChange: (String) -> Unit,
    floatMaxText: String,
    onFloatMaxChange: (String) -> Unit,
    showAxisRangeFields: Boolean,
    showDigitalMode: Boolean,
    customValues: Boolean,
    onCustomValuesChange: (Boolean) -> Unit,
    onValueText: String,
    onOnValueChange: (String) -> Unit,
    offValueText: String,
    onOffValueChange: (String) -> Unit,
    invert: Boolean,
    onInvertChange: (Boolean) -> Unit,
    catalogQuery: String,
    onCatalogQueryChange: (String) -> Unit,
    catalogHits: List<com.mareg74.osccontroller.catalog.CatalogEntry>,
    onPickCatalog: (com.mareg74.osccontroller.catalog.CatalogEntry) -> Unit,
    isMapped: Boolean,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onTest: () -> Unit,
    scrollableContent: Boolean = true,
    showFooter: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = if (scrollableContent) modifier else modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            (if (scrollableContent) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(14.dp),
        ) {
            // When scrollableContent: fields scroll inside panel (wide side editor).
            // When false: intrinsic height for embedding in a page-level verticalScroll.
            val fieldsModifier = if (scrollableContent) {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            }
            Column(
                fieldsModifier,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        shortLabel(selected),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        selected.name + if (isMapped) " · mappé" else " · libre",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onTest,
                    enabled = addressLines.any { it.isNotBlank() } &&
                        (!showFixedFloatField || parseFloat01(floatValueText) != null) &&
                        (!showAxisRangeFields || axisRangeValid(floatMinText, floatMaxText)),
                ) {
                    Text("Test")
                }
            }

            FloatingBorderTextField(
                value = legendNote,
                onValueChange = onLegendChange,
                label = "Libellé",
                modifier = Modifier.fillMaxWidth(),
            )

            val suggestion = suggestedCatalogLabel?.trim().orEmpty()
            if (suggestion.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Défaut : $suggestion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (legendNote != suggestion) {
                        TextButton(onClick = onApplySuggestedLabel) {
                            Text("Défaut")
                        }
                    }
                }
            }

            MultiAddressEditor(
                lines = addressLines,
                onLinesChange = onAddressLinesChange,
                onOpenLearnCopy = onOpenLearnCopy,
                onRequestDelete = onRequestDeleteLine,
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val kinds = if (selected.name.startsWith("AXIS_")) {
                    listOf(ArgKind.FLOAT)
                } else {
                    listOf(ArgKind.MOMENTARY, ArgKind.TOGGLE, ArgKind.FLOAT)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.forEach { kind ->
                        FilterChip(
                            selected = argKind == kind,
                            onClick = { onArgKindChange(kind) },
                            label = { Text(kind.chipLabel()) },
                        )
                    }
                }
                Text(
                    when (argKind) {
                        ArgKind.MOMENTARY ->
                            "Momentary : INT on à l’appui, INT off au relâchement."
                        ArgKind.TOGGLE ->
                            "Toggle : chaque appui bascule et envoie ; relâchement = rien."
                        ArgKind.FLOAT ->
                            when {
                                showFixedFloatField ->
                                    "FLOAT : valeur fixe 0…1 envoyée à l’appui (Play) et au Test."
                                showAxisRangeFields ->
                                    "FLOAT : remap libre [Min…Max] — sticks −1…1, gâchettes 0…1."
                                else ->
                                    "FLOAT : valeur continue — sticks, gâchettes, paramètres."
                            }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (showDigitalMode) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !customValues,
                        onClick = { onCustomValuesChange(false) },
                        label = { Text("INT 0/1") },
                    )
                    FilterChip(
                        selected = customValues,
                        onClick = { onCustomValuesChange(true) },
                        label = { Text("Custom") },
                    )
                }
                if (customValues) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FloatingBorderTextField(
                            value = onValueText,
                            onValueChange = onOnValueChange,
                            label = "On",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        FloatingBorderTextField(
                            value = offValueText,
                            onValueChange = onOffValueChange,
                            label = "Off",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Checkbox(checked = invert, onCheckedChange = onInvertChange)
                        Text("Invert", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (showFixedFloatField) {
                FloatingBorderTextField(
                    value = floatValueText,
                    onValueChange = onFloatValueChange,
                    label = "Valeur FLOAT (0–1)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = "Uniquement entre 0 et 1 — utilisée en Play et au Test",
                    isError = floatValueText.isNotBlank() && parseFloat01(floatValueText) == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (showAxisRangeFields) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FloatingBorderTextField(
                        value = floatMinText,
                        onValueChange = onFloatMinChange,
                        label = "Min FLOAT",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = floatMinText.isNotBlank() && parseFreeFloat(floatMinText) == null,
                        modifier = Modifier.weight(1f),
                    )
                    FloatingBorderTextField(
                        value = floatMaxText,
                        onValueChange = onFloatMaxChange,
                        label = "Max FLOAT",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = floatMaxText.isNotBlank() && (
                            parseFreeFloat(floatMaxText) == null ||
                                ((parseFreeFloat(floatMinText) ?: 0f) > (parseFreeFloat(floatMaxText) ?: 0f))
                            ),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Plage libre (ex. −1…1, 0…100). Stick centre → milieu Min/Max.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FloatingBorderTextField(
                value = catalogQuery,
                onValueChange = onCatalogQueryChange,
                label = "Chercher dans le catalogue de commandes Resolume Arena",
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                if (catalogHits.isEmpty()) {
                    Text(
                        "Aucune commande",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Plain Column (no nested scroll) — parent verticalScroll handles everything.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        catalogHits.forEach { entry ->
                            TextButton(
                                onClick = { onPickCatalog(entry) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        entry.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            } // editor fields

            if (showFooter) {
                MapApplyFooter(
                    enabled = addressLines.any { it.isNotBlank() } &&
                        (!showFixedFloatField || parseFloat01(floatValueText) != null) &&
                        (!showAxisRangeFields || axisRangeValid(floatMinText, floatMaxText)),
                    isMapped = isMapped,
                    onSave = onSave,
                    onClear = onClear,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun MapApplyFooter(
    enabled: Boolean,
    isMapped: Boolean,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            enabled = enabled,
        ) {
            Text("Appliquer")
        }
        if (isMapped) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f),
            ) {
                Text("Effacer le mapping")
            }
        }
    }
}



/** Free float (any range) — allow "-", ".", digits while typing. */
private fun filterFreeFloatInput(raw: String): String? {
    val cleaned = raw.replace(',', '.')
    if (cleaned.isEmpty()) return ""
    if (cleaned == "-" || cleaned == "." || cleaned == "-.") return cleaned
    if (cleaned.count { it == '.' } > 1) return null
    if (cleaned.count { it == '-' } > 1) return null
    if ('-' in cleaned && !cleaned.startsWith("-")) return null
    cleaned.toFloatOrNull() ?: return null
    return cleaned
}

internal fun parseFreeFloat(raw: String): Float? {
    val cleaned = raw.replace(',', '.').trim()
    if (cleaned.isEmpty() || cleaned == "-" || cleaned == "." || cleaned == "-.") return null
    return cleaned.toFloatOrNull()
}

internal fun formatFreeFloat(v: Float): String {
    return if (v == v.toInt().toFloat()) v.toInt().toString()
    else v.toString().trimEnd('0').trimEnd('.')
}

private fun axisRangeValid(minText: String, maxText: String): Boolean {
    val min = parseFreeFloat(minText) ?: return false
    val max = parseFreeFloat(maxText) ?: return false
    return min <= max
}

@Composable
private fun FloatingBorderTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    // Keep the Material label docked on the outline even when empty (like a filled field).
    val display = if (value.isEmpty()) "​" else value
    OutlinedTextField(
        value = display,
        onValueChange = { raw -> onValueChange(raw.replace("​", "")) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        modifier = modifier,
    )
}

/** Accept partial decimal typing while rejecting values outside 0…1 once complete. */
private fun filterFloat01Input(raw: String): String? {
    val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
    if (cleaned.count { it == '.' } > 1) return null
    if (cleaned.isEmpty() || cleaned == ".") return cleaned
    val value = cleaned.toFloatOrNull() ?: return null
    if (value < 0f || value > 1f) return null
    // Reject numbers with useless leading junk like 00.x beyond normal decimal entry
    return cleaned
}

internal fun parseFloat01(raw: String): Float? {
    val cleaned = raw.replace(',', '.').trim()
    if (cleaned.isEmpty() || cleaned == ".") return null
    val value = cleaned.toFloatOrNull() ?: return null
    if (value < 0f || value > 1f) return null
    return value
}

internal fun formatFloatValue(v: Float): String {
    val clamped = v.coerceIn(0f, 1f)
    return if (clamped == clamped.toInt().toFloat()) clamped.toInt().toString()
    else clamped.toString().trimEnd('0').trimEnd('.')
}

private fun defaultArgKind(control: ControlId): ArgKind =
    if (control.name.startsWith("AXIS_")) ArgKind.FLOAT else ArgKind.MOMENTARY

private fun shortLabel(id: ControlId): String = when (id) {
    ControlId.BTN_A -> "Bouton A"
    ControlId.BTN_B -> "Bouton B"
    ControlId.BTN_X -> "Bouton X"
    ControlId.BTN_Y -> "Bouton Y"
    ControlId.BTN_L1 -> "L1"
    ControlId.BTN_R1 -> "R1"
    ControlId.BTN_L3 -> "L3 (click stick G)"
    ControlId.BTN_R3 -> "R3 (click stick D)"
    ControlId.BTN_DPAD_UP -> "D-pad ↑"
    ControlId.BTN_DPAD_DOWN -> "D-pad ↓"
    ControlId.BTN_DPAD_LEFT -> "D-pad ←"
    ControlId.BTN_DPAD_RIGHT -> "D-pad →"
    ControlId.BTN_START -> "Start"
    ControlId.BTN_SELECT -> "Select"
    ControlId.AXIS_LX -> "Stick G · X"
    ControlId.AXIS_LY -> "Stick G · Y"
    ControlId.AXIS_RX -> "Stick D · X"
    ControlId.AXIS_RY -> "Stick D · Y"
    ControlId.AXIS_L2 -> "Gâchette L2"
    ControlId.AXIS_R2 -> "Gâchette R2"
}
