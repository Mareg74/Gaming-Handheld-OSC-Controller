package com.mareg74.osccontroller.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mareg74.osccontroller.ThorOscApp
import com.mareg74.osccontroller.config.ArgKind
import com.mareg74.osccontroller.config.PageType
import com.mareg74.osccontroller.config.TactileGrid
import com.mareg74.osccontroller.config.TactileKind
import com.mareg74.osccontroller.config.TactileWidget
import com.mareg74.osccontroller.config.darkenArgb
import com.mareg74.osccontroller.config.mappingOn
import com.mareg74.osccontroller.config.parseFillArgb
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class ResizeHandle { N, S, E, W, NE, NW, SE, SW }

private fun Color.Companion.fromFillHex(hex: String): Color =
    Color(parseFillArgb(hex) ?: 0xFF3DDC84.toInt())

private fun idleFillColor(widget: TactileWidget): Color =
    Color.fromFillHex(widget.resolvedFillHex())

private fun pressedFillColor(widget: TactileWidget): Color =
    Color(darkenArgb(parseFillArgb(widget.resolvedFillHex()) ?: 0xFF3DDC84.toInt()))

private const val UnmappedHintHoldMs = 1400L

/**
 * Play surface for tactile pages (top or bottom).
 * [tactileOnly] restricts the page switcher to [PageType.TACTILE] pages.
 */
@Composable
fun PlayTactileLayout(
    app: ThorOscApp,
    page: Int,
    lastFired: String,
    onOpenDrawer: () -> Unit,
    onPageChange: (Int) -> Unit,
    onLastFired: (String) -> Unit,
    tactileOnly: Boolean,
    onAddPage: (() -> Unit)? = null,
    showMenuButton: Boolean = true,
    /** When false, host chrome (e.g. Presentation title bar) owns the page switcher. */
    showPageSwitcher: Boolean = true,
) {
    val tactileIndices = remember(app.config.pageCount, app.config.pageTypes) {
        app.config.tactilePageIndices()
    }
    val effectivePage = when {
        !tactileOnly -> page
        tactileIndices.isEmpty() -> -1
        page in tactileIndices -> page
        else -> tactileIndices.first()
    }

    LaunchedEffect(tactileOnly, effectivePage, page) {
        if (tactileOnly && effectivePage >= 0 && effectivePage != page) {
            onPageChange(effectivePage)
        }
    }

    val bg = MaterialTheme.colorScheme.background
    val panel = MaterialTheme.colorScheme.surfaceContainerLow
    var unmappedHint by remember { mutableStateOf("") }
    var unmappedToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(unmappedToken) {
        if (unmappedHint.isBlank()) return@LaunchedEffect
        delay(UnmappedHintHoldMs)
        unmappedHint = ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        // Same chrome as PlayGamepad: menu left, page switcher truly centered.
        // Presentation hosts the switcher in its title bar — skip duplicate chrome.
        if (showMenuButton || showPageSwitcher) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                if (showMenuButton) {
                    Text(
                        "☰",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clickable(onClick = onOpenDrawer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (showPageSwitcher) {
                    if (tactileOnly) {
                        FilteredPageSwitcher(
                            pageIndices = tactileIndices,
                            currentPage = effectivePage.coerceAtLeast(0),
                            pageLabel = { app.config.pageLabel(it) },
                            onPageChange = onPageChange,
                            onAddPage = onAddPage,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        PageSwitcher(
                            page = page,
                            pageLabel = app.config.pageLabel(page),
                            lastPageIndex = app.config.lastPageIndex(),
                            onPageChange = onPageChange,
                            onAddPage = onAddPage,
                            pageTitles = app.config.pageTitleList(),
                            pageIsTactile = app.config.pageTactileFlags(),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        // Shared across dual displays — local lastFired alone would desync Primary ↔ Presentation.
        val oscMonitor = app.playOscMonitor.value.ifBlank { lastFired }
        // Same feedback as PlayGamepad, but side-by-side so the canvas height stays stable.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (oscMonitor.isBlank()) "Prêt" else oscMonitor,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (unmappedHint.isNotBlank()) {
                Text(
                    "  ·  ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                )
                Text(
                    unmappedHint,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (tactileOnly && tactileIndices.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Aucune page tactile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Crée une page de type « Page tactile » depuis Mapping.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
                    )
                }
            }
        } else if (effectivePage >= 0 && app.config.pageType(effectivePage) == PageType.TACTILE) {
            TactilePlayCanvas(
                app = app,
                page = effectivePage,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(panel),
                onEmit = { id, value, continuous ->
                    app.emitControl(id, value, continuous, page = effectivePage)
                },
                onFired = { line ->
                    app.playOscMonitor.value = line
                    onLastFired(line)
                },
                onUnmapped = { label ->
                    unmappedHint = "non mappé: $label"
                    unmappedToken++
                },
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Page physique — utilise le pad ou ouvre une page tactile.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

@Composable
fun FilteredPageSwitcher(
    pageIndices: List<Int>,
    currentPage: Int,
    pageLabel: (Int) -> String,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onAddPage: (() -> Unit)? = null,
) {
    if (pageIndices.isEmpty()) {
        Text(
            "PLAY TACTILE",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.1.sp,
            modifier = modifier,
        )
        return
    }
    val pos = pageIndices.indexOf(currentPage).coerceAtLeast(0)
    val titles = pageIndices.map { pageLabel(it) }
    PageSwitcher(
        page = pos,
        pageLabel = pageLabel(pageIndices[pos]),
        lastPageIndex = pageIndices.lastIndex,
        onPageChange = { newPos ->
            pageIndices.getOrNull(newPos)?.let(onPageChange)
        },
        onAddPage = onAddPage,
        pageTitles = titles,
        pageIsTactile = List(titles.size) { true },
        modifier = modifier,
    )
}

@Composable
fun TactilePlayCanvas(
    app: ThorOscApp,
    page: Int,
    modifier: Modifier = Modifier,
    /** When non-null (Mapping editor), drives layout from parent State so moves refresh live. */
    widgets: List<TactileWidget>? = null,
    editable: Boolean = false,
    selectedId: String? = null,
    onSelect: (String?) -> Unit = {},
    /**
     * Geometry change. [commit] true on gesture end (persist / external notify);
     * false while dragging so parent can update local State without secondary churn.
     */
    onWidgetMoved: (widget: TactileWidget, commit: Boolean) -> Unit = { _, _ -> },
    onEmit: (controlId: String, value: Float, continuous: Boolean) -> String? = { _, _, _ -> null },
    onFired: (String) -> Unit = {},
    onUnmapped: (label: String) -> Unit = {},
) {
    // Shared revision — Play (and peer Mapping) must recompose when the other
    // display commits a move/resize; app.config alone is not observable State.
    val widgetsRev = app.widgetsRevision.intValue
    val baseWidgets = widgets ?: run {
        widgetsRev
        app.config.widgetsOn(page)
    }
    val gridColumns = TactileGrid.normalizeColumns(app.config.tactileGridColumns)
    // Live geometry while dragging/resizing — Compose State so the canvas moves
    // immediately without waiting for app.config / parent skip-invalidation.
    val liveGeom = remember { mutableStateMapOf<String, TactileWidget>() }
    LaunchedEffect(page) { liveGeom.clear() }
    LaunchedEffect(baseWidgets, widgetsRev) {
        // Drop overlays once parent/peers caught up (commit or remote edit).
        liveGeom.keys.toList().forEach { id ->
            val base = baseWidgets.find { it.id == id } ?: run {
                liveGeom.remove(id)
                return@forEach
            }
            if (liveGeom[id] == base) liveGeom.remove(id)
        }
    }
    val resolvedWidgets = baseWidgets.map { liveGeom[it.id] ?: it }
    // Shared with the other display — local remember would desync Primary ↔ Presentation.
    val faderLevels = app.tactileFaderLevels
    val scheme = MaterialTheme.colorScheme
    val faderTrack = scheme.surfaceVariant
    val faderWell = scheme.surfaceContainerHighest
    val ink = scheme.onSurface
    val editBorder = scheme.outline
    val editBorderSel = scheme.primary
    val gridLine = scheme.outline.copy(alpha = 0.35f)

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val aspectWH = if (heightPx > 0f) widthPx / heightPx else 16f / 9f
        val showGrid = editable && selectedId != null

        if (showGrid && widthPx > 0f && heightPx > 0f) {
            val cols = gridColumns
            val cellW = widthPx / cols
            val rows = maxOf(1, (heightPx / cellW).roundToInt())
            Canvas(Modifier.fillMaxSize().zIndex(0f)) {
                for (c in 1 until cols) {
                    val x = c * cellW
                    drawLine(gridLine, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                }
                for (r in 1 until rows) {
                    val y = r * cellW
                    if (y >= size.height) break
                    drawLine(gridLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
            }
        }

        resolvedWidgets.forEach { widget ->
            key(widget.id) {
                val selected = selectedId == widget.id
                val left = (widget.x * widthPx)
                val top = (widget.y * heightPx)
                val w = (widget.w * widthPx)
                val h = (widget.h * heightPx)
                val idle = idleFillColor(widget)
                val latestWidget by rememberUpdatedState(widget)
                Box(
                    Modifier
                        .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                        .size(
                            width = with(density) { w.toDp() },
                            height = with(density) { h.toDp() },
                        )
                        .zIndex(if (selected) 2f else 1f)
                        .then(
                            if (editable) {
                                Modifier
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) editBorderSel else editBorder,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .pointerInput(widget.id) {
                                        detectTapGestures(onTap = { onSelect(widget.id) })
                                    }
                                    .pointerInput(widget.id, widthPx, heightPx, aspectWH, gridColumns) {
                                        var live = latestWidget
                                        detectDragGestures(
                                            onDragStart = {
                                                live = latestWidget
                                                liveGeom[live.id] = live
                                            },
                                            onDragEnd = {
                                                val snapped = TactileGrid.snapWidget(
                                                    live, aspectWH, gridColumns,
                                                )
                                                onWidgetMoved(snapped, true)
                                                // Config is source of truth after commit —
                                                // drop overlay so peer screens can drive geom.
                                                liveGeom.remove(snapped.id)
                                            },
                                            onDragCancel = {
                                                liveGeom.remove(live.id)
                                            },
                                        ) { change, drag ->
                                            change.consume()
                                            onSelect(widget.id)
                                            live = live.copy(
                                                x = live.x + drag.x / widthPx,
                                                y = live.y + drag.y / heightPx,
                                            ).clamped(aspectWH, gridColumns)
                                            liveGeom[live.id] = live
                                        }
                                    }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    when (widget.kind) {
                        TactileKind.TRIGGER -> {
                            TactileTriggerPad(
                                app = app,
                                page = page,
                                label = widget.label.ifBlank { "TRIG" },
                                editable = editable,
                                widgetId = widget.id,
                                idleColor = idle,
                                pressedColor = pressedFillColor(widget),
                                onEmit = onEmit,
                                onFired = onFired,
                                onUnmapped = onUnmapped,
                            )
                        }
                        TactileKind.FADER -> {
                            val level = faderLevels[widget.id] ?: 0f
                            val label = widget.label.ifBlank { "FADER" }
                            // Local to this canvas — opacity while dragging; level itself is shared.
                            var dragging by remember(widget.id) { mutableStateOf(false) }
                            val pct = (level * 100f).roundToInt().coerceIn(0, 100)
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(faderTrack)
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    label,
                                    color = ink,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                BoxWithConstraints(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(faderWell)
                                        .then(
                                            if (!editable) {
                                                Modifier.pointerInput(widget.id) {
                                                    // Relative drag: keep level at press, apply ΔY
                                                    // (finger up → level up). No jump to absolute Y.
                                                    var originLevel = 0f
                                                    var accumY = 0f
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            originLevel = faderLevels[widget.id] ?: 0f
                                                            accumY = 0f
                                                            dragging = true
                                                        },
                                                        onDragEnd = { dragging = false },
                                                        onDragCancel = { dragging = false },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            accumY += dragAmount.y
                                                            val next = (originLevel - accumY / size.height)
                                                                .coerceIn(0f, 1f)
                                                            faderLevels[widget.id] = next
                                                            val line = onEmit(widget.id, next, true)
                                                            if (line != null) onFired(line)
                                                        },
                                                    )
                                                }
                                            } else Modifier,
                                        ),
                                ) {
                                    val fillH = maxHeight * level
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(fillH)
                                            .background(idle.copy(alpha = 0.9f)),
                                    )
                                    Text(
                                        "$pct%",
                                        color = ink.copy(alpha = if (dragging) 0.95f else 0.48f),
                                        fontSize = 12.sp,
                                        fontWeight = if (dragging) FontWeight.SemiBold else FontWeight.Medium,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (editable && selected) {
                        ResizeHandle.entries.forEach { handle ->
                            ResizeHandleKnob(
                                handle = handle,
                                widget = widget,
                                widthPx = widthPx,
                                heightPx = heightPx,
                                aspectWH = aspectWH,
                                gridColumns = gridColumns,
                                liveGeom = liveGeom,
                                onWidgetChanged = onWidgetMoved,
                            )
                        }
                    }
                }
            }
        }

        if (resolvedWidgets.isEmpty()) {
            Text(
                if (editable) "Ajoute un trigger ou un fader" else "Page vide",
                color = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun BoxScope.ResizeHandleKnob(
    handle: ResizeHandle,
    widget: TactileWidget,
    widthPx: Float,
    heightPx: Float,
    aspectWH: Float,
    gridColumns: Int,
    liveGeom: SnapshotStateMap<String, TactileWidget>,
    onWidgetChanged: (widget: TactileWidget, commit: Boolean) -> Unit,
) {
    val latestWidget by rememberUpdatedState(widget)
    val alignment = when (handle) {
        ResizeHandle.NW -> Alignment.TopStart
        ResizeHandle.N -> Alignment.TopCenter
        ResizeHandle.NE -> Alignment.TopEnd
        ResizeHandle.W -> Alignment.CenterStart
        ResizeHandle.E -> Alignment.CenterEnd
        ResizeHandle.SW -> Alignment.BottomStart
        ResizeHandle.S -> Alignment.BottomCenter
        ResizeHandle.SE -> Alignment.BottomEnd
    }
    Box(
        Modifier
            .align(alignment)
            .offset(
                x = when (handle) {
                    ResizeHandle.NW, ResizeHandle.W, ResizeHandle.SW -> (-6).dp
                    ResizeHandle.NE, ResizeHandle.E, ResizeHandle.SE -> 6.dp
                    else -> 0.dp
                },
                y = when (handle) {
                    ResizeHandle.NW, ResizeHandle.N, ResizeHandle.NE -> (-6).dp
                    ResizeHandle.SW, ResizeHandle.S, ResizeHandle.SE -> 6.dp
                    else -> 0.dp
                },
            )
            .size(14.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(1.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
            .pointerInput(handle, widget.id, widthPx, heightPx, aspectWH, gridColumns) {
                var live = latestWidget
                detectDragGestures(
                    onDragStart = {
                        live = latestWidget
                        liveGeom[live.id] = live
                    },
                    onDragEnd = {
                        val snapped = TactileGrid.snapWidget(live, aspectWH, gridColumns)
                        onWidgetChanged(snapped, true)
                        liveGeom.remove(snapped.id)
                    },
                    onDragCancel = { liveGeom.remove(live.id) },
                ) { change, drag ->
                    change.consume()
                    live = applyResizeDrag(
                        live, handle, drag.x, drag.y, widthPx, heightPx, aspectWH, gridColumns,
                    )
                    liveGeom[live.id] = live
                }
            },
    )
}

private fun applyResizeDrag(
    widget: TactileWidget,
    handle: ResizeHandle,
    dxPx: Float,
    dyPx: Float,
    widthPx: Float,
    heightPx: Float,
    aspectWH: Float,
    gridColumns: Int,
): TactileWidget {
    val dx = dxPx / widthPx
    val dy = dyPx / heightPx
    var x = widget.x
    var y = widget.y
    var w = widget.w
    var h = widget.h
    when (handle) {
        ResizeHandle.E -> w += dx
        ResizeHandle.W -> { x += dx; w -= dx }
        ResizeHandle.S -> h += dy
        ResizeHandle.N -> { y += dy; h -= dy }
        ResizeHandle.SE -> { w += dx; h += dy }
        ResizeHandle.SW -> { x += dx; w -= dx; h += dy }
        ResizeHandle.NE -> { w += dx; y += dy; h -= dy }
        ResizeHandle.NW -> { x += dx; w -= dx; y += dy; h -= dy }
    }
    return widget.copy(x = x, y = y, w = w, h = h).clamped(aspectWH, gridColumns)
}

/** Trigger pad: Toggle stays lit from logical latch; Momentary flashes on press. */
@Composable
private fun TactileTriggerPad(
    app: ThorOscApp,
    page: Int,
    label: String,
    editable: Boolean,
    widgetId: String,
    idleColor: Color,
    pressedColor: Color,
    onEmit: (controlId: String, value: Float, continuous: Boolean) -> String?,
    onFired: (String) -> Unit,
    onUnmapped: (label: String) -> Unit,
) {
    var pressed by remember(widgetId) { mutableStateOf(false) }
    // Shared Toggle lit (widget id) — Primary ↔ Presentation, any page showing it.
    val sharedToggleLit = app.tactileToggleLit[widgetId]
    val mapping = app.config.mappingOn(page, widgetId)
    val lit = when (mapping?.argKind) {
        ArgKind.TOGGLE -> sharedToggleLit ?: mapping.toggleLogicalOn()
        else -> pressed
    }
    val fill by animateColorAsState(
        targetValue = if (lit) pressedColor else idleColor,
        animationSpec = tween(durationMillis = if (lit) 40 else 120),
        label = "trigger-fill-$widgetId",
    )
    val idleLabel = if (idleColor.luminance() > 0.55f) Color(0xFF0A1F14) else Color.White
    val pressedLabel = if (pressedColor.luminance() > 0.55f) Color(0xFF0A1F14) else Color.White
    val labelColor by animateColorAsState(
        targetValue = if (lit) pressedLabel else idleLabel,
        animationSpec = tween(durationMillis = if (lit) 40 else 120),
        label = "trigger-label-$widgetId",
    )
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .then(
                if (!editable) {
                    Modifier.pointerInput(widgetId) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                try {
                                    val line = onEmit(widgetId, 1f, false)
                                    if (line != null) {
                                        onFired(line)
                                    } else {
                                        onUnmapped(label)
                                    }
                                    tryAwaitRelease()
                                } finally {
                                    // Momentary release → INT 0; Toggle ignores release.
                                    val releaseLine = onEmit(widgetId, 0f, false)
                                    if (releaseLine != null) onFired(releaseLine)
                                    pressed = false
                                }
                            },
                        )
                    }
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = labelColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(6.dp),
        )
    }
}

/** Dialog: page title + PHYSICAL / TACTILE type (Presentation-safe overlay). */
@Composable
fun NewPageOverlay(
    name: String,
    onNameChange: (String) -> Unit,
    pageType: PageType,
    onPageTypeChange: (PageType) -> Unit,
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
                    label = { Text("Titre de la page") },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Type de page",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = pageType == PageType.PHYSICAL,
                        onClick = { onPageTypeChange(PageType.PHYSICAL) },
                        label = { Text("Contrôleur physique") },
                    )
                    FilterChip(
                        selected = pageType == PageType.TACTILE,
                        onClick = { onPageTypeChange(PageType.TACTILE) },
                        label = { Text("Page tactile") },
                    )
                }
                Text(
                    if (pageType == PageType.TACTILE) {
                        "Triggers et faders librement placés (sans pad)."
                    } else {
                        "Mapping des boutons / sticks du Thor."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
