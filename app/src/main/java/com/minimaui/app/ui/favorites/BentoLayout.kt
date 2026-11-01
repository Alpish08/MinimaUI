package com.minimaui.app.ui.favorites

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minimaui.app.ui.components.TileSize
import kotlin.math.abs

enum class Direction { Up, Down, Left, Right }

/** A tile position inside the favorites panel, in design pixels. */
data class BentoSlot(
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
    val size: TileSize,
) {
    internal val left get() = x.value
    internal val top get() = y.value
    internal val right get() = x.value + width.value
    internal val bottom get() = y.value + height.value
    internal val centerX get() = (left + right) / 2f
    internal val centerY get() = (top + bottom) / 2f
}

object BentoLayouts {
    /** One large cover, three medium covers and four small ones. Slot order is the favorites order. */
    val Default = listOf(
        BentoSlot(59.dp, 52.dp, 744.dp, 736.dp, TileSize.Large),
        BentoSlot(890.dp, 52.dp, 344.dp, 344.dp, TileSize.Medium),
        BentoSlot(1289.dp, 52.dp, 344.dp, 344.dp, TileSize.Medium),
        BentoSlot(890.dp, 444.dp, 344.dp, 344.dp, TileSize.Medium),
        BentoSlot(1289.dp, 444.dp, 158.dp, 158.dp, TileSize.Small),
        BentoSlot(1474.dp, 444.dp, 158.dp, 158.dp, TileSize.Small),
        BentoSlot(1289.dp, 625.dp, 158.dp, 158.dp, TileSize.Small),
        BentoSlot(1474.dp, 625.dp, 158.dp, 158.dp, TileSize.Small),
    )

    /** Every Favorites page after the first: eight equal tiles in two blocks of two columns, filled block by block (left block top-to-bottom rows first, then the right). */
    val Grid = listOf(
        BentoSlot(59.dp, 52.dp, 344.dp, 344.dp, TileSize.Medium),
        BentoSlot(457.dp, 52.dp, 344.dp, 344.dp, TileSize.Medium),
        BentoSlot(59.dp, 445.dp, 344.dp, 344.dp, TileSize.Medium),
        BentoSlot(457.dp, 445.dp, 344.dp, 344.dp, TileSize.Medium),
        BentoSlot(892.dp, 52.dp, 344.dp, 344.dp, TileSize.Medium),
        BentoSlot(1289.dp, 52.dp, 344.dp, 344.dp, TileSize.Medium),
        BentoSlot(892.dp, 445.dp, 344.dp, 344.dp, TileSize.Medium),
        BentoSlot(1289.dp, 445.dp, 344.dp, 344.dp, TileSize.Medium),
    )

    /** The Apps page: 212dp round icons in rows of 3, 4 and 3 (screen coordinates, top-left corners). */
    val Apps: List<BentoSlot> = listOf(
        605f to 272f, 960f to 272f, 1315f to 272f,
        428f to 535f, 783f to 535f, 1138f to 535f, 1493f to 535f,
        605f to 798f, 960f to 798f, 1315f to 798f,
    ).map { (cx, cy) -> BentoSlot((cx - 106f).dp, (cy - 106f).dp, 212.dp, 212.dp, TileSize.Medium) }
}

/**
 * Picks the slot to move to from [from] in [direction], among the slots [allowed] says can be focused:
 * the nearest one that lies fully in that direction, preferring slots that overlap on the other axis.
 */
fun findNeighbor(slots: List<BentoSlot>, allowed: (Int) -> Boolean, from: Int, direction: Direction): Int? {
    val a = slots[from]
    var best: Int? = null
    var bestScore = Float.MAX_VALUE
    for (i in slots.indices) {
        if (i == from || !allowed(i)) continue
        val b = slots[i]
        val gap: Float
        val overlaps: Boolean
        val offAxis: Float
        when (direction) {
            Direction.Right -> {
                gap = b.left - a.right
                overlaps = b.top < a.bottom && b.bottom > a.top
                offAxis = abs(b.centerY - a.centerY)
            }
            Direction.Left -> {
                gap = a.left - b.right
                overlaps = b.top < a.bottom && b.bottom > a.top
                offAxis = abs(b.centerY - a.centerY)
            }
            Direction.Down -> {
                gap = b.top - a.bottom
                overlaps = b.left < a.right && b.right > a.left
                offAxis = abs(b.centerX - a.centerX)
            }
            Direction.Up -> {
                gap = a.top - b.bottom
                overlaps = b.left < a.right && b.right > a.left
                offAxis = abs(b.centerX - a.centerX)
            }
        }
        if (gap < -1f) continue
        val score = gap * 4f + offAxis + if (overlaps) 0f else 1000f
        if (score < bestScore) {
            bestScore = score
            best = i
        }
    }
    return best
}
