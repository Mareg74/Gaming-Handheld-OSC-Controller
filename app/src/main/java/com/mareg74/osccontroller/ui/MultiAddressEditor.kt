package com.mareg74.osccontroller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mareg74.osccontroller.config.ControlMapping
import com.mareg74.osccontroller.config.MAX_MAPPING_ADDRESSES

/** Normalize mapping addresses into 1…[MAX_MAPPING_ADDRESSES] editable lines. */
fun ControlMapping?.toEditableAddressLines(): List<String> {
    if (this == null) return listOf("")
    val lines = (listOf(address) + extraAddresses).map { it.trim() }.filter { it.isNotEmpty() }
    return if (lines.isEmpty()) listOf("") else lines.take(MAX_MAPPING_ADDRESSES)
}

fun List<String>.toSavedAddresses(): Pair<String, List<String>>? {
    val cleaned = map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_MAPPING_ADDRESSES)
    if (cleaned.isEmpty()) return null
    return cleaned.first() to cleaned.drop(1)
}

/**
 * Multi OSC address rows (max 4) with Learn/Copy, add, and delete (caller confirms delete).
 */
@Composable
fun MultiAddressEditor(
    lines: List<String>,
    onLinesChange: (List<String>) -> Unit,
    onOpenLearnCopy: (index: Int) -> Unit,
    onRequestDelete: (index: Int) -> Unit,
) {
    val canAdd = lines.size < MAX_MAPPING_ADDRESSES
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEachIndexed { index, value ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { next ->
                        onLinesChange(lines.toMutableList().also { it[index] = next })
                    },
                    label = {
                        Text(if (index == 0) "Adresse OSC" else "Adresse ${index + 1}")
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { onOpenLearnCopy(index) }) { Text("⌕") }
                if (lines.size > 1) {
                    TextButton(onClick = { onRequestDelete(index) }) {
                        Text("×", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    if (canAdd) onLinesChange(lines + "")
                },
                enabled = canAdd,
            ) {
                Text("+ commande")
            }
            Text(
                "${lines.count { it.isNotBlank() }}/$MAX_MAPPING_ADDRESSES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
