package com.minimaui.app.ui.favorites

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.getValue
import com.minimaui.app.ui.components.backgroundOutline
import com.minimaui.app.ui.components.UnfocusedOutline
import com.minimaui.app.ui.components.softOutline
import com.minimaui.app.ui.theme.TileFillIdle
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.Modifier
import com.minimaui.app.ui.theme.TileSurfaceFocused
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.minimaui.app.model.GameEntry
import com.minimaui.app.ui.components.EdgeArrowButton
import com.minimaui.app.ui.components.drawCornerBrackets
import com.minimaui.app.ui.components.EdgeDirection
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.key
import androidx.compose.ui.unit.Dp
import com.minimaui.app.ui.components.EaseInOut
import com.minimaui.app.ui.components.rememberPressScale
import com.minimaui.app.ui.components.FocusRing
import com.minimaui.app.ui.components.GameTile
import com.minimaui.app.ui.components.GlowRect
import com.minimaui.app.ui.components.PageDots
import com.minimaui.app.ui.components.LocalFocusPulse
import com.minimaui.app.ui.components.RetroidLogo
import com.minimaui.app.ui.components.TileSize
import com.minimaui.app.ui.components.ScreenLabel
import com.minimaui.app.ui.components.SelectionGlow
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.SurfacePanel

private val FavoritesDivider = Color(0xFF262626)

/**
 * The bento panel behind Favorites and the Recently Played view: tiles in [slotsFor], one page of
 * [entriesFor] per page (a null entry is an empty slot). In [editMode] brackets mark the focused tile.
 */
@Composable
fun BentoScreen(
    pageCount: Int,
    entriesFor: (page: Int) -> List<GameEntry?>,
    slotsFor: (page: Int) -> List<BentoSlot>,
    page: Int,
    slot: Int,
    editMode: Boolean,
    held: Boolean,
    label: String,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pressTick: Int = 0,
    logoAlpha: Float = 1f,
    onTap: (slot: Int) -> Unit = {},
    onSwipe: (delta: Int) -> Unit = {},
    onEditHold: (slot: Int) -> Unit = {},
    onEditDrag: (slot: Int) -> Unit = {},
    onEditEdge: (delta: Int) -> Unit = {},
    onEditRelease: () -> Unit = {},
    showLauncher: Boolean = false,
    viewLabel: String? = null,
    viewIcon: Int? = null,
) {
    val density = LocalDensity.current.density
    val pagerState = rememberPagerState(initialPage = page, pageCount = { maxOf(pageCount, 1) })

    LaunchedEffect(page) { pagerState.animateScrollToPage(page, animationSpec = tween(420, easing = EaseInOut)) }
    val currentPage by rememberUpdatedState(page)

    Box(
        modifier = modifier
            .fillMaxSize()
            // Touch: tap a tile to launch it, swipe left/right to change page (the owner ignores both in Edit Mode).
            .pointerInput(page) {
                detectTapGestures { offset ->
                    val x = offset.x / density - Dimens.PanelX.value
                    val y = offset.y / density - Dimens.PanelY.value
                    val entries = entriesFor(page)
                    slotsFor(page).indexOfFirst { s ->
                        x >= s.x.value && x <= s.x.value + s.width.value && y >= s.y.value && y <= s.y.value + s.height.value
                    }.takeIf { it >= 0 && (entries.getOrNull(it) != null || (showLauncher && page == 0 && it == LauncherSlot)) }?.let(onTap)
                }
            }
            // Touch: swipe left or right and let go; the page then slides over (the owner ignores it in Edit Mode).
            .pointerInput(editMode) {
                if (editMode) return@pointerInput
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (abs(total) > SwipeDistance * density) onSwipe(if (total < 0f) 1 else -1) },
                ) { _, drag -> total += drag }
            }
            // Touch, Edit Mode: hold a tile to pick it up, drag it over the others (near the panel's side edge the page turns), let go to put it down.
            .pointerInput(editMode) {
                if (!editMode) return@pointerInput
                fun slotAt(position: Offset): Int {
                    val x = position.x / density - Dimens.PanelX.value
                    val y = position.y / density - Dimens.PanelY.value
                    return slotsFor(currentPage).indexOfFirst { s ->
                        x >= s.x.value && x <= s.x.value + s.width.value && y >= s.y.value && y <= s.y.value + s.height.value
                    }
                }
                detectDragGesturesAfterLongPress(
                    onDragStart = { position -> slotAt(position).takeIf { it >= 0 }?.let(onEditHold) },
                    onDrag = { change, _ ->
                        change.consume()
                        slotAt(change.position).takeIf { it >= 0 }?.let(onEditDrag)
                        val x = change.position.x / density - Dimens.PanelX.value
                        if (x < EditEdgeZone) onEditEdge(-1) else if (x > Dimens.PanelWidth.value - EditEdgeZone) onEditEdge(1)
                    },
                    onDragEnd = { onEditRelease() },
                    onDragCancel = { onEditRelease() },
                )
            }
    ) {
        val panelShape = RoundedCornerShape(Dimens.FavoritesPanelCorner)
        Box(
            modifier = Modifier
                .offset(Dimens.PanelX, Dimens.PanelY)
                .size(Dimens.PanelWidth, Dimens.PanelHeight)
                .clip(panelShape)
                .background(SurfacePanel)
                .backgroundOutline(depth = 24.dp, corner = Dimens.FavoritesPanelCorner)
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                // The neighbouring pages are already composed (and their covers loaded) when a slide starts.
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // While the pages slide, everything fades out toward the panel's left and right edges, into the panel's own background.
                        val moving = (abs(pagerState.currentPageOffsetFraction) * 5f).coerceIn(0f, 1f)
                        val fade = (EdgeFadeAtRest + (EdgeFadeMoving - EdgeFadeAtRest) * moving).dp.toPx()
                        if (moving > 0f) {
                            drawRect(
                                Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    fade / size.width to Color.Black,
                                    1f - fade / size.width to Color.Black,
                                    1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
            ) { pageIndex ->
                BentoPage(
                    entries = entriesFor(pageIndex),
                    slots = slotsFor(pageIndex),
                    focusedSlot = if (pageIndex == page) slot else -1,
                    editMode = editMode,
                    held = held,
                    pressTick = pressTick,
                    launcher = showLauncher && pageIndex == 0,
                    dimLauncher = editMode
                )
            }
            if (pageCount > 1) {
                PageDots(
                    count = pageCount,
                    current = page,
                    vertical = false,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = 799.dp),
                    activeSize = 18.dp,
                    inactiveSize = 14.dp,
                    gap = 16.dp
                )
            }
        }

        // Drawn outside the clipped panel, so the brackets can overlap the panel's edge at the corners.
        if (editMode && entriesFor(page).getOrNull(slot) != null) {
            FocusBrackets(
                slot = slotsFor(page)[slot],
                held = held,
                originX = Dimens.PanelX.value,
                originY = Dimens.PanelY.value
            )
        }

        // An arrow only shows when there is a page to go to in that direction; it fades in and out. Each is centred between
        // the panel's edge and the edge of the screen.
        val arrowPad = ((Dimens.PanelX.value - 62f) / 2f).dp
        AnimatedVisibility(
            visible = page > 0,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = arrowPad)
        ) {
            EdgeArrowButton(direction = EdgeDirection.Left, enabled = true, onClick = { onPageChange(page - 1) })
        }
        AnimatedVisibility(
            visible = page < pageCount - 1,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = arrowPad)
        ) {
            EdgeArrowButton(direction = EdgeDirection.Right, enabled = true, onClick = { onPageChange(page + 1) })
        }

        // Edit Mode keeps its place at the bottom left; a folder's (or Recently Played's) name is in the middle, level with the hints.
        if (editMode) {
            ScreenLabel(
                text = label,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 34.dp, bottom = Dimens.HintBarBottom)
            )
        }
        if (viewLabel != null) {
            ScreenLabel(
                text = viewLabel,
                fontSize = 44.sp,
                centered = true,
                dash = false,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Dimens.HintBarBottom)
                    .widthIn(max = 760.dp)
            )
        } else if (!editMode) {
            // Hidden while the boot logo is still travelling to this spot.
            if (logoAlpha > 0f) RetroidLogo(alpha = logoAlpha, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Dimens.HintBarBottom))
        }
    }
}

@Composable
private fun BentoPage(
    entries: List<GameEntry?>,
    slots: List<BentoSlot>,
    focusedSlot: Int,
    editMode: Boolean,
    held: Boolean,
    pressTick: Int,
    launcher: Boolean = false,
    dimLauncher: Boolean = false,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Faint line separating the two halves of the panel; part of the page, so it slides with the tiles.
        Box(
            modifier = Modifier
                .offset(846.dp, 101.dp)
                .size(3.dp, 650.dp)
                .background(FavoritesDivider)
        )
        // The launcher (the nine-dot tile that opens the launcher menu) can be focused like a tile, and gets the same soft
        // outline as the covers.
        val onLauncher = launcher && !dimLauncher && focusedSlot == LauncherSlot
        val focusRect = slots.getOrNull(focusedSlot)?.takeIf { entries.getOrNull(focusedSlot) != null || onLauncher }?.let {
            GlowRect(it.x.value, it.y.value, it.width.value, it.height.value, it.size.corner.value)
        }
        // Behind the tiles, so it bleeds through their translucent fills into the neighbours.
        SelectionGlow(focusRect, adaptiveBlur = true)
        // Over the glow, so it doesn't shine through the tile.
        if (launcher) {
            val s = slots[LauncherSlot]
            LauncherTile(s, focused = onLauncher, pressTick = pressTick)
        }
        // A tile that left this page (removed, or moved off it) shrinks and fades where it was.
        val before = remember { arrayOf(entries) }
        val leaving = remember(entries) {
            val ids = entries.mapNotNull { it?.id }.toSet()
            val gone = before[0].mapIndexedNotNull { i, g -> if (g != null && g.id !in ids) g to i else null }
            before[0] = entries
            gone
        }
        for ((gone, index) in leaving) key(gone.id) {
            slots.getOrNull(index)?.let { LeavingTile(gone, it, pressTick) }
        }
        entries.forEachIndexed { i, game ->
            if (game == null) return@forEachIndexed
            // Keyed by game, so when tiles are reordered or removed each one glides to its new slot.
            key(game.id) {
                val slot = slots[i]
                val glide = tween<Dp>(durationMillis = 260, easing = EaseInOut)
                val x by animateDpAsState(slot.x, glide, label = "tileX")
                val y by animateDpAsState(slot.y, glide, label = "tileY")
                val width by animateDpAsState(slot.width, glide, label = "tileW")
                val height by animateDpAsState(slot.height, glide, label = "tileH")
                GameTile(
                    title = game.title,
                    coverUri = game.coverUri,
                    tileSize = slot.size,
                    size = DpSize(width, height),
                    focused = i == focusedSlot,
                    editMode = editMode,
                    held = held && i == focusedSlot,
                    pressTick = pressTick,
                    modifier = Modifier
                        .offset(x, y)
                        .size(width, height)
                )
            }
        }
        // On top of the tiles; grows with a picked-up or pressed tile.
        val lift by animateFloatAsState(if (held) 1.05f else 1f, tween(160), label = "ringLift")
        val press = rememberPressScale(pressTick, true)
        val focusedSize = slots.getOrNull(focusedSlot)?.size
        // The soft white-to-transparent outline (deeper on bigger tiles), like the XMB covers and the Apps page.
        val fadeDepth = when (focusedSize) {
            TileSize.Large -> 28.dp
            TileSize.Medium -> 21.dp
            else -> 15.dp
        }
        FocusRing(focusRect, scale = { lift * press.value * focusScaleFor(focusedSize) }, fadeDepth = fadeDepth)
    }
}

/** A tile that was taken off the page: it shrinks a little and fades out in its place. */
@Composable
private fun LeavingTile(game: GameEntry, slot: BentoSlot, pressTick: Int) {
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) { com.minimaui.app.ui.components.withHalfRateFrames { progress.animateTo(0f, tween(240, easing = EaseInOut)) } }
    if (progress.value <= 0f) return
    GameTile(
        title = game.title,
        coverUri = game.coverUri,
        tileSize = slot.size,
        size = DpSize(slot.width, slot.height),
        focused = false,
        pressTick = pressTick,
        modifier = Modifier
            .offset(slot.x, slot.y)
            .size(slot.width, slot.height)
            .graphicsLayer {
                alpha = progress.value
                val s = 0.85f + 0.15f * progress.value
                scaleX = s
                scaleY = s
            }
    )
}

/** The slot of the first page that holds the launcher tile (the bottom right small one). */
const val LauncherSlot = 7

/** The tile with four dots that opens the Folder / Recently Played menu. */
@Composable
private fun LauncherTile(slot: BentoSlot, focused: Boolean, pressTick: Int) {
    val press = rememberPressScale(pressTick, focused)
    val grow by animateFloatAsState(if (focused) focusScaleFor(slot.size) else 1f, tween(200), label = "launcherGrow")
    val shape = RoundedCornerShape(slot.size.corner)
    val outlineStrength by animateFloatAsState(if (focused) 0f else UnfocusedOutline, tween(200), label = "launcherOutline")
    Box(
        modifier = Modifier
            .offset(slot.x, slot.y)
            .size(slot.width, slot.height)
            .graphicsLayer {
                scaleX = grow * press.value
                scaleY = grow * press.value
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
            .clip(shape)
            .background(if (focused) TileSurfaceFocused else TileFillIdle)
            // The same soft outline as the covers beside it.
            .softOutline(depth = 15.dp, corner = slot.size.corner) { outlineStrength },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(80.dp)) {
            val r = 6.3.dp.toPx()
            val c = size.width / 2f
            val o = 25.dp.toPx()
            for (dx in listOf(-o, 0f, o)) for (dy in listOf(-o, 0f, o)) drawCircle(LauncherDots, r, Offset(c + dx, c + dy))
        }
    }
}

private val LauncherDots = Color(0xFFE6E6E6)
private val BracketIdle = Color(0xFFB4B4B4)
private const val HeldScale = 1.05f

/** The focused tile is a touch larger than the rest (the ring follows). */
const val FocusedTileScale = 1.03f

/** The large tile grows 10% less than the others (it is already big). */
fun focusScaleFor(size: TileSize?): Float = if (size == TileSize.Large) 1f + (FocusedTileScale - 1f) * 0.9f else FocusedTileScale

/**
 * Four corner marks around the focused tile in Edit Mode. They glide between tiles, grow with the tile when
 * it is picked up (so they never crowd it) and turn from light gray to white while it is being moved.
 */
@Composable
private fun FocusBrackets(slot: BentoSlot, held: Boolean, originX: Float, originY: Float) {
    val spec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
    val x by animateFloatAsState(slot.x.value, spec, label = "bracketX")
    val y by animateFloatAsState(slot.y.value, spec, label = "bracketY")
    val width by animateFloatAsState(slot.width.value, spec, label = "bracketW")
    val height by animateFloatAsState(slot.height.value, spec, label = "bracketH")
    val pulse = LocalFocusPulse.current
    val scale by animateFloatAsState((if (held) HeldScale else 1f) * focusScaleFor(slot.size), tween(160), label = "bracketScale")
    Canvas(modifier = Modifier.fillMaxSize()) {
        // The marks are as thick as the focus outline and curve concentrically with the tile's own corners.
        val strokeWidth = Dimens.FocusedBorderWidth.toPx()
        val gap = 14.dp.toPx() + strokeWidth / 2f
        // Shorter arms on smaller tiles, so the marks never crowd them.
        val arm = when (slot.size) {
            TileSize.Large -> 26.dp
            TileSize.Medium -> 28.dp
            TileSize.Small -> 8.dp
        }.toPx()
        val radius = (slot.size.corner.toPx() * scale + gap).coerceAtLeast(strokeWidth)
        val centerX = (originX + x + width / 2f).dp.toPx()
        val centerY = (originY + y + height / 2f).dp.toPx()
        val halfW = width.dp.toPx() * scale / 2f + gap
        val halfH = height.dp.toPx() * scale / 2f + gap
        val left = centerX - halfW
        val right = centerX + halfW
        val top = centerY - halfH
        val bottom = centerY + halfH
        // The same slow fade in and out as the focus outline.
        val color = (if (held) Color.White else BracketIdle).copy(alpha = pulse())
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        drawCornerBrackets(left, top, right, bottom, radius, arm, stroke, color)
    }
}

/** How far from the panel's side edges the covers fade out while the pages slide (nothing fades at rest). */
private const val EdgeFadeAtRest = 40f
private const val EdgeFadeMoving = 260f
private const val SwipeDistance = 100f
private const val EditEdgeZone = 90f
