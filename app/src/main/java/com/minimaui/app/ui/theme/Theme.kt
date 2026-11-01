package com.minimaui.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MinimaDarkColorScheme = darkColorScheme(
    primary = TextPrimary,
    onPrimary = OledBlack,
    secondary = TextSecondary,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = SurfacePanel,
    onSurface = TextPrimary,
    outline = OutlineDarkGray,
)

/**
 * MinimaUI is a fixed, single-theme (always-dark, OLED-black) frontend.
 * No dynamic color, no light theme — the look is intentionally locked.
 */
@Composable
fun MinimaUITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MinimaDarkColorScheme,
        typography = Typography,
        content = content
    )
}
