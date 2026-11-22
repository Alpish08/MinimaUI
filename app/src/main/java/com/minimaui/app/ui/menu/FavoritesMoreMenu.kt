package com.minimaui.app.ui.menu

import androidx.compose.runtime.getValue
import com.minimaui.app.ui.components.backgroundOutline
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.minimaui.app.R
import com.minimaui.app.ui.components.ControlHint
import com.minimaui.app.ui.components.ControlHintBar
import com.minimaui.app.ui.components.EaseInOut
import com.minimaui.app.ui.components.FocusRing
import com.minimaui.app.ui.components.GlowRect
import com.minimaui.app.ui.components.SelectionGlow
import com.minimaui.app.ui.components.UnfocusedOutline
import com.minimaui.app.ui.components.softOutline
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.MenuPanelFill
import com.minimaui.app.ui.theme.MenuScrim
import com.minimaui.app.ui.theme.TileSurfaceFocused
import com.minimaui.app.ui.components.BackgroundOutline
import com.minimaui.app.ui.components.TileSize
import com.minimaui.app.ui.components.drawFadingOutline
import com.minimaui.app.ui.favorites.BentoLayouts
import com.minimaui.app.ui.favorites.LauncherSlot
import com.minimaui.app.ui.favorites.focusScaleFor
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset

/**
 * What the Favorites launcher tile offers, a 3x3 grid like its icon: Folders, Recently Played, Random Game / Settings, Apps,
 * Search / Edit Mode, Gallery, Music.
 */
const val MoreMenuEntries = 9
const val MoreMenuColumns = 3

private const val PanelX = 535f
private const val PanelY = 115f
private const val PanelW = 850f
private const val PanelH = 850f
private const val SlotSize = 190f
private const val SlotPitch = 242f
private const val RowPitch = 250f
private const val SlotCorner = 44f
private const val SlotGrow = 0.11f
private val SlotFill = androidx.compose.ui.graphics.Color(0xFF2B2B2B)
private val SlotFillFocused = androidx.compose.ui.graphics.Color(0xFF1B1B1B)

private fun centerX(i: Int) = 960f + (i % MoreMenuColumns - 1f) * SlotPitch
private const val Rows = (MoreMenuEntries + MoreMenuColumns - 1) / MoreMenuColumns
private fun centerY(i: Int) = 540f + (i / MoreMenuColumns - (Rows - 1) / 2f) * RowPitch

/**
 * The menu the Favorites launcher tile opens: a 3x3 grid of square choices. The chosen one is a little larger, with the
 * standard glow and ring. It grows out of the tile ([progress] 0 = the tile, 1 = open): the tile's shape stretches into the
 * panel's while its nine dots spread out toward the slots and fade, and the slots and their icons grow in from them.
 */
@Composable
fun FavoritesMoreMenu(
    selected: Int,
    hints: List<ControlHint>,
    modifier: Modifier = Modifier,
    progress: () -> Float = { 1f },
    focusShown: Boolean = true,
    onTapSlot: (Int) -> Unit = {},
    onTapOutside: () -> Unit = {},
) {
    val density = LocalDensity.current.density
    val icons = listOf(
        R.drawable.ic_folder, R.drawable.ic_history, R.drawable.ic_dice,
        R.drawable.ic_menu, R.drawable.ic_apps, R.drawable.ic_search,
        R.drawable.ic_pen, R.drawable.ic_gallery, R.drawable.ic_music,
    )
    val iconSizes = listOf(92, 112, 114, 96, 94, 105, 100, 100, 104)
    val late = { lateIn(progress()) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val x = offset.x / density
                    val y = offset.y / density
                    if (x < PanelX || x > PanelX + PanelW || y < PanelY || y > PanelY + PanelH) {
                        onTapOutside()
                        return@detectTapGestures
                    }
                    for (i in 0 until MoreMenuEntries) {
                        if (kotlin.math.abs(x - centerX(i)) <= SlotSize / 2f && kotlin.math.abs(y - centerY(i)) <= SlotSize / 2f) onTapSlot(i)
                    }
                }
            }
    ) {
        // The screen behind dims as the menu opens.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress() }.background(MenuScrim))
        Box(Modifier.morphingMenuPanel(progress))
        // Opened by touch, the focus is kept (a key shows it) but not drawn.
        val shown = if (focusShown) selected else -1
        // The glow and the ring move and grow with the chosen slot as it comes out of its dot.
        if (focusShown) Box(Modifier.fillMaxSize().slotMorph(selected, progress)) {
            SelectionGlow(GlowRect(centerX(selected) - SlotSize / 2f, centerY(selected) - SlotSize / 2f, SlotSize, SlotSize, SlotCorner))
        }
        for (i in 0 until MoreMenuEntries) {
            val grow by animateFloatAsState(if (i == shown) 1f + SlotGrow else 1f, tween(200, easing = EaseInOut), label = "moreGrow")
            val shape = RoundedCornerShape(SlotCorner.dp)
            Box(
                modifier = Modifier
                    .offset((centerX(i) - SlotSize / 2f).dp, (centerY(i) - SlotSize / 2f).dp)
                    .size(SlotSize.dp)
                    .slotMorph(i, progress, fullScreen = false)
                    .graphicsLayer {
                        scaleX = grow
                        scaleY = grow
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
                    .clip(shape)
                    .background(if (i == shown) SlotFillFocused else SlotFill)
                    // The covers' soft outline instead of a hard border; the chosen slot gets the ring.
                    .softOutline(depth = 21.dp, corner = SlotCorner.dp) { UnfocusedOutline },
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(icons[i]), contentDescription = null, modifier = Modifier.size(iconSizes[i].dp))
            }
        }
        if (focusShown) Box(Modifier.fillMaxSize().slotMorph(selected, progress)) {
            FocusRing(
                GlowRect(centerX(selected) - SlotSize / 2f, centerY(selected) - SlotSize / 2f, SlotSize, SlotSize, SlotCorner),
                scale = { 1f + SlotGrow },
                // The covers' soft white-to-transparent focus outline.
                fadeDepth = 21.dp,
                underStrength = UnfocusedOutline
            )
        }
        ControlHintBar(
            hints = hints,
            pill = false,
            pageSize = true,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Dimens.HintBarEnd + 17.dp, bottom = Dimens.HintBarBottom + 9.dp)
                .graphicsLayer { alpha = late() }
        )
    }
}

// The launcher tile the menu grows out of (the first Favorites page's bottom right small tile, focused), on screen.
private val Tile = BentoLayouts.Default[LauncherSlot]
private val TileCenterX = Dimens.PanelX.value + Tile.centerX
private val TileCenterY = Dimens.PanelY.value + Tile.centerY
private val TileSide = Tile.width.value * focusScaleFor(TileSize.Small)
private val TileCorner = TileSize.Small.corner.value * focusScaleFor(TileSize.Small)
private const val PanelCorner = 120f
private const val DotRadius = 6.3f
private const val DotStep = 25f // between the tile's dots
private const val DotScale = DotRadius * 2f / SlotSize

// Slot [i]'s dot on the tile.
private fun dotX(i: Int) = TileCenterX + (i % MoreMenuColumns - 1f) * DotStep
private fun dotY(i: Int) = TileCenterY + (i / MoreMenuColumns - 1f) * DotStep

/**
 * Slot [i] (or what goes with it) at [progress]: from its dot on the tile to its place, growing from the dot's size, coming in
 * mid-way. [fullScreen]: drawn over the whole screen (the glow, the ring), so it scales around the slot's middle.
 */
private fun Modifier.slotMorph(i: Int, progress: () -> Float, fullScreen: Boolean = true): Modifier = graphicsLayer {
    val e = progress()
    val sc = DotScale + (1f - DotScale) * e
    scaleX = sc
    scaleY = sc
    if (fullScreen) transformOrigin = androidx.compose.ui.graphics.TransformOrigin(centerX(i) / Dimens.DesignWidth, centerY(i) / Dimens.DesignHeight)
    translationX = (dotX(i) - centerX(i)) * (1f - e) * density
    translationY = (dotY(i) - centerY(i)) * (1f - e) * density
    alpha = slotIn(e)
}

/** The slots and their icons come in over the middle of the opening (and go there when it closes). */
private fun slotIn(e: Float) = ((e - 0.3f) / 0.4f).coerceIn(0f, 1f)

/** The ring, the glow and the hints come in last. */
private fun lateIn(e: Float) = ((e - 0.6f) / 0.4f).coerceIn(0f, 1f)

/**
 * The menu's panel at [progress]: laid out each frame (no recomposition) from the launcher tile's rounded square to the
 * panel, its fill and outline going from the tile's to the panel's, with the tile's nine dots spreading toward the slots and fading.
 */
private fun Modifier.morphingMenuPanel(progress: () -> Float): Modifier = this
    .layout { measurable, constraints ->
        val e = progress()
        val cx = TileCenterX + (PanelX + PanelW / 2f - TileCenterX) * e
        val cy = TileCenterY + (PanelY + PanelH / 2f - TileCenterY) * e
        val w = TileSide + (PanelW - TileSide) * e
        val h = TileSide + (PanelH - TileSide) * e
        val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w.dp.roundToPx(), h.dp.roundToPx()))
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place((cx - w / 2f).dp.roundToPx(), (cy - h / 2f).dp.roundToPx()) }
    }
    .drawBehind {
        val e = progress()
        val corner = (TileCorner + (PanelCorner - TileCorner) * e).dp.toPx()
        drawRoundRect(androidx.compose.ui.graphics.lerp(TileSurfaceFocused, MenuPanelFill, e), cornerRadius = CornerRadius(corner))
        drawFadingOutline(
            corner,
            (15f + (28f - 15f) * e).dp.toPx(),
            UnfocusedOutline + (BackgroundOutline - UnfocusedOutline) * e,
            gain = 1f
        )
        // The dots, spreading from the tile's middle toward their slots as they fade out.
        val dotsAlpha = (1f - e / 0.45f).coerceIn(0f, 1f)
        if (dotsAlpha > 0f) {
            val left = this@drawBehind.size.width / 2f
            val top = this@drawBehind.size.height / 2f
            for (i in 0 until MoreMenuEntries) {
                val dx = (dotX(i) - TileCenterX) + ((centerX(i) - (PanelX + PanelW / 2f)) - (dotX(i) - TileCenterX)) * e
                val dy = (dotY(i) - TileCenterY) + ((centerY(i) - (PanelY + PanelH / 2f)) - (dotY(i) - TileCenterY)) * e
                drawCircle(LauncherDotColor.copy(alpha = dotsAlpha), DotRadius.dp.toPx(), Offset(left + dx.dp.toPx(), top + dy.dp.toPx()))
            }
        }
    }

private val LauncherDotColor = androidx.compose.ui.graphics.Color(0xFFE6E6E6)
