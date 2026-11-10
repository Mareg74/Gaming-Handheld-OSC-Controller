package com.mareg74.osccontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class LearnCopyMode { MENU, COPY, LEARN }

/**
 * Mapping Learn/Copy card (Presentation-safe — host inside [InTreeModal] /
 * [PresentationSafePopup]).
 *
 * Copy: snapshot of last 40 monitor lines at open, toggle IN/OUT, apply address only.
 * Learn: wait for next OSC IN.
 */
@Composable
fun OscLearnCopyCard(
    log: MonitorLog,
    onPickAddress: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(LearnCopyMode.MENU) }
    val snapshot = remember { log.snapshotTraffic(40) }
    var showIn by remember { mutableStateOf(true) }
    var showOut by remember { mutableStateOf(true) }
    var learnAfterId by remember { mutableLongStateOf(0L) }
    var learnStatus by remember { mutableStateOf("En attente d’un OSC IN…") }

    LaunchedEffect(mode, learnAfterId) {
        if (mode != LearnCopyMode.LEARN) return@LaunchedEffect
        while (true) {
            val hit = log.entries().lastOrNull { e ->
                e.id > learnAfterId && e.dir == MonitorDir.IN && e.address.isNotEmpty()
            }
            if (hit != null) {
                onPickAddress(hit.address)
                return@LaunchedEffect
            }
            delay(120)
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth(0.92f)
            .heightIn(max = 520.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (mode) {
                        LearnCopyMode.MENU -> "Adresse OSC"
                        LearnCopyMode.COPY -> "Copy from monitor"
                        LearnCopyMode.LEARN -> "Learn"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }

            when (mode) {
                LearnCopyMode.MENU -> {
                    Text(
                        "Remplir le champ adresse depuis le moniteur ou en écoutant Resolume.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { mode = LearnCopyMode.COPY },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Copy from monitor")
                    }
                    TextButton(
                        onClick = {
                            learnAfterId = log.lastEntryId()
                            learnStatus = "En attente d’un OSC IN…"
                            mode = LearnCopyMode.LEARN
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Learn (prochain IN)")
                    }
                }

                LearnCopyMode.COPY -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = showIn,
                            onClick = { showIn = !showIn },
                            label = { Text("IN") },
                        )
                        FilterChip(
                            selected = showOut,
                            onClick = { showOut = !showOut },
                            label = { Text("OUT") },
                        )
                        TextButton(onClick = { mode = LearnCopyMode.MENU }) {
                            Text("Retour")
                        }
                    }
                    val visible = snapshot.filter {
                        (it.dir == MonitorDir.IN && showIn) || (it.dir == MonitorDir.OUT && showOut)
                    }
                    if (visible.isEmpty()) {
                        Text(
                            "Aucune ligne dans le snapshot (envoie/reçois de l’OSC puis rouvre).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(visible, key = { it.id }) { e ->
                                LearnCopyLine(e) { onPickAddress(e.address) }
                            }
                        }
                    }
                }

                LearnCopyMode.LEARN -> {
                    Text(
                        learnStatus,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Bouge un paramètre dans Resolume (OSC out → Thor).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { mode = LearnCopyMode.MENU }) {
                        Text("Annuler")
                    }
                }
            }
        }
    }
}

@Composable
private fun LearnCopyLine(e: MonitorEntry, onClick: () -> Unit) {
    val tagColor = when (e.dir) {
        MonitorDir.IN -> Color(0xFF2E7D32)
        MonitorDir.OUT -> Color(0xFF1565C0)
        MonitorDir.ERR -> Color(0xFFC62828)
    }
    val tag = when (e.dir) {
        MonitorDir.IN -> "IN"
        MonitorDir.OUT -> "OUT"
        MonitorDir.ERR -> "ERR"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(tagColor.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tag,
            color = tagColor,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "${e.address} ${e.argsText}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
