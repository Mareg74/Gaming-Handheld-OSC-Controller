package com.mareg74.osccontroller.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.mareg74.osccontroller.ThorOscApp
import com.mareg74.osccontroller.catalog.CatalogEntry
import com.mareg74.osccontroller.config.AppConfig
import com.mareg74.osccontroller.config.ArgKind
import com.mareg74.osccontroller.config.ControlMapping
import com.mareg74.osccontroller.config.TACTILE_COLOR_PRESETS
import com.mareg74.osccontroller.config.TactileGrid
import com.mareg74.osccontroller.config.TactileKind
import com.mareg74.osccontroller.config.TactileWidget
import com.mareg74.osccontroller.config.ThorDefaultOsc
import com.mareg74.osccontroller.config.chipLabel
import com.mareg74.osccontroller.config.hsvToRgbHex
import com.mareg74.osccontroller.config.normalizeFillHex
import com.mareg74.osccontroller.config.parseFillArgb
import com.mareg74.osccontroller.config.rgbHexToHsv
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TactileMapContent(
    app: ThorOscApp,
    page: Int,
    onConfigChanged: () -> Unit,
) {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf<String?>(null) }
    var addressLines by remember { mutableStateOf(listOf("")) }
    var pendingDeleteLine by remember { mutableStateOf<Int?>(null) }
    var argKind by remember { mutableStateOf(ArgKind.MOMENTARY) }
    var labelText by remember { mutableStateOf("") }
    var floatValueText by remember { mutableStateOf("1") }
    var floatMinText by remember { mutableStateOf("0") }
    var floatMaxText by remember { mutableStateOf("1") }
    var customValues by remember { mutableStateOf(false) }
    var onValueText by remember { mutableStateOf("1") }
    var offValueText by remember { mutableStateOf("0") }
    var invert by remember { mutableStateOf(false) }
    var toggleOn by remember { mutableStateOf(false) }
    var catalogQuery by remember { mutableStateOf("") }
    var catalogSuggestedLabel by remember { mutableStateOf<String?>(null) }
    var learnCopyLineIndex by remember { mutableStateOf<Int?>(null) }
    var showCustomColor by remember { mutableStateOf(false) }
    var showEditTrigger by remember { mutableStateOf(false) }
    // app.config is not Compose State — bump this so move/resize/color refresh live
    // without waiting for selectedId to change. Also observe shared revision so peer
    // display Mapping/Play edits land here on release.
    var widgetsEpoch by remember { mutableIntStateOf(0) }

    val widgets = run {
        widgetsEpoch
        app.widgetsRevision.intValue
        app.config.widgetsOn(page)
    }
    val selected = widgets.find { it.id == selectedId }
    val gridColumns = TactileGrid.normalizeColumns(app.config.tactileGridColumns)
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    // Canvas fills remaining space; mapping edits live in Edit Trigger modal (QCM 5A).
    val canvasAspect = with(density) {
        val w = (config.screenWidthDp.dp - 24.dp).toPx().coerceAtLeast(1f)
        // Approximate usable canvas height for snap math (title+toolbar+grid strip).
        val h = (config.screenHeightDp.dp - 200.dp).toPx().coerceAtLeast(1f)
        w / h
    }

    LaunchedEffect(selectedId) {
        if (selectedId == null) showEditTrigger = false
    }

    fun setGridColumns(cols: Int) {
        val next = TactileGrid.normalizeColumns(cols)
        if (next == gridColumns) return
        val snapped = app.config.widgets.map { w ->
            if (w.page == page) TactileGrid.snapWidget(w, canvasAspect, next) else w
        }
        app.config = app.config.copy(tactileGridColumns = next, widgets = snapped)
        widgetsEpoch++
        app.noteWidgetsChanged()
        app.persist()
        onConfigChanged()
    }

    LaunchedEffect(selectedId, page) {
        catalogSuggestedLabel = null
        catalogQuery = ""
    }

    LaunchedEffect(selectedId, page, app.config.mappings, app.config.widgets) {
        val w = app.config.widgetsOn(page).find { it.id == selectedId }
        val m = w?.let { widget ->
            app.config.mappings.firstOrNull { it.controlId == widget.id && it.page == page }
        }
        labelText = w?.label.orEmpty()
        addressLines = m.toEditableAddressLines()
        argKind = m?.argKind ?: if (w?.kind == TactileKind.FADER) ArgKind.FLOAT else ArgKind.MOMENTARY
        floatValueText = formatFloatValue(m?.floatValue ?: 1f)
        floatMinText = formatFreeFloat(m?.floatMin ?: 0f)
        floatMaxText = formatFreeFloat(m?.floatMax ?: 1f)
        customValues = m?.customValues ?: false
        onValueText = (m?.onValue ?: 1).toString()
        offValueText = (m?.offValue ?: 0).toString()
        invert = m?.invert ?: false
        toggleOn = m?.toggleOn ?: false
    }

    val catalogHits = remember(catalogQuery, app.catalog, selected?.kind) {
        val q = catalogQuery.trim().lowercase()
        // Empty query → no hits. Dumping the first 80 catalog rows buried Libellé /
        // Adresse OSC / Appliquer under a huge list on Thor landscape.
        if (q.isEmpty()) return@remember emptyList()
        val base = app.catalog.filter {
            it.label.lowercase().contains(q) || it.address.lowercase().contains(q)
        }.take(120)
        // Faders: prefer FLOAT commands first, keep BANG visible after.
        if (selected?.kind == TactileKind.FADER) {
            base.sortedBy { if (it.argKind == ArgKind.FLOAT) 0 else 1 }
        } else {
            base
        }
    }

    fun persistWidget(w: TactileWidget, notifyExternal: Boolean = true) {
        app.config = app.config.upsertWidget(w)
        widgetsEpoch++
        // Always notify peer Compose trees (Play / other Mapping) of geometry.
        app.noteWidgetsChanged()
        if (notifyExternal) {
            app.persist()
            onConfigChanged()
        }
    }

    fun addWidget(kind: TactileKind) {
        val count = widgets.size
        val col = count % 4
        val row = count / 4
        val id = UUID.randomUUID().toString()
        val spec = ThorDefaultOsc.defaultAddSpec(kind, app.config.mappings)
        val w = TactileGrid.snapWidget(
            TactileWidget(
                id = id,
                page = page,
                kind = kind,
                label = spec.label,
                x = 0.05f + col * 0.24f,
                y = 0.08f + row * 0.28f,
                w = if (kind == TactileKind.FADER) 0.14f else 0.20f,
                h = if (kind == TactileKind.FADER) 0.40f else 0.16f,
            ),
            canvasAspect,
            gridColumns,
        )
        val mapping = ControlMapping(
            controlId = id,
            address = spec.address,
            argKind = spec.argKind,
            page = page,
            floatMin = if (kind == TactileKind.FADER) 0f else null,
            floatMax = if (kind == TactileKind.FADER) 1f else null,
        )
        app.config = app.config.upsertWidget(w).copy(
            mappings = app.config.mappings.filterNot { it.controlId == id && it.page == page } + mapping,
        )
        widgetsEpoch++
        app.noteWidgetsChanged()
        app.persist()
        onConfigChanged()
        selectedId = id
        Toast.makeText(context, "${kind.name.lowercase()} ajouté", Toast.LENGTH_SHORT).show()
    }

    fun pickCatalog(entry: CatalogEntry) {
        addressLines = listOf(entry.address)
        catalogSuggestedLabel = entry.label
        when (selected?.kind) {
            TactileKind.FADER -> {
                if (entry.argKind != ArgKind.FLOAT) {
                    Toast.makeText(
                        context,
                        "Fader : FLOAT forcé (commande catalogue en ${entry.argKind.name})",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                argKind = ArgKind.FLOAT
            }
            else -> argKind = entry.argKind
        }
    }

    fun saveMapping() {
        val w = selected ?: return
        val saved = addressLines.toSavedAddresses() ?: run {
            Toast.makeText(context, "Indique une adresse OSC", Toast.LENGTH_SHORT).show()
            return
        }
        val continuous = w.kind == TactileKind.FADER
        if (continuous && argKind != ArgKind.FLOAT) {
            Toast.makeText(context, "Les faders nécessitent FLOAT", Toast.LENGTH_SHORT).show()
            return
        }
        val fv = if (!continuous && argKind == ArgKind.FLOAT) {
            parseFloat01(floatValueText) ?: 1f
        } else 1f
        val fMin: Float?
        val fMax: Float?
        if (continuous) {
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
        val digitalOn: Int
        val digitalOff: Int
        val useCustom: Boolean
        val useInvert: Boolean
        if (!continuous && (argKind == ArgKind.MOMENTARY || argKind == ArgKind.TOGGLE)) {
            useCustom = customValues
            if (useCustom) {
                digitalOn = onValueText.trim().toIntOrNull() ?: run {
                    Toast.makeText(context, "On INT invalide", Toast.LENGTH_SHORT).show()
                    return
                }
                digitalOff = offValueText.trim().toIntOrNull() ?: run {
                    Toast.makeText(context, "Off INT invalide", Toast.LENGTH_SHORT).show()
                    return
                }
            } else {
                digitalOn = 1
                digitalOff = 0
            }
            useInvert = !useCustom && invert
        } else {
            digitalOn = 1
            digitalOff = 0
            useCustom = false
            useInvert = false
        }
        val without = app.config.mappings.filterNot { it.controlId == w.id && it.page == page }
        val labeled = w.copy(label = labelText.trim())
        app.config = app.config
            .upsertWidget(labeled)
            .copy(
                mappings = without + ControlMapping(
                    controlId = w.id,
                    address = saved.first,
                    argKind = argKind,
                    page = page,
                    extraAddresses = saved.second,
                    floatValue = fv,
                    floatMin = fMin,
                    floatMax = fMax,
                    customValues = useCustom,
                    onValue = digitalOn,
                    offValue = digitalOff,
                    invert = useInvert,
                    toggleOn = toggleOn,
                ),
            )
        addressLines = listOf(saved.first) + saved.second
        widgetsEpoch++ // refresh canvas label after Libellé change
        app.noteWidgetsChanged()
        app.hydrateTactileToggleLit()
        app.persist()
        onConfigChanged()
        showEditTrigger = false
        Toast.makeText(context, "Mapping enregistré", Toast.LENGTH_SHORT).show()
    }

    fun clearMapping() {
        val w = selected ?: return
        app.config = app.config.copy(
            mappings = app.config.mappings.filterNot { it.controlId == w.id && it.page == page },
        )
        addressLines = listOf("")
        app.hydrateTactileToggleLit()
        app.persist()
        onConfigChanged()
    }

    fun deleteWidget() {
        val id = selectedId ?: return
        app.config = app.config.removeWidget(id)
        selectedId = null
        widgetsEpoch++
        app.noteWidgetsChanged()
        app.hydrateTactileToggleLit()
        app.persist()
        onConfigChanged()
    }

    fun testOsc() {
        val w = selected ?: return
        val continuous = w.kind == TactileKind.FADER
        if (app.config.activePage != page) {
            app.config = app.config.copy(activePage = page)
        }
        val line = app.emitControl(w.id, if (continuous) 0.5f else 1f, continuous, page = page)
        Toast.makeText(
            context,
            line ?: "Non mappé",
            Toast.LENGTH_SHORT,
        ).show()
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Compact single strip: grille dropdown + actions (max canvas height).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TactileGridDensityDropdown(
                    columns = gridColumns,
                    onColumnsChange = ::setGridColumns,
                )
                Button(onClick = { addWidget(TactileKind.TRIGGER) }) { Text("+ Trigger") }
                Button(onClick = { addWidget(TactileKind.FADER) }) { Text("+ Fader") }
                if (selected != null) {
                    Button(onClick = { showEditTrigger = true }) {
                        Text("Edit Trigger")
                    }
                    OutlinedButton(onClick = ::deleteWidget) { Text("Supprimer") }
                }
            }
            TactilePlayCanvas(
                app = app,
                page = page,
                widgets = widgets,
                editable = true,
                selectedId = selectedId,
                onSelect = { selectedId = it },
                onWidgetMoved = { w, commit -> persistWidget(w, notifyExternal = commit) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }

        if (showEditTrigger && selected != null) {
            InTreeModal(onDismiss = { showEditTrigger = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 720.dp)
                        .fillMaxHeight(0.92f),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Edit Trigger",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${selected.kind.name} · " +
                                        if (addressLines.any { it.isNotBlank() }) "mappé" else "libre",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(
                                onClick = ::testOsc,
                                enabled = addressLines.any { it.isNotBlank() },
                            ) { Text("Test") }
                            TextButton(onClick = { showEditTrigger = false }) { Text("Fermer") }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TactileSizeColorPanel(
                                widget = selected,
                                aspectWH = canvasAspect,
                                gridColumns = gridColumns,
                                onSizeChange = { persistWidget(it) },
                                onFillChange = { hex ->
                                    persistWidget(
                                        selected.copy(fillColor = hex)
                                            .clamped(canvasAspect, gridColumns),
                                    )
                                },
                                onOpenCustom = { showCustomColor = true },
                            )
                            OutlinedTextField(
                                value = labelText,
                                onValueChange = { labelText = it },
                                label = { Text("Libellé") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val suggestion = catalogSuggestedLabel?.trim().orEmpty()
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
                                    if (labelText != suggestion) {
                                        TextButton(onClick = { labelText = suggestion }) {
                                            Text("Défaut")
                                        }
                                    }
                                }
                            }
                            MultiAddressEditor(
                                lines = addressLines,
                                onLinesChange = { addressLines = it },
                                onOpenLearnCopy = { learnCopyLineIndex = it },
                                onRequestDelete = { pendingDeleteLine = it },
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (selected.kind == TactileKind.TRIGGER) {
                                    listOf(ArgKind.MOMENTARY, ArgKind.TOGGLE, ArgKind.FLOAT)
                                        .forEach { kind ->
                                            FilterChip(
                                                selected = argKind == kind,
                                                onClick = { argKind = kind },
                                                label = { Text(kind.chipLabel()) },
                                            )
                                        }
                                } else {
                                    FilterChip(
                                        selected = true,
                                        onClick = { argKind = ArgKind.FLOAT },
                                        label = { Text("FLOAT") },
                                    )
                                }
                            }
                            if (selected.kind == TactileKind.TRIGGER &&
                                (argKind == ArgKind.MOMENTARY || argKind == ArgKind.TOGGLE)
                            ) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !customValues,
                                        onClick = { customValues = false },
                                        label = { Text("INT 0/1") },
                                    )
                                    FilterChip(
                                        selected = customValues,
                                        onClick = { customValues = true },
                                        label = { Text("Custom") },
                                    )
                                }
                                if (customValues) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = onValueText,
                                            onValueChange = {
                                                if (it.isEmpty() || it.matches(Regex("-?\\d*"))) {
                                                    onValueText = it
                                                }
                                            },
                                            label = { Text("On") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        OutlinedTextField(
                                            value = offValueText,
                                            onValueChange = {
                                                if (it.isEmpty() || it.matches(Regex("-?\\d*"))) {
                                                    offValueText = it
                                                }
                                            },
                                            label = { Text("Off") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = invert, onCheckedChange = { invert = it })
                                        Text("Invert")
                                    }
                                }
                            }
                            if (selected.kind == TactileKind.TRIGGER && argKind == ArgKind.FLOAT) {
                                OutlinedTextField(
                                    value = floatValueText,
                                    onValueChange = { floatValueText = it },
                                    label = { Text("Valeur FLOAT (0…1)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (selected.kind == TactileKind.FADER) {
                                OutlinedTextField(
                                    value = floatMinText,
                                    onValueChange = { floatMinText = it },
                                    label = { Text("Float min") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = floatMaxText,
                                    onValueChange = { floatMaxText = it },
                                    label = { Text("Float max") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            OutlinedTextField(
                                value = catalogQuery,
                                onValueChange = { catalogQuery = it },
                                label = { Text("Chercher dans le catalogue Resolume Arena") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState())
                                            .padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        catalogHits.forEach { entry ->
                                            TextButton(
                                                onClick = { pickCatalog(entry) },
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Column(Modifier.fillMaxWidth()) {
                                                    Text(
                                                        entry.label,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
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
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = ::saveMapping,
                                enabled = addressLines.any { it.isNotBlank() },
                                modifier = Modifier.weight(1f),
                            ) { Text("Appliquer") }
                            OutlinedButton(
                                onClick = ::clearMapping,
                                modifier = Modifier.weight(1f),
                            ) { Text("Effacer") }
                        }
                    }
                }
            }
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
        if (showCustomColor && selected != null) {
            // Same shell as Edit Trigger — Popup card was overflowing landscape Thor.
            InTreeModal(onDismiss = { showCustomColor = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 720.dp)
                        .fillMaxHeight(0.92f),
                ) {
                    TactileCustomColorPicker(
                        initialHex = selected.resolvedFillHex(),
                        history = app.config.customColorHistory,
                        onConfirm = { hex ->
                            val w = selected.copy(fillColor = hex).clamped(canvasAspect, gridColumns)
                            app.config = app.config.upsertWidget(w).pushCustomColor(hex)
                            widgetsEpoch++
                            app.noteWidgetsChanged()
                            app.persist()
                            onConfigChanged()
                            showCustomColor = false
                        },
                        onDismiss = { showCustomColor = false },
                    )
                }
            }
        }
    }
}

/** Compact grille picker — DropdownMenu uses Popup (Presentation-safe, no Dialog). */
@Composable
private fun TactileGridDensityDropdown(
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Grille $columns ▾")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TactileGrid.COLUMN_CHOICES.forEach { cols ->
                DropdownMenuItem(
                    text = { Text("$cols") },
                    onClick = {
                        onColumnsChange(cols)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TactileSizeColorPanel(
    widget: TactileWidget,
    aspectWH: Float,
    gridColumns: Int,
    onSizeChange: (TactileWidget) -> Unit,
    onFillChange: (String) -> Unit,
    onOpenCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cw = TactileGrid.cellWidth(gridColumns)
    val ch = TactileGrid.cellHeight(aspectWH, gridColumns)
    val wCells = TactileGrid.cellsAlong(widget.w, cw)
    val hCells = TactileGrid.cellsAlong(widget.h, ch)
    val current = widget.resolvedFillHex()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Taille",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CellStepper(
            label = "L",
            value = wCells,
            onMinus = {
                onSizeChange(
                    TactileGrid.bumpSize(
                        widget,
                        deltaWCells = -1,
                        deltaHCells = 0,
                        aspectWH = aspectWH,
                        columns = gridColumns,
                    ),
                )
            },
            onPlus = {
                onSizeChange(
                    TactileGrid.bumpSize(
                        widget,
                        deltaWCells = 1,
                        deltaHCells = 0,
                        aspectWH = aspectWH,
                        columns = gridColumns,
                    ),
                )
            },
        )
        CellStepper(
            label = "H",
            value = hCells,
            onMinus = {
                onSizeChange(
                    TactileGrid.bumpSize(
                        widget,
                        deltaWCells = 0,
                        deltaHCells = -1,
                        aspectWH = aspectWH,
                        columns = gridColumns,
                    ),
                )
            },
            onPlus = {
                onSizeChange(
                    TactileGrid.bumpSize(
                        widget,
                        deltaWCells = 0,
                        deltaHCells = 1,
                        aspectWH = aspectWH,
                        columns = gridColumns,
                    ),
                )
            },
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TACTILE_COLOR_PRESETS.forEach { hex ->
                val selectedChip = normalizeFillHex(hex) == current
                val argb = parseFillArgb(hex) ?: return@forEach
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (selectedChip) 2.dp else 1.dp,
                            color = if (selectedChip) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .clickable { onFillChange(hex) },
                )
            }
            Box(
                Modifier
                    .height(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenCustom)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Custom", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun CellStepper(
    label: String,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        CompactStepButton("−", onMinus)
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        CompactStepButton("+", onPlus)
    }
}

@Composable
private fun CompactStepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TactileCustomColorPicker(
    initialHex: String,
    history: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = rgbHexToHsv(initialHex) ?: Triple(140f, 0.7f, 0.85f)
    var hue by remember { mutableFloatStateOf(start.first) }
    var sat by remember { mutableFloatStateOf(start.second) }
    var value by remember { mutableFloatStateOf(start.third) }
    var hexField by remember { mutableStateOf(initialHex.removePrefix("#")) }
    val previewHex = remember(hue, sat, value) { hsvToRgbHex(hue, sat, value) }
    val previewColor = Color(parseFillArgb(previewHex) ?: 0xFF3DDC84.toInt())
    val hueThumb = remember(hue) { hsvColor(hue, 1f, 1f) }
    val satThumb = remember(hue, sat, value) { hsvColor(hue, sat, value.coerceAtLeast(0.15f)) }
    val valueThumb = remember(hue, sat, value) { hsvColor(hue, sat, value.coerceAtLeast(0.08f)) }
    val hueTrack = remember {
        listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { hsvColor(it, 1f, 1f) }
    }
    val satTrack = remember(hue, value) {
        listOf(hsvColor(hue, 0f, value.coerceAtLeast(0.2f)), hsvColor(hue, 1f, value.coerceAtLeast(0.2f)))
    }
    val valueTrack = remember(hue, sat) {
        listOf(
            Color.Black,
            hsvColor(hue, sat, 0.5f),
            hsvColor(hue, sat, 1f),
        )
    }
    fun applyHexToPicker(hex: String) {
        val n = normalizeFillHex(hex) ?: return
        hexField = n.removePrefix("#")
        rgbHexToHsv(n)?.let { (h, s, v) ->
            hue = h
            sat = s
            value = v
        }
    }
    LaunchedEffect(hue, sat, value) {
        hexField = previewHex.removePrefix("#")
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Couleur custom",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        // QCM 3C: last 6 custom colors (project) — tap fills picker only.
        // Hex sits to the right of Récentes to free vertical space for HSV.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text("Récentes", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(AppConfig.CUSTOM_COLOR_HISTORY_MAX) { idx ->
                        val hex = history.getOrNull(idx)
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (hex != null) {
                                        Color(parseFillArgb(hex) ?: 0xFF888888.toInt())
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    },
                                )
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .then(
                                    if (hex != null) {
                                        Modifier.clickable { applyHexToPicker(hex) }
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
            // Compact Hex (no floating label) so all 3 HSV sliders keep vertical room.
            Row(
                Modifier
                    .width(168.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Hex #",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BasicTextField(
                    value = hexField,
                    onValueChange = { raw ->
                        val cleaned = raw.filter { it.isLetterOrDigit() }.take(6).uppercase()
                        hexField = cleaned
                        normalizeFillHex("#$cleaned")?.let { applyHexToPicker(it) }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // QCM 1A: shortened HSV sliders (left column) — keep all three visible.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Teinte", style = MaterialTheme.typography.labelSmall)
                    HsvGradientSlider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f,
                        trackColors = hueTrack,
                        thumbColor = hueThumb,
                        modifier = Modifier.fillMaxWidth(0.88f),
                        compact = true,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Saturation", style = MaterialTheme.typography.labelSmall)
                    HsvGradientSlider(
                        value = sat,
                        onValueChange = { sat = it },
                        valueRange = 0f..1f,
                        trackColors = satTrack,
                        thumbColor = satThumb,
                        modifier = Modifier.fillMaxWidth(0.88f),
                        compact = true,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Valeur", style = MaterialTheme.typography.labelSmall)
                    HsvGradientSlider(
                        value = value,
                        onValueChange = { value = it },
                        valueRange = 0f..1f,
                        trackColors = valueTrack,
                        thumbColor = valueThumb,
                        modifier = Modifier.fillMaxWidth(0.88f),
                        compact = true,
                    )
                }
            }
            // QCM 2C: vertical rect preview to the right of the sliders.
            Box(
                Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(previewColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onDismiss) { Text("Annuler") }
            Button(
                onClick = {
                    val hex = normalizeFillHex("#$hexField") ?: previewHex
                    onConfirm(hex)
                },
            ) { Text("OK") }
        }
    }
}

/** HSV color as Compose [Color] via existing hex helpers. */
private fun hsvColor(h: Float, s: Float, v: Float): Color =
    Color(parseFillArgb(hsvToRgbHex(h, s, v)) ?: 0xFFFFFFFF.toInt())

/**
 * Custom HSV channel slider: real gradient track + circular thumb tinted to the
 * channel color (white ring so it stays visible on dark ends).
 */
@Composable
private fun HsvGradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    trackColors: List<Color>,
    thumbColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val thumbDp = if (compact) 18.dp else 22.dp
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(if (compact) 28.dp else 36.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val thumbPx = with(density) { thumbDp.toPx() }
        val travel = (widthPx - thumbPx).coerceAtLeast(1f)
        val start = valueRange.start
        val end = valueRange.endInclusive
        val span = (end - start).takeIf { it != 0f } ?: 1f
        val frac = ((value - start) / span).coerceIn(0f, 1f)
        fun valueAt(x: Float): Float {
            val f = ((x - thumbPx / 2f) / travel).coerceIn(0f, 1f)
            return start + f * span
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (compact) 8.dp else 12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(trackColors))
                .pointerInput(valueRange, widthPx) {
                    detectTapGestures { pos -> onValueChange(valueAt(pos.x)) }
                }
                .pointerInput(valueRange, widthPx) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onValueChange(valueAt(change.position.x))
                    }
                },
        )
        Box(
            Modifier
                .offset { IntOffset((frac * travel).roundToInt(), 0) }
                .size(thumbDp)
                .border(2.dp, Color.White, CircleShape)
                .clip(CircleShape)
                .background(thumbColor)
                .pointerInput(value, valueRange, widthPx) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val cur = ((value - start) / span).coerceIn(0f, 1f)
                        val next = (cur + dragAmount / travel).coerceIn(0f, 1f)
                        onValueChange(start + next * span)
                    }
                },
        )
    }
}
