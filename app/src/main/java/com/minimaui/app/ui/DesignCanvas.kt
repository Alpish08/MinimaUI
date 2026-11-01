package com.minimaui.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.OledBlack

/**
 * Lays content out on a fixed 1920x1080 canvas where 1dp (and 1sp) equals one design pixel,
 * scaled uniformly to fit the real screen. System density and font scale are ignored on purpose.
 */
@Composable
fun FixedDesignCanvas(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(OledBlack),
        contentAlignment = Alignment.Center
    ) {
        val scale = minOf(
            constraints.maxWidth / Dimens.DesignWidth,
            constraints.maxHeight / Dimens.DesignHeight
        )
        CompositionLocalProvider(LocalDensity provides Density(density = scale, fontScale = 1f)) {
            Box(
                modifier = Modifier.size(Dimens.DesignWidth.dp, Dimens.DesignHeight.dp),
                content = content
            )
        }
    }
}
