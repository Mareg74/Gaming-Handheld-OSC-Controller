package com.mareg74.osccontroller.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.mareg74.osccontroller.config.ColorPalette
import com.mareg74.osccontroller.config.ThemeMode

// A · Teal scène
private val TealDark = darkColorScheme(
    primary = Color(0xFF2EC4B6),
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF005049),
    onPrimaryContainer = Color(0xFF8FF0E4),
    secondary = Color(0xFFF4A261),
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF6A3B00),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFF4BA3F0),
    onTertiary = Color(0xFF003258),
    tertiaryContainer = Color(0xFF00497D),
    onTertiaryContainer = Color(0xFFD0E4FF),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE8EEF5),
    surface = Color(0xFF151C24),
    onSurface = Color(0xFFE8EEF5),
    surfaceVariant = Color(0xFF2A3340),
    onSurfaceVariant = Color(0xFFC2CBD6),
    surfaceContainerLowest = Color(0xFF080B0F),
    surfaceContainerLow = Color(0xFF11161C),
    surfaceContainer = Color(0xFF151C24),
    surfaceContainerHigh = Color(0xFF1C2430),
    surfaceContainerHighest = Color(0xFF242D3A),
    outline = Color(0xFF8C96A3),
    outlineVariant = Color(0xFF414B58),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val TealLight = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFFC2410C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFEDD5),
    onSecondaryContainer = Color(0xFF7C2D12),
    tertiary = Color(0xFF2563EB),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDBEAFE),
    onTertiaryContainer = Color(0xFF1E3A8A),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE8EEF4),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F9),
    surfaceContainer = Color(0xFFE8EEF4),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFDCE3EB),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
)

// B · Pupitre amber
private val AmberDark = darkColorScheme(
    primary = Color(0xFFE8B84A),
    onPrimary = Color(0xFF3D2A00),
    primaryContainer = Color(0xFF6B4E00),
    onPrimaryContainer = Color(0xFFFFE4A3),
    secondary = Color(0xFF4ECDC4),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF005049),
    onSecondaryContainer = Color(0xFFA8F0EA),
    tertiary = Color(0xFF7EB8E8),
    onTertiary = Color(0xFF003258),
    tertiaryContainer = Color(0xFF00497D),
    onTertiaryContainer = Color(0xFFD0E4FF),
    background = Color(0xFF12100C),
    onBackground = Color(0xFFF5F0E6),
    surface = Color(0xFF1C1914),
    onSurface = Color(0xFFF5F0E6),
    surfaceVariant = Color(0xFF2E2A22),
    onSurfaceVariant = Color(0xFFC9BFAE),
    surfaceContainerLowest = Color(0xFF0E0C09),
    surfaceContainerLow = Color(0xFF17140F),
    surfaceContainer = Color(0xFF1C1914),
    surfaceContainerHigh = Color(0xFF262118),
    surfaceContainerHighest = Color(0xFF2E2A22),
    outline = Color(0xFF9A9080),
    outlineVariant = Color(0xFF463F34),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val AmberLight = lightColorScheme(
    primary = Color(0xFFA16207),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFDE68A),
    onPrimaryContainer = Color(0xFF713F12),
    secondary = Color(0xFF0F766E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    tertiary = Color(0xFF1D4ED8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDBEAFE),
    onTertiaryContainer = Color(0xFF1E3A8A),
    background = Color(0xFFFAF8F4),
    onBackground = Color(0xFF1C1914),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1914),
    surfaceVariant = Color(0xFFF0EBE0),
    onSurfaceVariant = Color(0xFF5C5346),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F1EA),
    surfaceContainer = Color(0xFFEDE7DB),
    surfaceContainerHigh = Color(0xFFE6DFD0),
    surfaceContainerHighest = Color(0xFFDFD8C8),
    outline = Color(0xFFA89F8C),
    outlineVariant = Color(0xFFD6CFC0),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
)

// C · Cyan broadcast
private val CyanDark = darkColorScheme(
    primary = Color(0xFF22D3EE),
    onPrimary = Color(0xFF003640),
    primaryContainer = Color(0xFF0E7490),
    onPrimaryContainer = Color(0xFFCFFAFE),
    secondary = Color(0xFFFB7185),
    onSecondary = Color(0xFF4C0519),
    secondaryContainer = Color(0xFF9F1239),
    onSecondaryContainer = Color(0xFFFFE4E6),
    tertiary = Color(0xFFA3E635),
    onTertiary = Color(0xFF1A2E05),
    tertiaryContainer = Color(0xFF3F6212),
    onTertiaryContainer = Color(0xFFD9F99D),
    background = Color(0xFF0A0E12),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF12181F),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF1E2832),
    onSurfaceVariant = Color(0xFFA8B6C4),
    surfaceContainerLowest = Color(0xFF070A0D),
    surfaceContainerLow = Color(0xFF0E1318),
    surfaceContainer = Color(0xFF12181F),
    surfaceContainerHigh = Color(0xFF1A222B),
    surfaceContainerHighest = Color(0xFF1E2832),
    outline = Color(0xFF7A8A9A),
    outlineVariant = Color(0xFF33404C),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF690005),
)

private val CyanLight = lightColorScheme(
    primary = Color(0xFF0891B2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = Color(0xFF155E75),
    secondary = Color(0xFFE11D48),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE4E6),
    onSecondaryContainer = Color(0xFF9F1239),
    tertiary = Color(0xFF65A30D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9F99D),
    onTertiaryContainer = Color(0xFF365314),
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF0C1220),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0C1220),
    surfaceVariant = Color(0xFFE4EBF2),
    onSurfaceVariant = Color(0xFF4B5A6A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF2F6),
    surfaceContainer = Color(0xFFE4EBF2),
    surfaceContainerHigh = Color(0xFFD9E2EB),
    surfaceContainerHighest = Color(0xFFD0DBE6),
    outline = Color(0xFF8B9AAB),
    outlineVariant = Color(0xFFC5D0DB),
    error = Color(0xFFBE123C),
    onError = Color(0xFFFFFFFF),
)

// D · Lime club
private val LimeDark = darkColorScheme(
    primary = Color(0xFFA3E635),
    onPrimary = Color(0xFF1A2E05),
    primaryContainer = Color(0xFF3F6212),
    onPrimaryContainer = Color(0xFFD9F99D),
    secondary = Color(0xFFF472B6),
    onSecondary = Color(0xFF500724),
    secondaryContainer = Color(0xFF9D174D),
    onSecondaryContainer = Color(0xFFFCE7F3),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF003258),
    tertiaryContainer = Color(0xFF075985),
    onTertiaryContainer = Color(0xFFE0F2FE),
    background = Color(0xFF0A0B08),
    onBackground = Color(0xFFF0F4E8),
    surface = Color(0xFF121410),
    onSurface = Color(0xFFF0F4E8),
    surfaceVariant = Color(0xFF1E2218),
    onSurfaceVariant = Color(0xFFB4BCA4),
    surfaceContainerLowest = Color(0xFF060705),
    surfaceContainerLow = Color(0xFF0E100C),
    surfaceContainer = Color(0xFF121410),
    surfaceContainerHigh = Color(0xFF1A1E15),
    surfaceContainerHighest = Color(0xFF1E2218),
    outline = Color(0xFF8A9478),
    outlineVariant = Color(0xFF353B2C),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF690005),
)

private val LimeLight = lightColorScheme(
    primary = Color(0xFF4D7C0F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9F99D),
    onPrimaryContainer = Color(0xFF365314),
    secondary = Color(0xFFDB2777),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFCE7F3),
    onSecondaryContainer = Color(0xFF9D174D),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF075985),
    background = Color(0xFFF6F7F2),
    onBackground = Color(0xFF14160F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14160F),
    surfaceVariant = Color(0xFFE8EBE0),
    onSurfaceVariant = Color(0xFF545B48),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF1EA),
    surfaceContainer = Color(0xFFE8EBE0),
    surfaceContainerHigh = Color(0xFFDEE2D4),
    surfaceContainerHighest = Color(0xFFD5DAC9),
    outline = Color(0xFF919984),
    outlineVariant = Color(0xFFCED3C2),
    error = Color(0xFFBE123C),
    onError = Color(0xFFFFFFFF),
)

// E · Violet scène
private val VioletDark = darkColorScheme(
    primary = Color(0xFFC4B5FD),
    onPrimary = Color(0xFF2E1065),
    primaryContainer = Color(0xFF5B21B6),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF422006),
    secondaryContainer = Color(0xFF92400E),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFF67E8F9),
    onTertiary = Color(0xFF003640),
    tertiaryContainer = Color(0xFF0E7490),
    onTertiaryContainer = Color(0xFFCFFAFE),
    background = Color(0xFF120E18),
    onBackground = Color(0xFFF0EAF8),
    surface = Color(0xFF1A1524),
    onSurface = Color(0xFFF0EAF8),
    surfaceVariant = Color(0xFF2C2438),
    onSurfaceVariant = Color(0xFFC4B8D6),
    surfaceContainerLowest = Color(0xFF0C0912),
    surfaceContainerLow = Color(0xFF15101C),
    surfaceContainer = Color(0xFF1A1524),
    surfaceContainerHigh = Color(0xFF231C30),
    surfaceContainerHighest = Color(0xFF2C2438),
    outline = Color(0xFF9588A8),
    outlineVariant = Color(0xFF3D334C),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF690005),
)

private val VioletLight = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF4C1D95),
    secondary = Color(0xFFB45309),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFEDD5),
    onSecondaryContainer = Color(0xFF7C2D12),
    tertiary = Color(0xFF0E7490),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFFAFE),
    onTertiaryContainer = Color(0xFF155E75),
    background = Color(0xFFF7F4FB),
    onBackground = Color(0xFF1A1228),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1228),
    surfaceVariant = Color(0xFFEDE7F6),
    onSurfaceVariant = Color(0xFF5B4F6E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1ECF7),
    surfaceContainer = Color(0xFFE8E0F2),
    surfaceContainerHigh = Color(0xFFDFD5EB),
    surfaceContainerHighest = Color(0xFFD6CCE4),
    outline = Color(0xFF9588A8),
    outlineVariant = Color(0xFFD5CBE3),
    error = Color(0xFFBE123C),
    onError = Color(0xFFFFFFFF),
)

private fun schemeFor(palette: ColorPalette, dark: Boolean): ColorScheme = when (palette) {
    ColorPalette.TEAL_SCENE -> if (dark) TealDark else TealLight
    ColorPalette.PUPITRE_AMBER -> if (dark) AmberDark else AmberLight
    ColorPalette.CYAN_BROADCAST -> if (dark) CyanDark else CyanLight
    ColorPalette.LIME_CLUB -> if (dark) LimeDark else LimeLight
    ColorPalette.VIOLET_SCENE -> if (dark) VioletDark else VioletLight
}

@Composable
fun ThorOscTheme(
    themeMode: ThemeMode,
    colorPalette: ColorPalette = ColorPalette.TEAL_SCENE,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val scheme = schemeFor(colorPalette, dark)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Transparent system bars so content can draw edge-to-edge (no black bottom strip).
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
