package com.minimaui.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minimaui.app.ui.theme.PillSurface

/**
 * How opaque the Favorites panel's fill is: 1 normally; less over the Minima wave, which shows behind Favorites (blurred), so
 * it shows through the panel a little. The other waves keep the exact black.
 */
val LocalSeeThrough = compositionLocalOf { 1f }

/** The Favorites panel's fill opacity over the Minima wave (see [LocalSeeThrough]). */
const val MinimaPanelSeeThrough = 0.4f

/** A rounded, tile-colored surface with the dim background outline, used for floating chrome. */
fun Modifier.glassPill(cornerRadius: Dp, outlineDepth: Dp = 14.dp): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(PillSurface)
    .backgroundOutline(outlineDepth, cornerRadius)
