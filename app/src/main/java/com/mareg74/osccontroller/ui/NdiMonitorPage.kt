package com.mareg74.osccontroller.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mareg74.osccontroller.ThorOscApp
import com.mareg74.osccontroller.ndi.NdiFrame
import com.mareg74.osccontroller.ndi.NdiSettings
import com.mareg74.osccontroller.ndi.NdiSource
import com.mareg74.osccontroller.ndi.NdiStatus

@Composable
fun NdiMonitorPage(app: ThorOscApp) {
    val session = app.ndiSession
    var tick by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }

    DisposableEffect(session) {
        val listener = { tick += 1 }
        session.acquireViewer()
        session.addListener(listener)
        session.connectLastOrIdle()
        onDispose {
            session.removeListener(listener)
            session.releaseViewer()
        }
    }

    val status = session.status
    val statusLabel = ndiStatusLabel(status, session.errorMessage)
    val sourceName = session.currentSource?.name ?: "—"

    val frame = session.lastFrame
    val imageBitmap = remember(tick) {
        session.lastFrame?.let { f ->
            if (f.width > 0 && f.height > 0) ndiFrameToImageBitmap(f) else null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101010)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (imageBitmap != null && frame != null && frame.width > 0 && frame.height > 0) {
                val aspect = frame.width.toFloat() / frame.height.toFloat()
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(
                            if (maxWidth / maxHeight > aspect) {
                                Modifier.fillMaxHeight().aspectRatio(aspect)
                            } else {
                                Modifier.fillMaxWidth().aspectRatio(aspect)
                            },
                        ),
                )
            } else {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                    )
                    if (status == NdiStatus.IDLE) {
                        Text(
                            "Choisissez une source via ⚙",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.55f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                sourceName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
            )
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val onPauseLabel = when (status) {
                NdiStatus.LIVE -> "Pause"
                NdiStatus.PAUSED -> "On"
                else -> "On"
            }
            Button(
                onClick = {
                    when (status) {
                        NdiStatus.LIVE -> session.pause()
                        NdiStatus.PAUSED -> session.resume()
                        NdiStatus.ERROR -> session.retry()
                        else -> session.connectLastOrIdle()
                    }
                },
            ) {
                Text(onPauseLabel)
            }
            OutlinedButton(onClick = { showSettings = true }) {
                Text("⚙")
            }
        }

        // In-tree overlays: AlertDialog/Window crashes inside secondary Presentation.
        if (showSettings) {
            NdiSettingsOverlay(
                initial = session.settings,
                onDismiss = { showSettings = false },
                onPickSource = {
                    showSettings = false
                    showSourcePicker = true
                    session.refreshSources()
                },
                onApply = { settings ->
                    session.applySettings(settings)
                    showSettings = false
                },
            )
        }

        if (showSourcePicker) {
            NdiSourcePickerOverlay(
                sources = session.sources,
                onDismiss = { showSourcePicker = false },
                onRefresh = { session.refreshSources() },
                onSelect = { source ->
                    session.connect(source)
                    showSourcePicker = false
                },
            )
        }
    }
}

/** Modal without a new Window — safe on secondary Presentation. */
@Composable
private fun NdiSettingsOverlay(
    initial: NdiSettings,
    onDismiss: () -> Unit,
    onPickSource: () -> Unit,
    onApply: (NdiSettings) -> Unit,
) {
    var quality by remember(initial) { mutableIntStateOf(initial.quality) }
    var maxWidth by remember(initial) { mutableStateOf(initial.maxWidth.toString()) }
    var maxHeight by remember(initial) { mutableStateOf(initial.maxHeight.toString()) }
    var maxFps by remember(initial) { mutableStateOf(initial.maxFps.toString()) }
    var audioEnabled by remember(initial) { mutableStateOf(initial.audioEnabled) }

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
                .fillMaxWidth()
                .heightIn(max = 520.dp),
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Réglages NDI",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val qualityLabel = if (NdiSettings.useHighestBandwidth(quality)) {
                        "Haute — plein débit NDI"
                    } else {
                        "Basse — économie Wi‑Fi"
                    }
                    Text("Qualité réseau ($quality) · $qualityLabel", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = { quality = it.toInt().coerceIn(0, 100) },
                        valueRange = 0f..100f,
                    )
                    OutlinedTextField(
                        value = maxWidth,
                        onValueChange = { maxWidth = it.filter { c -> c.isDigit() } },
                        label = { Text("Largeur max (0 = native)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = maxHeight,
                        onValueChange = { maxHeight = it.filter { c -> c.isDigit() } },
                        label = { Text("Hauteur max (0 = native)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = maxFps,
                        onValueChange = { maxFps = it.filter { c -> c.isDigit() } },
                        label = { Text("FPS affichage (0 = illimité)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Audio")
                        Switch(checked = audioEnabled, onCheckedChange = { audioEnabled = it })
                    }
                    OutlinedButton(onClick = onPickSource, modifier = Modifier.fillMaxWidth()) {
                        Text("Source…")
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                    Button(
                        onClick = {
                            val w = NdiSettings.clampSize(maxWidth.toIntOrNull() ?: initial.maxWidth)
                            val h = NdiSettings.clampSize(maxHeight.toIntOrNull() ?: initial.maxHeight)
                            val fps = NdiSettings.clampFps(maxFps.toIntOrNull() ?: initial.maxFps)
                            onApply(
                                NdiSettings(
                                    quality = NdiSettings.clampQuality(quality),
                                    maxWidth = w,
                                    maxHeight = h,
                                    maxFps = fps,
                                    audioEnabled = audioEnabled,
                                ),
                            )
                        },
                    ) {
                        Text("Appliquer")
                    }
                }
            }
        }
    }
}

/** Modal without a new Window — safe on secondary Presentation. */
@Composable
private fun NdiSourcePickerOverlay(
    sources: List<NdiSource>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (NdiSource) -> Unit,
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
                .fillMaxWidth()
                .heightIn(max = 420.dp),
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Sources NDI",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (sources.isEmpty()) {
                    Text("Aucune source NDI")
                } else {
                    LazyColumn(
                        Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 280.dp),
                    ) {
                        items(sources, key = { it.url.ifBlank { it.name } }) { source ->
                            Text(
                                source.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(source) }
                                    .padding(vertical = 10.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                    Button(onClick = onRefresh) { Text("Actualiser") }
                }
            }
        }
    }
}

private fun ndiStatusLabel(status: NdiStatus, errorMessage: String?): String = when (status) {
    NdiStatus.IDLE -> "Inactif"
    NdiStatus.DISCOVERING -> "Recherche…"
    NdiStatus.CONNECTING -> "Connexion…"
    NdiStatus.LIVE -> "Live"
    NdiStatus.PAUSED -> "Pause"
    NdiStatus.ERROR -> "Erreur: ${errorMessage ?: "inconnue"}"
}

private fun ndiFrameToImageBitmap(frame: NdiFrame): ImageBitmap {
    val pixels = frame.argb.copyOf()
    val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
    return bitmap.asImageBitmap()
}
