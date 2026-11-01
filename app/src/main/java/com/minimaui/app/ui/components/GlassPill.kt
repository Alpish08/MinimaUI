package com.minimaui.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minimaui.app.ui.theme.PillSurface

/** A rounded, tile-colored surface with the dim background outline, used for floating chrome. */
fun Modifier.glassPill(cornerRadius: Dp, outlineDepth: Dp = 14.dp): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(PillSurface)
    .backgroundOutline(outlineDepth, cornerRadius)
