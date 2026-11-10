package com.mareg74.osccontroller.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mareg74.osccontroller.ThorOscApp
import com.mareg74.osccontroller.config.playDigitalLit
import com.mareg74.osccontroller.mapping.ControlId
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

@Composable private fun playPanel(): Color = MaterialTheme.colorScheme.surfaceContainerHighest
@Composable private fun playMuted(): Color = MaterialTheme.colorScheme.onSurfaceVariant
@Composable private fun playInk(): Color = MaterialTheme.colorScheme.onSurface
private val ColorA = Color(0xFF3DDC84)
private val ColorB = Color(0xFFE85D5D)
private val ColorX = Color(0xFF4BA3F0)
private val ColorY = Color(0xFFF0C14A)
private val ColorShoulder = Color(0xFF2EC4B6)
private val ColorDpad = Color(0xFF7AD7F0)
private val ColorStickL = Color(0xFFF4A261)
private val ColorStickR = Color(0xFFE76F51)
private val ColorMeta = Color(0xFFB8C0CC)

@Composable
fun PlayGamepadLayout(
    app: ThorOscApp,
    levels: Map<ControlId, Float>,
    lastFired: String,
    unmappedHint: String,
    page: Int,
    onOpenDrawer: () -> Unit,
    onPageChange: (Int) -> Unit,
) {
    val bg = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(bg, surface, bg)
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // Keep legends (esp. RS) above the gesture bar without a black strip:
                // background already fills edge-to-edge in the parent Box.
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            PlayHeader(
                page = page,
                pageLabel = app.config.pageLabel(page),
                lastPageIndex = app.config.lastPageIndex(),
                pageTitles = app.config.pageTitleList(),
                pageIsTactile = app.config.pageTactileFlags(),
                onOpenDrawer = onOpenDrawer,
                onPageChange = onPageChange,
            )
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Width only picks chrome density; pad columns size from available height.
                val compact = maxWidth < 600.dp
                key(page) {
                    PlayPadBody(app, levels, page, compact = compact)
                }
                // Transparent overlay, ultra-compact — clusters may draw under it.
                PlayStatusFooter(
                    lastFired = lastFired,
                    unmappedHint = unmappedHint,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 1.dp),
                )
            }
        }
    }
}

/**
 * Compact Play chrome: ☰ left, page switcher truly centered.
 * Pressed-key / unmapped feedback lives in [PlayStatusFooter] at the bottom.
 */
@Composable
private fun PlayHeader(
    page: Int,
    pageLabel: String,
    lastPageIndex: Int,
    pageTitles: List<String>,
    pageIsTactile: List<Boolean>,
    onOpenDrawer: () -> Unit,
    onPageChange: (Int) -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            "☰",
            style = MaterialTheme.typography.titleLarge,
            color = ink,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable(onClick = onOpenDrawer)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        PageSwitcher(
            page = page,
            pageLabel = pageLabel,
            lastPageIndex = lastPageIndex,
            onPageChange = onPageChange,
            pageTitles = pageTitles,
            pageIsTactile = pageIsTactile,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Bottom-centered feedback: unmapped above last-fired. Fully transparent,
 *  minimal height — pad clusters reclaim the bottom; overlap is OK. */
@Composable
private fun PlayStatusFooter(
    lastFired: String,
    unmappedHint: String,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (unmappedHint.isNotBlank()) {
            Text(
                unmappedHint,
                color = MaterialTheme.colorScheme.error,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (lastFired.isBlank()) "En attente…" else lastFired,
            color = if (lastFired.isBlank()) muted else ink,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Disposition = CAPTURE 2 (référence utilisateur) :
 * - LS et ABXY sur la MÊME ligne horizontale (milieu-haut)
 * - D-pad et RS sur la MÊME ligne horizontale (bas)
 * - Clusters centrés dans chaque demi-écran (pas collés haut/bas via Spacer)
 * Grille 2×2, Alignment.CenterStart / CenterEnd.
 * Footer transparent overlay ; libellés sticks complets sous les cercles.
 */
@Composable
private fun PlayPadBody(
    app: ThorOscApp,
    levels: Map<ControlId, Float>,
    page: Int,
    compact: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        TopControlsRow(app, levels, page, compact = compact)
        Capture2Grid(
            app = app,
            levels = levels,
            page = page,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

/**
 * Grille capture 2 : deux rangées de hauteur égale.
 * Rangée haute → LS | ABXY (centrés verticalement ensemble)
 * Rangée basse → D-pad | RS (centrés verticalement ensemble)
 */
@Composable
private fun Capture2Grid(
    app: ThorOscApp,
    levels: Map<ControlId, Float>,
    page: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val colW = maxWidth / 2
        // Rangée haute assez haute pour X/Y/A/B + libellés (évite le clip Android de B).
        val topWeight = 1.00f
        val botWeight = 1.00f
        val topRowH = maxHeight * topWeight / (topWeight + botWeight)
        val botRowH = maxHeight * botWeight / (topWeight + botWeight)
        val stickSize = (botRowH * 0.36f).coerceIn(88.dp, 100.dp).coerceAtMost(colW * 0.34f)
        // Hauteur FaceDiamond ≈ 3×face + ~46.dp (notes X/B + gaps). Doit être ≤ topRowH.
        val faceSize = ((topRowH - 50.dp) / 3.05f).coerceIn(32.dp, 46.dp)
        val dpadSize = (faceSize * 0.78f).coerceIn(28.dp, 38.dp)
        Column(Modifier.fillMaxSize()) {
            // —— Rangée haute : LS + ABXY en haut (dégage le libellé B) ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(topWeight),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    StickPad(
                        label = "LS",
                        x = levels[ControlId.AXIS_LX] ?: 0f,
                        y = levels[ControlId.AXIS_LY] ?: 0f,
                        accent = ColorStickL,
                        size = stickSize,
                        clickLevel = digitalLevel(app, page, ControlId.BTN_L3, levels),
                        clickLabel = "L3",
                        note = stickLabel(app, page, ControlId.AXIS_LX, ControlId.AXIS_LY, ControlId.BTN_L3),
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    FaceDiamond(levels, app, page, buttonSize = faceSize)
                }
            }
            // —— Rangée basse : D-pad + RS ancrés en bas → même ligne de libellés ——
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(botWeight),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    DpadCluster(levels, app, page, buttonSize = dpadSize)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // Fine-tune : aligne la note RS sur le libellé D-pad↓.
                    Box(Modifier.offset(y = 4.dp)) {
                        StickPad(
                            label = "RS",
                            x = levels[ControlId.AXIS_RX] ?: 0f,
                            y = levels[ControlId.AXIS_RY] ?: 0f,
                            accent = ColorStickR,
                            size = stickSize,
                            clickLevel = digitalLevel(app, page, ControlId.BTN_R3, levels),
                            clickLabel = "R3",
                            note = stickLabel(app, page, ControlId.AXIS_RX, ControlId.AXIS_RY, ControlId.BTN_R3),
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun TopControlsRow(
    app: ThorOscApp,
    levels: Map<ControlId, Float>,
    page: Int,
    compact: Boolean,
) {
    val gap = if (compact) 6.dp else 10.dp
    val triggerWidth = if (compact) 72.dp else 96.dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        // Top-align so L1/L2/R1/R2 stay put; legends grow underneath.
        verticalAlignment = Alignment.Top,
    ) {
        CompactShoulder(ControlId.BTN_L1, "L1", levels, app, page, ColorShoulder, compact)
        CompactTrigger(ControlId.AXIS_L2, "L2", levels, app, page, ColorShoulder, triggerWidth, compact)
        Spacer(Modifier.weight(1f))
        CompactPill(ControlId.BTN_SELECT, "SEL", levels, app, page, ColorMeta, compact)
        CompactPill(ControlId.BTN_START, "START", levels, app, page, ColorMeta, compact)
        Spacer(Modifier.weight(1f))
        CompactShoulder(ControlId.BTN_R1, "R1", levels, app, page, ColorShoulder, compact)
        CompactTrigger(ControlId.AXIS_R2, "R2", levels, app, page, ColorShoulder, triggerWidth, compact, mirror = true)
    }
}

@Composable
private fun CompactShoulder(
    control: ControlId,
    label: String,
    levels: Map<ControlId, Float>,
    app: ThorOscApp,
    page: Int,
    accent: Color,
    compact: Boolean,
) {
    val pressed = digitalLit(app, page, control, levels)
    val note = legendFor(app, page, control)
    val band = if (compact) 28.dp else 32.dp
    val panel = playPanel()
    val ink = playInk()
    val bg by animateColorAsState(
        targetValue = if (pressed) accent else panel,
        animationSpec = tween(durationMillis = if (pressed) 40 else 140),
        label = "shoulder-bg-${control.name}",
    )
    val labelColor by animateColorAsState(
        targetValue = if (pressed) Color.White else ink,
        animationSpec = tween(durationMillis = if (pressed) 40 else 140),
        label = "shoulder-label-${control.name}",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.height(band),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(if (compact) 56.dp else 72.dp)
                    .height(band)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .border(1.dp, accent.copy(alpha = if (pressed) 1f else 0.4f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = labelColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 12.sp else 13.sp,
                )
            }
        }
        if (note.isNotBlank()) {
            LegendText(note, Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun CompactTrigger(
    control: ControlId,
    label: String,
    levels: Map<ControlId, Float>,
    app: ThorOscApp,
    page: Int,
    accent: Color,
    width: Dp,
    compact: Boolean,
    mirror: Boolean = false,
) {
    val level = abs(levels[control] ?: 0f).coerceIn(0f, 1f)
    val fill by animateFloatAsState(level, animationSpec = spring(stiffness = 400f), label = control.name)
    val note = legendFor(app, page, control)
    // Same band height as shoulders so L1/L2/R1/R2 share one baseline.
    val band = if (compact) 28.dp else 32.dp
    val panel = playPanel()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.height(band),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(width)
                    .height(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(panel)
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .align(if (mirror) Alignment.CenterEnd else Alignment.CenterStart)
                        .background(
                            Brush.horizontalGradient(
                                if (mirror) listOf(accent.copy(alpha = 0.35f), accent)
                                else listOf(accent, accent.copy(alpha = 0.35f))
                            )
                        )
                )
                Text(
                    label,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
        }
        if (note.isNotBlank()) {
            LegendText(note, Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun CompactPill(
    control: ControlId,
    label: String,
    levels: Map<ControlId, Float>,
    app: ThorOscApp,
    page: Int,
    accent: Color,
    compact: Boolean,
) {
    val pressed = digitalLit(app, page, control, levels)
    val note = legendFor(app, page, control)
    val band = if (compact) 28.dp else 32.dp
    val pillH = if (compact) 26.dp else 28.dp
    val panel = playPanel()
    val ink = playInk()
    val bg by animateColorAsState(
        targetValue = if (pressed) accent else panel,
        animationSpec = tween(durationMillis = if (pressed) 40 else 140),
        label = "pill-bg-${control.name}",
    )
    val labelColor by animateColorAsState(
        targetValue = if (pressed) Color.White else ink,
        animationSpec = tween(durationMillis = if (pressed) 40 else 140),
        label = "pill-label-${control.name}",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.height(band),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(if (compact) 58.dp else 72.dp)
                    .height(pillH)
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .border(1.dp, accent.copy(alpha = if (pressed) 1f else 0.4f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = labelColor,
                    fontSize = if (compact) 10.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (note.isNotBlank()) {
            LegendText(note, Modifier.padding(top = 2.dp))
        }
    }
}

/** Losange ABXY : libellé dans un couloir dédié autour de chaque bouton.
 *  Hauteur ≈ 3×[buttonSize] + libellés ; le parent doit allouer assez pour B + noteB. */
@Composable
private fun FaceDiamond(
    levels: Map<ControlId, Float>,
    app: ThorOscApp,
    page: Int,
    buttonSize: Dp = 52.dp,
) {
    val noteX = legendFor(app, page, ControlId.BTN_X)
    val noteY = legendFor(app, page, ControlId.BTN_Y)
    val noteA = legendFor(app, page, ControlId.BTN_A)
    val noteB = legendFor(app, page, ControlId.BTN_B)
    val sideW = 84.dp
    val labelGap = 4.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SideLegend(noteX, Modifier.padding(bottom = labelGap).widthIn(max = 110.dp))
        FillRound(ControlId.BTN_X, "X", levels, app, page, ColorX, buttonSize)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(sideW),
                contentAlignment = Alignment.CenterEnd,
            ) {
                SideLegend(noteY, Modifier.padding(end = labelGap).widthIn(max = sideW - labelGap))
            }
            FillRound(ControlId.BTN_Y, "Y", levels, app, page, ColorY, buttonSize)
            Spacer(Modifier.width(buttonSize * 0.95f))
            FillRound(ControlId.BTN_A, "A", levels, app, page, ColorA, buttonSize)
            Box(
                Modifier.width(sideW),
                contentAlignment = Alignment.CenterStart,
            ) {
                SideLegend(noteA, Modifier.padding(start = labelGap).widthIn(max = sideW - labelGap))
            }
        }
        // B même taille que X/Y/A ; emplacement de libellé toujours réservé.
        FillRound(ControlId.BTN_B, "B", levels, app, page, ColorB, buttonSize)
        Box(
            Modifier
                .padding(top = labelGap)
                .heightIn(min = 16.dp)
                .widthIn(max = 110.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (noteB.isNotBlank()) {
                LegendText(noteB)
            }
        }
    }
}

@Composable
private fun DpadCluster(
    levels: Map<ControlId, Float>,
    app: ThorOscApp,
    page: Int,
    buttonSize: Dp = 40.dp,
) {
    val noteUp = legendFor(app, page, ControlId.BTN_DPAD_UP)
    val noteLeft = legendFor(app, page, ControlId.BTN_DPAD_LEFT)
    val noteRight = legendFor(app, page, ControlId.BTN_DPAD_RIGHT)
    val noteDown = legendFor(app, page, ControlId.BTN_DPAD_DOWN)
    val sideW = 76.dp
    val labelGap = 6.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SideLegend(noteUp, Modifier.padding(bottom = labelGap).widthIn(max = 100.dp))
        FillRound(ControlId.BTN_DPAD_UP, "▲", levels, app, page, ColorDpad, buttonSize)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(sideW),
                contentAlignment = Alignment.Center,
            ) {
                SideLegend(noteLeft, Modifier.padding(end = labelGap).widthIn(max = sideW - labelGap))
            }
            FillRound(ControlId.BTN_DPAD_LEFT, "◀", levels, app, page, ColorDpad, buttonSize)
            Spacer(Modifier.width(buttonSize + 4.dp))
            FillRound(ControlId.BTN_DPAD_RIGHT, "▶", levels, app, page, ColorDpad, buttonSize)
            Box(
                Modifier.width(sideW),
                contentAlignment = Alignment.Center,
            ) {
                SideLegend(noteRight, Modifier.padding(start = labelGap).widthIn(max = sideW - labelGap))
            }
        }
        FillRound(ControlId.BTN_DPAD_DOWN, "▼", levels, app, page, ColorDpad, buttonSize)
        SideLegend(noteDown, Modifier.padding(top = labelGap).widthIn(max = 100.dp))
    }
}

/** Empty notes still occupy no space; non-blank notes sit clearly outside the control. */
@Composable
private fun SideLegend(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    LegendText(text, modifier)
}

@Composable
private fun FillRound(
    control: ControlId,
    label: String,
    levels: Map<ControlId, Float>,
    app: ThorOscApp,
    page: Int,
    accent: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    // Toggle: stay lit from logical latch; Momentary/FLOAT: hardware press.
    val pressed = digitalLit(app, page, control, levels)
    val panel = playPanel()
    val ink = playInk()
    val fill by animateColorAsState(
        targetValue = if (pressed) accent else accent.copy(alpha = 0.18f),
        animationSpec = tween(durationMillis = if (pressed) 40 else 140),
        label = "pad-fill-${control.name}",
    )
    val labelColor by animateColorAsState(
        targetValue = if (pressed) Color.White else ink,
        animationSpec = tween(durationMillis = if (pressed) 40 else 140),
        label = "pad-label-${control.name}",
    )
    // requiredSize: ignore parent max constraints so B isn't crushed when the row is tight.
    Box(modifier.requiredSize(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.toPx() * 0.06f
            drawCircle(color = panel)
            drawCircle(color = fill)
            drawCircle(
                color = accent.copy(alpha = if (pressed) 1f else 0.75f),
                style = Stroke(width = stroke),
            )
        }
        Text(
            label,
            color = labelColor,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.32f).sp,
        )
    }
}

@Composable
private fun StickPad(
    label: String,
    x: Float,
    y: Float,
    accent: Color,
    note: String,
    size: Dp = 110.dp,
    clickLevel: Float = 0f,
    clickLabel: String = "",
) {
    val ax by animateFloatAsState(x, animationSpec = spring(stiffness = 350f), label = "${label}x")
    val ay by animateFloatAsState(y, animationSpec = spring(stiffness = 350f), label = "${label}y")
    val click = abs(clickLevel).coerceIn(0f, 1f)
    val mag = min(1f, hypot(ax.toDouble(), ay.toDouble()).toFloat())
    val panel = playPanel()
    val ink = playInk()
    val muted = playMuted()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2f
                drawCircle(color = panel)
                drawCircle(color = accent.copy(alpha = 0.18f + 0.35f * mag + 0.25f * click), radius = r)
                drawCircle(
                    color = accent.copy(alpha = 0.55f + 0.35f * click),
                    style = Stroke(width = (3.dp + 2.dp * click).toPx()),
                )
                drawCircle(color = Color.White.copy(alpha = 0.08f), radius = r * 0.55f, style = Stroke(1.5.dp.toPx()))
                // Click-fill ring for L3/R3
                if (click > 0.01f) {
                    drawCircle(
                        color = accent.copy(alpha = 0.35f * click),
                        radius = r * (0.35f + 0.2f * click),
                    )
                }
                val travel = r * 0.42f
                val cx = center.x + ax * travel
                val cy = center.y + ay * travel
                drawCircle(
                    color = accent.copy(alpha = 0.95f),
                    radius = r * (0.28f + 0.06f * click),
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = ink.copy(alpha = 0.9f),
                    radius = r * 0.1f,
                    center = Offset(cx, cy),
                )
            }
            Text(
                if (click > 0.5f && clickLabel.isNotBlank()) clickLabel else label,
                color = if (click > 0.5f) ink else muted,
                fontSize = 10.sp,
                fontWeight = if (click > 0.5f) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp),
            )
        }
        if (note.isNotBlank()) {
            // Stick legends: wide, no ellipsis — may sit over the transparent footer.
            StickLegendText(
                note,
                Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Stick notes under LS/RS — full text, not truncated. */
@Composable
private fun StickLegendText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = playMuted(),
        fontSize = 11.sp,
        maxLines = 3,
        softWrap = true,
        overflow = TextOverflow.Clip,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.widthIn(max = 300.dp),
    )
}

@Composable
private fun LegendText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = playMuted(),
        fontSize = 11.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.widthIn(max = 200.dp),
    )
}

/** Toggle stays lit from logical latch (xor Invert); else hardware press. */
private fun digitalLit(
    app: ThorOscApp,
    page: Int,
    control: ControlId,
    levels: Map<ControlId, Float>,
): Boolean {
    val hw = abs(levels[control] ?: 0f) >= 0.5f
    return app.config.playDigitalLit(page, control.name, hw)
}

/** 0…1 level for stick click rings; Toggle latched → 1. */
private fun digitalLevel(
    app: ThorOscApp,
    page: Int,
    control: ControlId,
    levels: Map<ControlId, Float>,
): Float {
    if (digitalLit(app, page, control, levels)) return 1f
    return abs(levels[control] ?: 0f).coerceIn(0f, 1f)
}

/** All notes/mappings for a stick (LX+LY+L3 or RX+RY+R3), joined — so LY/L3
 *  and RY/R3 stay visible without changing control placement. */
private fun stickLabel(app: ThorOscApp, page: Int, vararg controls: ControlId): String {
    val parts = controls.mapNotNull { control ->
        val labeled = legendFor(app, page, control)
        labeled.takeIf { it.isNotBlank() }
    }.distinct()
    return parts.joinToString(" · ")
}

private fun legendFor(app: ThorOscApp, page: Int, vararg controls: ControlId): String {
    for (control in controls) {
        val note = app.config.noteFor(page, control.name).trim()
        if (note.isNotEmpty()) return note
    }
    for (control in controls) {
        val mapping = app.config.mappings.firstOrNull {
            it.controlId == control.name && it.page == page
        } ?: continue
        val short = mapping.address.substringAfterLast('/').ifBlank { mapping.address }
        val fb = app.feedback.getForMappedAddress(mapping.address)
        return when (fb) {
            null -> short
            is Unit -> "$short •"
            else -> "$short=$fb"
        }
    }
    return ""
}
