package com.minimaui.app.ui.theme

import androidx.compose.ui.graphics.Color

// MinimaUI — OLED-black, monochrome palette.
val OledBlack = Color(0xFF000000)
val SurfacePanel = Color(0xFF0A0A0A)
val TileSurfaceFocused = Color(0xFF1E1E1E)

// Resting tiles/rows are translucent so the selection glow shows through into neighbours.
val TileFillIdle = Color(0xBF1A1A1A)

val OutlineDarkGray = Color(0xFF3A3A3A)
val OutlineFocused = Color(0xFFE5E5E5)

val TextPrimary = Color(0xFFF2F2F2)
val TextSecondary = Color(0xFF8E8E8E)
val TextDim = Color(0xFF666666)
val HintLabel = Color(0xFFD1D1D1)

val GlowColor = Color(0xFFB4B4B4)
val IndicatorOn = Color(0xFFFFFFFF)
val DotDim = Color(0x80FFFFFF)
val MenuPanelFill = Color(0x99141414)
val MenuScrim = Color(0x99000000)

// Tiles while rearranging in Edit Mode: darker, so the focused one stands out.
val EditTileFill = Color(0xFF1A1A1A)

// Settings-menu pills: resting ones a little lighter than the panel, the selected one darker.
val MenuPillIdle = Color(0x99303030)
val MenuPillActive = Color(0xFF181818)

// Floating pills (status, control hints) use the same solid fill as the All Games platform tiles.
val PillSurface = Color(0xFF0C0C0C)
