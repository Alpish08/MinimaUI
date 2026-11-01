package com.minimaui.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.minimaui.app.ui.theme.TextDim

enum class EdgeDirection { Left, Right }

private val ArrowColor = Color(0xFFD0D0D0)

/** Page arrow: a tall stadium outline with a small rounded triangle, nudged toward its tip to look centered. */
@Composable
fun EdgeArrowButton(
    direction: EdgeDirection,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .graphicsLayer { alpha = 0.7f }
            .size(62.dp, 74.dp)
            .clip(shape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val color = if (enabled) ArrowColor else TextDim
        val tipShift = if (direction == EdgeDirection.Left) (-1.5).dp else 1.5.dp
        Canvas(modifier = Modifier.offset(x = tipShift).size(22.dp, 40.dp)) {
            val inset = 3.dp.toPx()
            val path = Path().apply {
                if (direction == EdgeDirection.Left) {
                    moveTo(size.width - inset, inset)
                    lineTo(inset, size.height / 2f)
                    lineTo(size.width - inset, size.height - inset)
                } else {
                    moveTo(inset, inset)
                    lineTo(size.width - inset, size.height / 2f)
                    lineTo(inset, size.height - inset)
                }
                close()
            }
            drawPath(path, color, style = Fill)
            drawPath(path, color, style = Stroke(width = 6.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}
