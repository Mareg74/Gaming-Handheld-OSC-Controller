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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mareg74.osccontroller.ThorOscApp
import com.mareg74.osccontroller.config.PageType
import com.mareg74.osccontroller.mapping.ControlId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SecondaryScreen(
    app: ThorOscApp,
    controlLevels: Map<ControlId, Float>,
    stickCalibrating: Boolean = false,
    calibPeakLeft: Float = 0f,
    calibPeakRight: Float = 0f,
    onStartStickCalibration: () -> Unit = {},
    onFinishStickCalibration: () -> Unit = {},
    onCancelStickCalibration: () -> Unit = {},
    onResetStickCalibration: () -> Unit = {},
    onConfigChanged: () -> Unit = {},
) {
    val destinations = listOf(
        "Play Triggers",
        "Moniteur OSC",
        "Moniteur NDI",
        "Mapping Triggers",
        "Projets",
        "Réglages",
    )
    var selected by remember { mutableStateOf(destinations.first()) }
    val safeSelected = if (selected in destinations) selected else destinations.first()
    // Mute hardware pad OSC while Mapping is open (QCM except Mapping).
    LaunchedEffect(safeSelected) {
        app.secondaryDestination = safeSelected
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showMappingNewPage by remember { mutableStateOf(false) }
    var mappingNewPageName by remember { mutableStateOf("") }
    var mappingNewPageType by remember { mutableStateOf(PageType.TACTILE) }
    val scheme = MaterialTheme.colorScheme
    var touchFired by remember { mutableStateOf("") }
    var playPage by remember { mutableIntStateOf(app.config.bottomPlayPage) }
    LaunchedEffect(app.config.bottomPlayPage) {
        playPage = app.config.bottomPlayPage
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = scheme.background,
        contentColor = scheme.onBackground,
    ) {
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
                    .statusBarsPadding(),
            ) {
                val mappingTactile = safeSelected == "Mapping Triggers" &&
                    app.config.pageType(app.config.activePage) == PageType.TACTILE
                val playTriggers = safeSelected == "Play Triggers"
                val compactTitle = mappingTactile || playTriggers
                val tactileIndices = remember(app.config.pageCount, app.config.pageTypes) {
                    app.config.tactilePageIndices()
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(scheme.surface)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Row(
                        Modifier
                            .align(Alignment.CenterStart)
                            // Keep title clear of the centered page switcher (narrow Presentation).
                            .fillMaxWidth(if (compactTitle) 0.34f else 1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "☰",
                            style = MaterialTheme.typography.titleLarge,
                            color = scheme.onSurface,
                            modifier = Modifier
                                .clickable { scope.launch { drawerState.open() } }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                        Text(
                            safeSelected,
                            style = if (compactTitle) {
                                MaterialTheme.typography.titleSmall
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (playTriggers) {
                        // Independent from primary Play / Mapping ([bottomPlayPage]).
                        FilteredPageSwitcher(
                            pageIndices = tactileIndices,
                            currentPage = playPage.let { p ->
                                if (p in tactileIndices) p
                                else tactileIndices.firstOrNull() ?: 0
                            },
                            pageLabel = { app.config.pageLabel(it) },
                            onPageChange = { newPage ->
                                playPage = newPage
                                app.config = app.config.copy(bottomPlayPage = newPage)
                                app.persist()
                                onConfigChanged()
                            },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else if (mappingTactile) {
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
                when (safeSelected) {
                    "Play Triggers" -> PlayTactileLayout(
                        app = app,
                        page = playPage,
                        lastFired = touchFired,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onPageChange = { newPage ->
                            playPage = newPage
                            app.config = app.config.copy(bottomPlayPage = newPage)
                            app.persist()
                            onConfigChanged()
                        },
                        onLastFired = { touchFired = it },
                        tactileOnly = true,
                        showMenuButton = false,
                        showPageSwitcher = false,
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

