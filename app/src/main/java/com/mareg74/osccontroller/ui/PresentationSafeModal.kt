package com.mareg74.osccontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Modals on the Thor bottom [android.app.Presentation] must NOT use
 * [androidx.compose.ui.window.Dialog] / AlertDialog — those open a new Window and crash.
 *
 * Prefer:
 * 1. [InTreeModal] — draw inside an existing fillMaxSize Box (no Window). Best when
 *    you already have a root Box (Mapping, Settings, NDI).
 * 2. [PresentationSafePopup] — Popup attached to the current Compose view token
 *    (Presentation window), for widgets nested deep (e.g. [PageSwitcher]).
 */
@Composable
fun InTreeModal(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
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
        content()
    }
}

/**
 * Full-screen scrim + centered card via Popup (Presentation-safe).
 * Do not replace with Dialog.
 */
@Composable
fun PresentationSafePopup(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val w = with(density) { config.screenWidthDp.dp }
    val h = with(density) { config.screenHeightDp.dp }

    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        Box(
            Modifier
                .requiredSize(w, h)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 420.dp),
            ) {
                content()
            }
        }
    }
}
