package com.mareg74.osccontroller.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mareg74.osccontroller.config.AppConfig
import com.mareg74.osccontroller.config.PageType

private val PageChipBgDark = Color(0xFF1A222C)
private val PageChipInkDark = Color(0xFFE8EEF5)
/** Accent for tactile / triggers pages in the picker list. */
private val TactilePageAccentDark = Color(0xFF2EC4B6)
private val TactilePageFillDark = Color(0xFF1A3A38)

@Composable
fun PageSwitcher(
    page: Int,
    pageLabel: String,
    lastPageIndex: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onAddPage: (() -> Unit)? = null,
    /** Labels for indices 0..lastPageIndex. Defaults to « Page N ». */
    pageTitles: List<String>? = null,
    /** True = page tactile (triggers/faders) — tinted in the picker. */
    pageIsTactile: List<Boolean>? = null,
) {
    val maxPage = lastPageIndex.coerceAtLeast(0)
    var showPicker by remember { mutableStateOf(false) }
    val titles = remember(pageTitles, maxPage) {
        pageTitles?.takeIf { it.size >= maxPage + 1 }
            ?: (0..maxPage).map { "Page $it" }
    }
    val tactile = remember(pageIsTactile, maxPage) {
        pageIsTactile?.takeIf { it.size >= maxPage + 1 }
            ?: List(maxPage + 1) { false }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PageNavChip(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Page précédente",
            enabled = page > 0,
        ) { onPageChange(page - 1) }
        Text(
            pageLabel.uppercase(),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showPicker = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        PageNavChip(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Page suivante",
            enabled = page < maxPage,
        ) { onPageChange(page + 1) }
        if (onAddPage != null) {
            PageNavChip(
                icon = Icons.Filled.Add,
                contentDescription = "Nouvelle page",
                enabled = true,
            ) { onAddPage() }
        }
    }

    // Never Dialog/AlertDialog here — crashes inside secondary Presentation.
    if (showPicker) {
        PresentationSafePopup(onDismiss = { showPicker = false }) {
            Column(
                Modifier
                    .padding(20.dp)
                    .heightIn(max = 480.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Choisir une page",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    titles.forEachIndexed { index, title ->
                        val active = index == page
                        val isTactile = tactile.getOrElse(index) { false }
                        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        val tactileAccent = if (dark) {
                            TactilePageAccentDark
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                        val tactileFill = if (dark) {
                            TactilePageFillDark
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                        val label = buildString {
                            append(title)
                            if (isTactile) append(" · tactile")
                            if (active) append(" · active")
                        }
                        OutlinedButton(
                            onClick = {
                                onPageChange(index)
                                showPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (isTactile) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = tactileFill,
                                    contentColor = tactileAccent,
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            border = if (isTactile) {
                                BorderStroke(1.dp, tactileAccent)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            },
                        ) {
                            Text(
                                label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isTactile) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { showPicker = false },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Annuler")
                }
            }
        }
    }
}

@Composable
private fun PageNavChip(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val chipBg = if (dark) {
        PageChipBgDark
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val chipInk = if (dark) {
        PageChipInkDark
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val chipBorder = if (dark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) chipBg else chipBg.copy(alpha = 0.45f))
            .border(1.dp, chipBorder, RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) chipInk else chipInk.copy(alpha = 0.45f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Highest page index shown in the rename editor (0..count inclusive range size). */
fun AppConfig.editablePageCount(): Int = lastPageIndex()

/** Labels 0..lastPageIndex for [PageSwitcher]. */
fun AppConfig.pageTitleList(): List<String> =
    (0..lastPageIndex()).map { pageLabel(it) }

/** Tactile flags 0..lastPageIndex for [PageSwitcher] picker tint. */
fun AppConfig.pageTactileFlags(): List<Boolean> =
    (0..lastPageIndex()).map { pageType(it) == PageType.TACTILE }
