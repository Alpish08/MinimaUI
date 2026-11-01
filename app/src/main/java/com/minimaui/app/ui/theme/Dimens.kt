package com.minimaui.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * MinimaUI is designed for exactly one screen: 1920x1080 landscape (Retroid Pocket 6).
 * The whole UI is laid out on a 1920x1080 design canvas where 1dp == 1 design pixel
 * (see FixedDesignCanvas), so values here map 1:1 to the mockups.
 */
object Dimens {
    const val DesignWidth = 1920f
    const val DesignHeight = 1080f

    val FocusedBorderWidth = 6.dp

    val StatusPillWidth = 440.dp
    val StatusPillHeight = 71.dp
    val StatusPillTop = 27.dp

    val PanelX = 113.dp
    val PanelY = 127.dp
    val PanelWidth = 1694.dp
    val PanelHeight = 844.dp
    val PanelCorner = 90.dp
    val FavoritesPanelCorner = 90.dp

    // Hint bar and the Favorites label share a vertical centre at y = 1022.
    val HintBarHeight = 50.4.dp
    val HintBarEnd = 43.dp
    val HintBarBottom = 32.8.dp
}
