package com.mareg74.osccontroller.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import com.mareg74.osccontroller.ThorOscApp
import com.mareg74.osccontroller.config.ConfigCodec
import kotlinx.coroutines.delay

private val ColorIn = Color(0xFF2E7D32)
private val ColorOut = Color(0xFF1565C0)
private val ColorErr = Color(0xFFC62828)

/**
 * Shared OSC Monitor UI (primary + secondary).
 * Compact toolbar + collapsible filters; log list is primary; newest at bottom.
 */
@Composable
fun MonitorPanel(
    app: ThorOscApp,
    onConfigChanged: () -> Unit = {},
) {
    var entries by remember { mutableStateOf(app.log.entries()) }
    var dropCount by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scheme = MaterialTheme.colorScheme

    var showIn by remember { mutableStateOf(app.config.monitorShowIn) }
    var showOut by remember { mutableStateOf(app.config.monitorShowOut) }
    var mappedOnly by remember { mutableStateOf(app.config.monitorMappedOnly) }
    var contains by remember { mutableStateOf(app.config.monitorFilterContains) }
    var selected by remember { mutableStateOf(app.config.monitorFilterAddresses) }
    var lineSize by remember {
        mutableIntStateOf(
            app.config.monitorLineSize.coerceIn(
                ConfigCodec.MONITOR_LINE_SIZE_MIN,
                ConfigCodec.MONITOR_LINE_SIZE_MAX,
            ),
        )
    }

    LaunchedEffect(
        app.config.monitorShowIn,
        app.config.monitorShowOut,
        app.config.monitorMappedOnly,
        app.config.monitorFilterContains,
        app.config.monitorFilterAddresses,
        app.config.monitorLineSize,
    ) {
        showIn = app.config.monitorShowIn
        showOut = app.config.monitorShowOut
        mappedOnly = app.config.monitorMappedOnly
        contains = app.config.monitorFilterContains
        selected = app.config.monitorFilterAddresses
        lineSize = app.config.monitorLineSize.coerceIn(
            ConfigCodec.MONITOR_LINE_SIZE_MIN,
            ConfigCodec.MONITOR_LINE_SIZE_MAX,
        )
    }

    fun persistPrefs(
        inV: Boolean = showIn,
        outV: Boolean = showOut,
        mapped: Boolean = mappedOnly,
        q: String = contains,
        addrs: Set<String> = selected,
        size: Int = lineSize,
    ) {
        val clamped = size.coerceIn(
            ConfigCodec.MONITOR_LINE_SIZE_MIN,
            ConfigCodec.MONITOR_LINE_SIZE_MAX,
        )
        app.config = app.config.copy(
            monitorShowIn = inV,
            monitorShowOut = outV,
            monitorMappedOnly = mapped,
            monitorFilterContains = q,
            monitorFilterAddresses = addrs,
            monitorLineSize = clamped,
        )
        app.persist()
        onConfigChanged()
    }

    fun resetFilters() {
        selected = emptySet()
        contains = ""
        mappedOnly = false
        showIn = true
        showOut = true
        persistPrefs(inV = true, outV = true, mapped = false, q = "", addrs = emptySet())
        filtersOpen = false
    }

    LaunchedEffect(paused) {
        while (true) {
            if (!paused) {
                entries = app.log.entries()
                dropCount = app.feedback.dropCount
            }
            delay(250)
        }
    }

    val mappedAddresses = remember(app.config.mappings) { app.config.mappedOscAddresses() }
    val proposalAddresses = remember(mappedAddresses) {
        (mappedAddresses + listOf(
            "/thor/BTN_A", "/thor/BTN_B", "/thor/AXIS_LX", "/thor/AXIS_LY",
            "/thor/AXIS_RX", "/thor/AXIS_RY", "/thor/AXIS_L2", "/thor/AXIS_R2",
        )).distinct().sorted()
    }

    // Chronological: oldest top, newest bottom.
    val visible = remember(entries, showIn, showOut, mappedOnly, selected, contains, mappedAddresses) {
        entries.filter {
            it.matches(showIn, showOut, mappedOnly, mappedAddresses, selected, contains)
        }
    }

    val filtersHiding = entries.isNotEmpty() && visible.isEmpty()
    var didAutoUnstick by remember { mutableStateOf(false) }

    LaunchedEffect(filtersHiding, selected) {
        if (!filtersHiding) return@LaunchedEffect
        filtersOpen = true
        // One-shot: stuck address chip(s) with traffic but 0 visible → clear chips.
        if (!didAutoUnstick && selected.isNotEmpty()) {
            didAutoUnstick = true
            selected = emptySet()
            persistPrefs(addrs = emptySet())
        }
    }

    // Finger down on the log: freeze auto-scroll so long-press copy is not cancelled.
    var pointerDown by remember { mutableStateOf(false) }

    LaunchedEffect(visible.size, paused, visible.lastOrNull()?.id, pointerDown) {
        if (!paused && !pointerDown && visible.isNotEmpty()) {
            val idx = visible.lastIndex
            runCatching { listState.scrollToItem(idx) }
        }
    }

    val activeFilterCount =
        (if (mappedOnly) 1 else 0) +
            selected.size +
            (if (contains.isNotBlank()) 1 else 0) +
            (if (!showIn) 1 else 0) +
            (if (!showOut) 1 else 0)

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${visible.size}/${entries.size}·$dropCount",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            CompactCheck(
                checked = showIn,
                label = "IN",
                color = ColorIn,
                onCheckedChange = {
                    showIn = it
                    persistPrefs(inV = it)
                },
            )
            CompactCheck(
                checked = showOut,
                label = "OUT",
                color = ColorOut,
                onCheckedChange = {
                    showOut = it
                    persistPrefs(outV = it)
                },
            )
            TextButton(
                onClick = { filtersOpen = !filtersOpen },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text(
                    if (filtersOpen) "Filtres ▴"
                    else if (activeFilterCount > 0) "Filtres($activeFilterCount) ▾"
                    else "Filtres ▾",
                    fontSize = 11.sp,
                )
            }
            TextButton(
                onClick = { paused = !paused },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text(if (paused) "▶" else "⏸", fontSize = 12.sp)
            }
            TextButton(
                onClick = {
                    app.log.clear()
                    entries = emptyList()
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text("Effacer", fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    val next = (lineSize - 1).coerceAtLeast(ConfigCodec.MONITOR_LINE_SIZE_MIN)
                    if (next == lineSize) return@TextButton
                    lineSize = next
                    persistPrefs(size = next)
                },
                enabled = lineSize > ConfigCodec.MONITOR_LINE_SIZE_MIN,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(
                onClick = {
                    val next = (lineSize + 1).coerceAtMost(ConfigCodec.MONITOR_LINE_SIZE_MAX)
                    if (next == lineSize) return@TextButton
                    lineSize = next
                    persistPrefs(size = next)
                },
                enabled = lineSize < ConfigCodec.MONITOR_LINE_SIZE_MAX,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        app.log.listenError?.let {
            Text(it, color = scheme.error, style = MaterialTheme.typography.labelSmall)
        }

        if (filtersOpen) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = contains,
                    onValueChange = {
                        contains = it
                        persistPrefs(q = it)
                    },
                    label = { Text("Contient", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 52.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = mappedOnly,
                        onClick = {
                            mappedOnly = !mappedOnly
                            persistPrefs(mapped = mappedOnly)
                        },
                        label = { Text("Mappées", fontSize = 11.sp) },
                    )
                    proposalAddresses.take(20).forEach { addr ->
                        val on = addr in selected
                        FilterChip(
                            selected = on,
                            onClick = {
                                selected = if (on) selected - addr else selected + addr
                                persistPrefs(addrs = selected)
                            },
                            label = {
                                Text(
                                    addr.substringAfterLast('/').ifBlank { addr },
                                    fontSize = 11.sp,
                                )
                            },
                        )
                    }
                    TextButton(
                        onClick = { resetFilters() },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text("Reset", fontSize = 11.sp)
                    }
                }
            }
        }

        if (filtersHiding) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(scheme.errorContainer, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${entries.size} msg masqués par les filtres",
                    color = scheme.onErrorContainer,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { resetFilters() }) {
                    Text("Reset filtres", color = scheme.onErrorContainer)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (visible.isEmpty() && !filtersHiding) {
                Text(
                    if (paused) "Pause — aucun message"
                    else "En attente OSC…",
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            pointerDown = true
                            waitForUpOrCancellation()
                            pointerDown = false
                        }
                    },
            ) {
                items(visible, key = { it.id }) { e ->
                    MonitorLine(e, lineSize = lineSize)
                }
            }
        }
    }
}

@Composable
private fun CompactCheck(
    checked: Boolean,
    label: String,
    color: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 2.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(28.dp),
        )
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
private fun MonitorLine(e: MonitorEntry, lineSize: Int) {
    val context = LocalContext.current
    val appCtx = remember(context) { context.applicationContext }
    val tagColor = when (e.dir) {
        MonitorDir.IN -> ColorIn
        MonitorDir.OUT -> ColorOut
        MonitorDir.ERR -> ColorErr
    }
    val tag = when (e.dir) {
        MonitorDir.IN -> "IN"
        MonitorDir.OUT -> "OUT"
        MonitorDir.ERR -> "ERR"
    }
    val copyable = OscAddressClipboard.canCopy(e.dir, e.address)
    val step = lineSize.coerceIn(ConfigCodec.MONITOR_LINE_SIZE_MIN, ConfigCodec.MONITOR_LINE_SIZE_MAX)
    val metaSp = (8 + step * 2).sp
    val bodySp = (9 + step * 2).sp
    val padV = (2 + step).dp
    val minH = (28 + step * 6).dp
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minH)
            .background(tagColor.copy(alpha = 0.07f), RoundedCornerShape(4.dp))
            .then(
                if (copyable) {
                    Modifier.pointerInput(e.id, e.address, e.dir) {
                        detectTapGestures(
                            onLongPress = {
                                when (OscAddressClipboard.copyAddress(appCtx, e.dir, e.address)) {
                                    OscAddressClipboard.Result.COPIED ->
                                        Toast.makeText(
                                            appCtx,
                                            "Adresse copiée",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    OscAddressClipboard.Result.DEDUPED ->
                                        Toast.makeText(
                                            appCtx,
                                            "Déjà dans le presse-papiers",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    OscAddressClipboard.Result.SKIPPED -> Unit
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 6.dp, vertical = padV),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            e.timeLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = metaSp,
        )
        Text(
            tag,
            style = MaterialTheme.typography.labelSmall,
            color = tagColor,
            fontWeight = FontWeight.Bold,
            fontSize = metaSp,
        )
        Text(
            buildString {
                if (e.address.isNotEmpty()) append(e.address).append(' ')
                append(e.argsText)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = bodySp,
            modifier = Modifier.weight(1f),
        )
    }
}
