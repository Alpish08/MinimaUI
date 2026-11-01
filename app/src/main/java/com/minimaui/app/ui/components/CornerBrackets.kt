package com.minimaui.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The four corner marks of Edit Mode around the rectangle ([left], [top], [right], [bottom]): each is an arm along
 * the vertical edge, a quarter circle of [radius], and an arm along the horizontal edge ([arm] long).
 */
fun DrawScope.drawCornerBrackets(left: Float, top: Float, right: Float, bottom: Float, radius: Float, arm: Float, stroke: Stroke, color: Color) {
    fun corner(origin: Offset, dx: Float, dy: Float) {
        val path = Path().apply {
            moveTo(origin.x, origin.y + dy * (radius + arm))
            lineTo(origin.x, origin.y + dy * radius)
            val arc = Rect(
                if (dx > 0) origin.x else origin.x - 2 * radius,
                if (dy > 0) origin.y else origin.y - 2 * radius,
                if (dx > 0) origin.x + 2 * radius else origin.x,
                if (dy > 0) origin.y + 2 * radius else origin.y
            )
            // From the vertical edge, around to the horizontal one.
            val start = if (dx > 0 && dy > 0) 180f else if (dx < 0 && dy > 0) 0f else if (dx > 0) 180f else 0f
            val sweep = if ((dx > 0) == (dy > 0)) 90f else -90f
            arcTo(arc, start, sweep, false)
            lineTo(origin.x + dx * (radius + arm), origin.y)
        }
        drawPath(path, color, style = stroke)
    }
    corner(Offset(left, top), 1f, 1f)
    corner(Offset(right, top), -1f, 1f)
    corner(Offset(left, bottom), 1f, -1f)
    corner(Offset(right, bottom), -1f, -1f)
}
