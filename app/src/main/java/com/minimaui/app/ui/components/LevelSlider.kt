package com.minimaui.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaui.app.ui.theme.TextPrimary

// The bars sit at the top, over the status pill, which they dim and blur away.
const val LevelCenterX = 960f
// Like the boot logo's patch (same radius, same dim), but centred on the top edge so only its bottom half shows.
const val LevelCenterY = 0f
const val LevelRadius = 760f
const val LevelHeightShare = 0.5f // the patch is this much as tall as it is wide
const val LevelBlurRadius = 1242f
private const val BarWidth = 580f
private const val BarHeight = 36f

/** A dimmed, faded-edged patch behind the bars (the same look as the boot logo's). [alpha] is read at draw time. */
@Composable
fun LevelBackdrop(alpha: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) }
            .drawBehind {
                val unit = size.width / 1920f
                val center = Offset(LevelCenterX * unit, LevelCenterY * unit)
                scale(scaleX = 1f, scaleY = LevelHeightShare, pivot = center) {
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0xE6000000),
                        0.55f to Color(0xB3000000),
                        1f to Color.Transparent,
                        center = center,
                        radius = LevelRadius * unit
                    ),
                    radius = LevelRadius * unit,
                    center = center
                )
                }
            }
    )
}

/** "Volume:" or "Brightness:" over a bar filled to [fraction] (0..1); display only. [alpha] is read at draw time. */
@Composable
fun LevelSlider(label: String, fraction: Float, alpha: () -> Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) }) {
        Text(
            text = label,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp)
        )
        // The filled part sits inside the outline with an even gap all round.
        val inset = 5f
        val innerHeight = BarHeight - 2 * inset
        val innerWidth = BarWidth - 2 * inset
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 72.dp)
                .size(BarWidth.dp, BarHeight.dp)
                .backgroundOutline(depth = 10.dp)
                .padding(inset.dp)
        ) {
            // Nothing at all at 0%; any other level is at least a full round end wide.
            if (fraction > 0.004f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f).coerceAtLeast(innerHeight / innerWidth))
                        .background(Color.White, CircleShape)
                )
            }
        }
    }
}
