package com.mareg74.osccontroller.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mareg74.osccontroller.ThorOscApp
import com.mareg74.osccontroller.config.ColorPalette
import com.mareg74.osccontroller.config.PageType
import com.mareg74.osccontroller.config.ThemeMode
import com.mareg74.osccontroller.mapping.ControlId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PrimaryScreen(
    app: ThorOscApp,
    dualDisplay: Boolean,
    lastFired: String,
    unmappedHint: String,
    controlLevels: Map<ControlId, Float>,
    stickCalibrating: Boolean = false,
    calibPeakLeft: Float = 0f,
    calibPeakRight: Float = 0f,
    onStartStickCalibration: () -> Unit = {},
    onFinishStickCalibration: () -> Unit = {},
    onCancelStickCalibration: () -> Unit = {},
    onResetStickCalibration: () -> Unit = {},
    onConfigChanged: () -> Unit,
) {
    val destinations = listOf(
        "Play",
        "Moniteur OSC",
        "Moniteur NDI",
        "Mapping Triggers",
        "Projets",
        "Réglages",
    )
    // Remount chrome when dual/mono flips — avoids stale destination indices.
    key(dualDisplay) {
        var selected by remember(dualDisplay) {
            mutableStateOf("Play")
        }
        val safeSelected = if (selected in destinations) selected else destinations.first()
        // Mute hardware pad OSC while Mapping is open (QCM except Mapping).
        LaunchedEffect(safeSelected) {
            app.primaryDestination = safeSelected
        }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var showMappingNewPage by remember { mutableStateOf(false) }
        var mappingNewPageName by remember { mutableStateOf("") }
        var mappingNewPageType by remember { mutableStateOf(PageType.TACTILE) }

        Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                // Flush to bottom: no bottom system-bar inset, only round the trailing edge.
                ModalDrawerSheet(
                    modifier = Modifier.fillMaxHeight(),
                    drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    Column(
                        Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(
                                WindowInsets.systemBars.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.Start,
                                ),
                            ),
                    ) {
                        Text(
                            "Navigation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                        )
                        destinations.forEach { title ->
                            NavigationDrawerItem(
                                label = { Text(title) },
                                selected = safeSelected == title,
                                onClick = {
                                    selected = title
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
            },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    // Keep content clear of the status bar; draw through the gesture bar
                    // so there is no black strip at the bottom.
                    .statusBarsPadding(),
            ) {
                // Play uses its own compact top bar (☰ + centered page switcher).
                // Other screens keep the classic ☰ + title strip.
                // Mapping tactile: page switcher centered in the title bar.
                if (safeSelected != "Play") {
                    val mappingTactile = safeSelected == "Mapping Triggers" &&
                        app.config.pageType(app.config.activePage) == PageType.TACTILE
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Row(
                            Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(if (mappingTactile) 0.34f else 1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "☰",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clickable { scope.launch { drawerState.open() } }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                            Text(
                                safeSelected,
                                style = if (mappingTactile) {
                                    MaterialTheme.typography.titleSmall
                                } else {
                                    MaterialTheme.typography.titleMedium
                                },
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (mappingTactile) {
                            PageSwitcher(
                                page = app.config.activePage,
                                pageLabel = app.config.pageLabel(app.config.activePage),
                                lastPageIndex = app.config.lastPageIndex(),
                                onPageChange = { newPage ->
                                    val clamped = newPage.coerceIn(0, app.config.lastPageIndex())
                                    app.config = app.config.copy(activePage = clamped)
                                    app.persist()
                                    onConfigChanged()
                                },
                                onAddPage = {
                                    mappingNewPageName = ""
                                    mappingNewPageType = PageType.TACTILE
                                    showMappingNewPage = true
                                },
                                pageTitles = app.config.pageTitleList(),
                                pageIsTactile = app.config.pageTactileFlags(),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }
                when (safeSelected) {
                    "Play" -> PlayTab(
                        app = app,
                        lastFired = lastFired,
                        unmappedHint = unmappedHint,
                        controlLevels = controlLevels,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onConfigChanged = onConfigChanged,
                    )
                    "Moniteur OSC" -> MonitorPanel(app, onConfigChanged)
                    "Moniteur NDI" -> NdiMonitorPage(app)
                    "Mapping Triggers" -> MapTab(app, controlLevels, onConfigChanged)
                    "Projets" -> ProjectsTab(app, onConfigChanged)
                    "Réglages" -> SettingsTab(
                        app = app,
                        stickCalibrating = stickCalibrating,
                        calibPeakLeft = calibPeakLeft,
                        calibPeakRight = calibPeakRight,
                        onStartStickCalibration = onStartStickCalibration,
                        onFinishStickCalibration = onFinishStickCalibration,
                        onCancelStickCalibration = onCancelStickCalibration,
                        onResetStickCalibration = onResetStickCalibration,
                        onConfigChanged = onConfigChanged,
                    )
                }
            }
        }
        if (showMappingNewPage) {
            NewPageOverlay(
                name = mappingNewPageName,
                onNameChange = { mappingNewPageName = it },
                pageType = mappingNewPageType,
                onPageTypeChange = { mappingNewPageType = it },
                placeholder = "Page ${app.config.pageCount}",
                onConfirm = {
                    app.config = app.config.addPage(mappingNewPageName, mappingNewPageType)
                    app.persist()
                    onConfigChanged()
                    showMappingNewPage = false
                    Toast.makeText(
                        context,
                        "Page « ${app.config.pageLabel(app.config.activePage)} » créée",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onDismiss = { showMappingNewPage = false },
            )
        }
        } // Box
    }
}

@Composable
private fun PlayTab(
    app: ThorOscApp,
    lastFired: String,
    unmappedHint: String,
    controlLevels: Map<ControlId, Float>,
    onOpenDrawer: () -> Unit,
    onConfigChanged: () -> Unit,
) {
    var feedbackTick by remember { mutableIntStateOf(0) }
    // Local Compose state so PAGE updates immediately (app.config alone is not observable).
    var page by remember { mutableIntStateOf(app.config.activePage) }
    var touchFired by remember { mutableStateOf("") }
    LaunchedEffect(app.config.activePage) {
        page = app.config.activePage
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            feedbackTick++
        }
    }
    feedbackTick // refresh legends with OSC feedback

    fun setPage(newPage: Int) {
        val clamped = newPage.coerceIn(0, app.config.lastPageIndex())
        page = clamped
        app.config = app.config.copy(activePage = clamped)
        app.persist()
        onConfigChanged()
    }

    if (app.config.pageType(page) == PageType.TACTILE) {
        PlayTactileLayout(
            app = app,
            page = page,
            lastFired = touchFired.ifBlank { lastFired },
            onOpenDrawer = onOpenDrawer,
            onPageChange = ::setPage,
            onLastFired = { touchFired = it },
            tactileOnly = false,
        )
    } else {
        PlayGamepadLayout(
            app = app,
            levels = controlLevels,
            lastFired = lastFired,
            unmappedHint = unmappedHint,
            page = page,
            onOpenDrawer = onOpenDrawer,
            onPageChange = ::setPage,
        )
    }
}

@Composable
fun SettingsTab(
    app: ThorOscApp,
    stickCalibrating: Boolean,
    calibPeakLeft: Float,
    calibPeakRight: Float,
    onStartStickCalibration: () -> Unit,
    onFinishStickCalibration: () -> Unit,
    onCancelStickCalibration: () -> Unit,
    onResetStickCalibration: () -> Unit,
    onConfigChanged: () -> Unit,
) {
    var ip by remember(app.config.targetIp) { mutableStateOf(app.config.targetIp) }
    var targetPort by remember(app.config.targetPort) { mutableStateOf(app.config.targetPort.toString()) }
    var listenPort by remember(app.config.listenPort) { mutableStateOf(app.config.listenPort.toString()) }
    var forceSingle by remember(app.config.forceSingleDisplay) {
        mutableStateOf(app.config.forceSingleDisplay)
    }
    var themeMode by remember(app.config.themeMode) { mutableStateOf(app.config.themeMode) }
    var colorPalette by remember(app.config.colorPalette) { mutableStateOf(app.config.colorPalette) }
    var stickCurve by remember(app.config.stickCurve) { mutableFloatStateOf(app.config.stickCurve) }
    var triggerCurve by remember(app.config.triggerCurve) { mutableFloatStateOf(app.config.triggerCurve) }
    var showDeletePagePicker by remember { mutableStateOf(false) }
    var pendingDeletePage by remember { mutableStateOf<Int?>(null) }
    var showRenamePagePicker by remember { mutableStateOf(false) }
    var renamePageIndex by remember { mutableStateOf<Int?>(null) }
    var renamePageDraft by remember { mutableStateOf("") }
    var showNewPageDialog by remember { mutableStateOf(false) }
    var newPageName by remember { mutableStateOf("") }
    var newPageType by remember { mutableStateOf(PageType.PHYSICAL) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        SettingsSection("Réseau OSC") {
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP Resolume (sortie OSC)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = targetPort,
                onValueChange = { targetPort = it },
                label = { Text("Port sortie") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = listenPort,
                onValueChange = { listenPort = it },
                label = { Text("Port écoute (feedback)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Dans Resolume → OSC output vers IP du Thor et ce port listen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection("Affichage") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Forcer UI mono-écran")
                    Text(
                        "Désactive le 2e écran (Moniteur OSC reste dans la liste) — immédiat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = forceSingle,
                    onCheckedChange = {
                        forceSingle = it
                        app.config = app.config.copy(forceSingleDisplay = it)
                        app.persist()
                        onConfigChanged()
                        Toast.makeText(
                            context,
                            if (it) "UI mono-écran activée" else "UI dual-écran restaurée",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }

            Text("Thème", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to "Système",
                    ThemeMode.LIGHT to "Clair",
                    ThemeMode.DARK to "Sombre",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = {
                            themeMode = mode
                            // Aperçu immédiat du thème (persisté tout de suite).
                            app.config = app.config.copy(themeMode = mode)
                            app.persist()
                            onConfigChanged()
                        },
                        label = { Text(label) },
                    )
                }
            }

            Text("Palette de couleurs", style = MaterialTheme.typography.titleSmall)
            // Always render every enum entry so new palettes cannot be omitted from the UI.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPalette.entries.toList().chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { palette ->
                            FilterChip(
                                selected = colorPalette == palette,
                                onClick = {
                                    colorPalette = palette
                                    app.config = app.config.copy(colorPalette = palette)
                                    app.persist()
                                    onConfigChanged()
                                },
                                label = { Text(palette.labelFr()) },
                            )
                        }
                    }
                }
            }
        }

        SettingsSection("Réponse analogique") {
            Text(
                "Courbe sticks (${"%.1f".format(stickCurve)})",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "1 = linéaire · plus élevé = milieu plus doux",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = stickCurve,
                onValueChange = { stickCurve = it },
                valueRange = 1f..4f,
                steps = 5,
            )

            Text(
                "Courbe gâchettes (${"%.1f".format(triggerCurve)})",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "1 = linéaire · plus élevé = début de course plus doux",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = triggerCurve,
                onValueChange = { triggerCurve = it },
                valueRange = 1f..3f,
                steps = 3,
            )
        }

        SettingsSection("Pages de mapping") {
            Text(
                "Active : ${app.config.pageLabel(app.config.activePage)} · ${app.config.pageCount} page(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        newPageName = ""
                        newPageType = PageType.PHYSICAL
                        showNewPageDialog = true
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Ajouter") }
                OutlinedButton(
                    onClick = {
                        app.config = app.config.copyPage()
                        app.persist()
                        onConfigChanged()
                        Toast.makeText(context, "Page copiée", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Copier") }
            }
            OutlinedButton(
                onClick = { showDeletePagePicker = true },
                enabled = app.config.pageCount > 1,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Supprimer une page") }

            OutlinedButton(
                onClick = { showRenamePagePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Changer le nom d'une page")
            }
        }

        SettingsSection("Calibration sticks") {
            Text(
                "Pousse chaque stick jusqu’à la butée physique, puis valide. " +
                    "Si le firmware plafonne déjà à 1.0 avant la butée, la cal ne peut pas inventer de course.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Actuel G ${"%.2f".format(app.config.stickLeftFull)} · " +
                    "D ${"%.2f".format(app.config.stickRightFull)} " +
                    "(1.00 = pas de recalage)",
                style = MaterialTheme.typography.bodySmall,
            )
            if (stickCalibrating) {
                Text(
                    "Pics live G ${"%.2f".format(calibPeakLeft)} · " +
                        "D ${"%.2f".format(calibPeakRight)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            onFinishStickCalibration()
                            onConfigChanged()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Valider")
                    }
                    OutlinedButton(
                        onClick = onCancelStickCalibration,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Annuler")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onStartStickCalibration,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Calibrer")
                    }
                    TextButton(onClick = {
                        onResetStickCalibration()
                        onConfigChanged()
                    }) {
                        Text("Reset")
                    }
                }
            }
        }

        app.log.listenError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        } // scrollable content

        // Transparent fixed footer — Appliquer always visible, no scroll needed.
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        ) {
            Button(
                onClick = {
                    app.config = app.config.copy(
                        targetIp = ip.trim(),
                        targetPort = targetPort.toIntOrNull() ?: app.config.targetPort,
                        listenPort = listenPort.toIntOrNull() ?: app.config.listenPort,
                        // mono-écran / thème / palette déjà persistés immédiatement via leurs contrôles
                        forceSingleDisplay = forceSingle,
                        themeMode = themeMode,
                        colorPalette = colorPalette,
                        stickCurve = stickCurve,
                        triggerCurve = triggerCurve,
                    )
                    app.persist()
                    app.restartReceiver()
                    onConfigChanged()
                    Toast.makeText(context, "Paramètres appliqués", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Appliquer")
            }
        }
    }

    if (showDeletePagePicker) {
        PresentationSafePopup(onDismiss = { showDeletePagePicker = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Supprimer une page",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Choisissez la page à supprimer. Il doit rester au moins une page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    (0..app.config.lastPageIndex()).forEach { index ->
                        val label = app.config.pageLabel(index)
                        val activeMark = if (index == app.config.activePage) " · active" else ""
                        OutlinedButton(
                            onClick = {
                                showDeletePagePicker = false
                                pendingDeletePage = index
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("$label$activeMark")
                        }
                    }
                    TextButton(
                        onClick = { showDeletePagePicker = false },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Annuler")
                    }
                }
            }
        }
    }

    if (showRenamePagePicker) {
        PresentationSafePopup(onDismiss = { showRenamePagePicker = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Changer le nom d'une page",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Choisissez la page à renommer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    (0..app.config.lastPageIndex()).forEach { index ->
                        val label = app.config.pageLabel(index)
                        val activeMark = if (index == app.config.activePage) " · active" else ""
                        OutlinedButton(
                            onClick = {
                                showRenamePagePicker = false
                                renamePageIndex = index
                                renamePageDraft = app.config.pageNames[index] ?: ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("$label$activeMark")
                        }
                    }
                    TextButton(
                        onClick = { showRenamePagePicker = false },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Annuler")
                    }
                }
            }
        }
    }

    renamePageIndex?.let { index ->
        PresentationSafePopup(onDismiss = { renamePageIndex = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Renommer la page",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Page $index · actuel : ${app.config.pageLabel(index)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = renamePageDraft,
                        onValueChange = { renamePageDraft = it },
                        label = { Text("Nouveau nom") },
                        placeholder = { Text("Page $index") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(onClick = { renamePageIndex = null }) {
                            Text("Annuler")
                        }
                        Button(
                            onClick = {
                                val trimmed = renamePageDraft.trim()
                                val names = if (trimmed.isEmpty()) {
                                    app.config.pageNames - index
                                } else {
                                    app.config.pageNames + (index to trimmed)
                                }
                                app.config = app.config.copy(pageNames = names)
                                app.persist()
                                onConfigChanged()
                                renamePageIndex = null
                                Toast.makeText(
                                    context,
                                    if (trimmed.isEmpty()) {
                                        "Nom réinitialisé (Page $index)"
                                    } else {
                                        "Page renommée « $trimmed »"
                                    },
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        ) {
                            Text("Enregistrer")
                        }
                    }
                }
            }
        }
    }

    pendingDeletePage?.let { index ->
        val label = app.config.pageLabel(index)
        PresentationSafePopup(onDismiss = { pendingDeletePage = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Confirmer la suppression",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Supprimer définitivement « $label » et ses mappings ? Cette action est irréversible.",
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = { pendingDeletePage = null }) {
                        Text("Annuler")
                    }
                    TextButton(
                        onClick = {
                            if (app.config.pageCount <= 1) {
                                Toast.makeText(
                                    context,
                                    "Impossible : il faut au moins une page",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                pendingDeletePage = null
                                return@TextButton
                            }
                            app.config = app.config.deletePage(index)
                            app.persist()
                            onConfigChanged()
                            pendingDeletePage = null
                            Toast.makeText(context, "« $label » supprimée", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text("Supprimer")
                    }
                }
            }
        }
    }

    if (showNewPageDialog) {
        NewPageOverlay(
            name = newPageName,
            onNameChange = { newPageName = it },
            pageType = newPageType,
            onPageTypeChange = { newPageType = it },
            placeholder = "Page ${app.config.pageCount}",
            onConfirm = {
                app.config = app.config.addPage(newPageName, newPageType)
                app.persist()
                onConfigChanged()
                showNewPageDialog = false
                Toast.makeText(
                    context,
                    "Page « ${app.config.pageLabel(app.config.activePage)} » créée",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = { showNewPageDialog = false },
        )
    }
    } // Box
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

