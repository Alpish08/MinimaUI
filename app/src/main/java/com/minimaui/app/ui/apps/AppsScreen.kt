package com.minimaui.app.ui.apps

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.getValue
import com.minimaui.app.ui.components.unfocusedOutline
import com.minimaui.app.ui.components.LocalArrival
import com.minimaui.app.ui.components.OverlayMotion
import com.minimaui.app.ui.components.cascade
import com.minimaui.app.ui.components.backgroundOutline
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.key
import androidx.compose.ui.unit.IntOffset
import com.minimaui.app.ui.components.EaseInOut
import com.minimaui.app.ui.components.progressiveBlur
import com.minimaui.app.ui.components.rememberPressScale
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.minimaui.app.ui.components.FocusedOutlineGain
import com.minimaui.app.ui.components.UnfocusedOutline
import com.minimaui.app.ui.components.softOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaui.app.model.AppEntry
import com.minimaui.app.model.AppFolder
import com.minimaui.app.model.AppTile
import com.minimaui.app.model.AppTileApp
import com.minimaui.app.ui.components.FocusRing
import com.minimaui.app.ui.components.GlowRect
import com.minimaui.app.ui.components.MarqueeText
import com.minimaui.app.ui.components.PageDots
import com.minimaui.app.ui.components.ScreenLabel
import com.minimaui.app.ui.components.SelectionGlow
import com.minimaui.app.ui.components.AppsWaveBrightness
import com.minimaui.app.ui.components.WaveOriginal
import com.minimaui.app.ui.components.XmbWave
import com.minimaui.app.ui.components.rememberCover
import com.minimaui.app.ui.favorites.BentoLayouts
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.TextPrimary


/**
 * The Apps page: round icons with their names, ten to a page (rows of 3, 4 and 3), pages stacked vertically. Apps and folders
 * (glass bubbles holding up to four small icons) sit freely in the slots. Empty slots show only while [editMode] is on, as
 * empty bubbles. With a [folder] open, the page zooms into its bubble, which grows into a rounded panel holding the folder's
 * ten slots in the same layout.
 *
 * [pageTiles] gives a page's ten tiles as they are to be shown; while something is [carrying] it is shown under the cursor
 * and the swap it would make is shown too.
 */
@Composable
fun AppsScreen(
    pageTiles: (page: Int) -> List<AppTile?>,
    page: Int,
    slot: Int,
    pageCount: Int,
    editMode: Boolean,
    carrying: Boolean,
    folder: AppFolder?,
    folderTiles: List<AppTile?>,
    folderSlot: Int,
    modifier: Modifier = Modifier,
    /** The folder of the focused bubble: its panel is built ahead (invisible), so opening it only has to animate. */
    preparedFolder: AppFolder? = null,
    wave: Int = WaveOriginal,
    ribbon: Boolean = false,
    pressTick: Int = 0,
    onTap: (slot: Int) -> Unit = {},
    onLongPress: () -> Unit = {},
    onTapOutsideFolder: () -> Unit = {},
    onSwipe: (delta: Int) -> Unit = {},
    onEditHold: (slot: Int) -> Unit = {},
    onEditDrag: (slot: Int) -> Unit = {},
    onEditEdge: (delta: Int) -> Unit = {},
    onEditRelease: () -> Unit = {},
) {
    val density = LocalDensity.current.density
    val folderOpen = folder != null
    // The folder last opened stays drawn while it closes; it grows out of (and back into) its bubble's slot.
    val lastFolder = remember { arrayOf<AppFolder?>(null) }
    val lastTiles = remember { arrayOf<List<AppTile?>>(emptyList()) }
    val origin = remember { intArrayOf(0) }
    if (folder != null) {
        if (lastFolder[0]?.id != folder.id) origin[0] = slot
        lastFolder[0] = folder
        lastTiles[0] = folderTiles
    }
    val open = remember { androidx.compose.animation.core.Animatable(if (folderOpen) 1f else 0f) }
    // Read in composition only as a yes/no, so the animation itself never recomposes the page.
    val opening by remember { androidx.compose.runtime.derivedStateOf { open.value > 0f } }
    if (folder == null && !opening && preparedFolder != null) origin[0] = slot
    LaunchedEffect(folderOpen) { open.animateTo(if (folderOpen) 1f else 0f, tween(FolderOpenMotion.Millis, easing = EaseInOut)) }
    val currentFolderOpen by androidx.compose.runtime.rememberUpdatedState(folderOpen)
    val currentPageTiles by androidx.compose.runtime.rememberUpdatedState(pageTiles(page))
    val currentFolderTiles by androidx.compose.runtime.rememberUpdatedState(folderTiles)
    fun slotAt(position: Offset): Int {
        var x = position.x / density
        var y = position.y / density
        if (currentFolderOpen) {
            // The folder's slots are the page's, scaled down around the panel's middle: back to page coordinates.
            x = FolderCenterX + (x - FolderCenterX) / FolderLayoutScale
            y = PageLayoutCenterY + (y - FolderCenterY) / FolderLayoutScale
        }
        return BentoLayouts.Apps.indexOfFirst { slot ->
            val dx = x - (slot.x.value + slot.width.value / 2f)
            val dy = y - (slot.y.value + slot.height.value / 2f)
            dx * dx + dy * dy <= TapRadius * TapRadius
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            // Touch: tap an icon to launch it (or a bubble to open it), tap outside an open folder to close it, swipe up/down to change page.
            .pointerInput(editMode) {
                detectTapGestures(
                    // Outside Edit Mode, holding an app or a folder turns Edit Mode on.
                    onLongPress = if (editMode) null else { offset ->
                        val s = slotAt(offset)
                        val tiles = if (currentFolderOpen) currentFolderTiles else currentPageTiles
                        if (s >= 0 && tiles.getOrNull(s) != null) onLongPress()
                    },
                ) { offset ->
                    if (currentFolderOpen && !insideFolderPanel(offset.x / density, offset.y / density)) {
                        onTapOutsideFolder()
                        return@detectTapGestures
                    }
                    slotAt(offset).takeIf { it >= 0 }?.let(onTap)
                }
            }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (abs(total) > SwipeDistance * density) onSwipe(if (total < 0f) 1 else -1)
                    },
                ) { _, drag -> total += drag }
            }
            // Touch, Edit Mode: hold an icon to pick it up, drag it over the slots (near the top or bottom the page turns), let go to put it down.
            .pointerInput(editMode) {
                if (!editMode) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { position -> slotAt(position).takeIf { it >= 0 }?.let(onEditHold) },
                    onDrag = { change, _ ->
                        change.consume()
                        slotAt(change.position).takeIf { it >= 0 }?.let(onEditDrag)
                        val y = change.position.y / density
                        if (y < 110f) onEditEdge(-1) else if (y > Dimens.DesignHeight - 110f) onEditEdge(1)
                    },
                    onDragEnd = { onEditRelease() },
                    onDragCancel = { onEditRelease() },
                )
            }
    ) {
        // The same wave as behind All Games, a little blurred and dimmer so the icons stay in front.
        XmbWave(style = wave, soft = { 3f }, brightness = { AppsWaveBrightness * (1f - 0.65f * open.value) }, modifier = Modifier.fillMaxSize())
        // Opening a folder zooms into its bubble: the page grows a little around it, moving the other icons aside, while
        // they quickly fade and blur away (see FolderOpenMotion).
        val bubble = BentoLayouts.Apps.getOrNull(origin[0]) ?: BentoLayouts.Apps[0]
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = open.value
                    val zoom = FolderOpenMotion.zoom(t)
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(bubble.centerX / Dimens.DesignWidth, bubble.centerY / Dimens.DesignHeight)
                    alpha = FolderOpenMotion.alpha(t)
                }
        ) {
            // Pages stack vertically, so a page change slides the icons up or down (eased) while they blur and fade.
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val down = targetState > initialState
                    val slide = tween<IntOffset>(380, easing = EaseInOut)
                    (slideInVertically(slide) { if (down) it / 5 else -it / 5 } + fadeIn(tween(300, easing = EaseInOut))) togetherWith
                        (slideOutVertically(slide) { if (down) -it / 5 else it / 5 } + fadeOut(tween(240, easing = EaseInOut)))
                },
                label = "appsPage"
            ) { shownPage ->
                val blur by transition.animateFloat(transitionSpec = { tween(300, easing = EaseInOut) }, label = "appsBlur") {
                    if (it == EnterExitState.Visible) 0f else 1f
                }
                Box(Modifier.fillMaxSize().progressiveBlur(14.dp) { blur }) {
                    TileGrid(
                        tiles = pageTiles(shownPage),
                        focusedSlot = if (shownPage == page && !folderOpen) slot else -1,
                        editMode = editMode,
                        held = carrying && !folderOpen,
                        pressTick = pressTick,
                        backdrop = ribbon,
                        arrival = LocalArrival.current,
                        // The opening folder's own bubble hands over to the panel growing out of it (else it would zoom
                        // up behind the see-through panel too).
                        fadedSlot = if (shownPage == page && (folderOpen || opening)) origin[0] else -1,
                        fadedAlpha = { 1f - (open.value / 0.3f).coerceIn(0f, 1f) },
                    )
                }
            }

            if (pageCount > 1) {
                PageDots(
                    count = pageCount,
                    current = page,
                    vertical = true,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 33.dp),
                    activeSize = 22.dp,
                    inactiveSize = 11.dp,
                    gap = 18.dp
                )
            }
        }

        // The open (or closing) folder; otherwise the focused bubble's, built ahead and drawn at no opacity.
        val shownFolder = if (folderOpen || opening) lastFolder[0] else preparedFolder
        if (shownFolder != null) {
            FolderPanel(
                folder = shownFolder,
                tiles = when {
                    folderOpen -> folderTiles
                    opening -> lastTiles[0]
                    else -> shownFolder.apps.map { app -> app?.let(::AppTileApp) }
                },
                focusedSlot = if (folderOpen) folderSlot else -1,
                originSlot = origin[0],
                progress = { open.value },
                opening = folderOpen,
                prepared = !folderOpen && !opening,
                editMode = editMode,
                held = carrying && folderOpen,
                pressTick = pressTick,
            )
        }

        // The Edit Mode label comes into focus when the mode starts and blurs away when it ends.
        AnimatedVisibility(
            visible = editMode,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 46.dp, bottom = Dimens.HintBarBottom)
        ) {
            OverlayMotion { ScreenLabel(text = "Edit Mode", fontSize = 41.5.sp) }
        }
    }
}

/**
 * An open folder: a rounded panel (flat top and bottom, round ends) that grows out of the folder's bubble in [originSlot] as
 * [progress] goes to 1, with the folder's name on top and its ten slots in the page's layout, a little smaller.
 */
@Composable
private fun FolderPanel(
    folder: AppFolder,
    tiles: List<AppTile?>,
    focusedSlot: Int,
    originSlot: Int,
    progress: () -> Float,
    opening: Boolean,
    prepared: Boolean,
    editMode: Boolean,
    held: Boolean,
    pressTick: Int,
) {
    val from = BentoLayouts.Apps.getOrNull(originSlot) ?: BentoLayouts.Apps[0]
    val shape = RoundedCornerShape(FolderCorner.dp)
    Box(
        Modifier
            .fillMaxSize()
            .folderGrowth(from, progress) { t ->
                // Opening, it comes in while the page is still fading (no empty moment); closing, it is gone before the page's
                // icons come back, so its outline never lies over them.
                if (opening) ((t - FolderPanelInOpening) / 0.3f).coerceIn(0f, 1f) else ((t - FolderPanelIn) / 0.25f).coerceIn(0f, 1f)
            }
    ) {
        Box(
            Modifier
                .offset((FolderCenterX - FolderWidth / 2f).dp, (FolderCenterY - FolderHeight / 2f).dp)
                .size(FolderWidth.dp, FolderHeight.dp)
                .clip(shape)
                .background(FolderPanelFill)
                .backgroundOutline(depth = FolderOutlineDepth, corner = FolderCorner.dp)
        )
    }
    // The name and the slots come in once the panel has mostly grown, moving and growing out of the bubble with it.
    Box(
        Modifier.fillMaxSize().folderGrowth(from, progress) { t ->
            when {
                // Ready behind a focused bubble: drawn faintly enough not to show, so its icons are already on the GPU when it opens.
                prepared -> FolderWarmAlpha
                opening -> ((t - FolderPanelInOpening - 0.05f) / 0.4f).coerceIn(0f, 1f)
                else -> ((t - FolderPanelIn - 0.05f) / 0.4f).coerceIn(0f, 1f)
            }
        }
    ) {
        Box(
            Modifier.offset(0.dp, (FolderTitleY - 30f).dp).size(Dimens.DesignWidth.dp, 60.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(
                text = folder.name,
                color = TextPrimary.copy(alpha = 0.6f),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 40.sp,
                maxLines = 1,
            )
        }
        // The page's layout, scaled down around the panel's middle.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = FolderLayoutScale
                    scaleY = FolderLayoutScale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(FolderCenterX / Dimens.DesignWidth, PageLayoutCenterY / Dimens.DesignHeight)
                    translationY = (FolderCenterY - PageLayoutCenterY) * density
                }
        ) {
            TileGrid(tiles = tiles, focusedSlot = focusedSlot, editMode = editMode, held = held, pressTick = pressTick, backdrop = false, arrival = { 1f })
        }
    }
}

/**
 * The panel (and its contents) growing in as the folder opens ([progress] 0 to 1): from [FolderStartScale] of its size,
 * starting a little toward the [bubble] it opens from, to its full size and place, shown at [alpha] of the progress. (Growing
 * from the bubble's own size would cover the most ground in the first few frames, which shows as a jump.)
 */
private fun Modifier.folderGrowth(bubble: com.minimaui.app.ui.favorites.BentoSlot, progress: () -> Float, alpha: (Float) -> Float): Modifier =
    graphicsLayer {
        val t = progress()
        val e = EaseInOut.transform(t)
        this.alpha = alpha(t)
        val s = FolderStartScale + (1f - FolderStartScale) * e
        scaleX = s
        scaleY = s
        translationX = (bubble.centerX - FolderCenterX) * FolderStartDrift * (1f - e) * density
        translationY = (bubble.centerY - FolderCenterY) * FolderStartDrift * (1f - e) * density
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(FolderCenterX / Dimens.DesignWidth, FolderCenterY / Dimens.DesignHeight)
    }

/** Whether a point (design px) is on the open folder's panel. */
private fun insideFolderPanel(x: Float, y: Float): Boolean {
    val dx = abs(x - FolderCenterX) - (FolderWidth / 2f - FolderCorner)
    val dy = abs(y - FolderCenterY) - (FolderHeight / 2f - FolderCorner)
    return if (dx <= 0f) abs(y - FolderCenterY) <= FolderHeight / 2f else dx * dx + maxOf(dy, 0f) * maxOf(dy, 0f) <= FolderCorner * FolderCorner
}

/** Ten slots (a page, or an open folder). Tiles are keyed, so a move or a swap glides them to their new slots. */
@Composable
private fun TileGrid(
    tiles: List<AppTile?>,
    focusedSlot: Int,
    editMode: Boolean,
    held: Boolean,
    pressTick: Int,
    backdrop: Boolean,
    arrival: () -> Float,
    fadedSlot: Int = -1,
    fadedAlpha: () -> Float = { 1f },
) {
    val slots = BentoLayouts.Apps
    // An icon that left (removed, or moved off the page) shrinks and fades where it was.
    val before = remember { arrayOf(tiles) }
    val leaving = remember(tiles) {
        val keys = tiles.mapNotNull { it?.key }.toSet()
        val gone = before[0].mapIndexedNotNull { i, t -> if (t is AppTileApp && t.key !in keys) t.app to i else null }
        before[0] = tiles
        gone
    }
    Box(modifier = Modifier.fillMaxSize()) {
        val focusedTile = tiles.getOrNull(focusedSlot)
        // Empty slots (Edit Mode only) get the ring when focused too, and the plain menu-style glow: there is no cover to match.
        val focusRect = slots.getOrNull(focusedSlot)?.takeIf { focusedTile != null || editMode }?.let {
            GlowRect(it.x.value, it.y.value, it.width.value, it.height.value, it.width.value / 2f)
        }
        // While the screen comes in, the glow and the ring ride along with the focused icon: they fade in, rise and grow as it does.
        val glowIn = { if (focusedTile != null) cascade(arrival(), focusedSlot) else 1f }
        val withIcon = Modifier.fillMaxSize().graphicsLayer {
            val settled = glowIn()
            translationY = (1f - settled) * ArrivalRise.dp.toPx()
            val s = 0.9f + 0.1f * settled
            scaleX = s
            scaleY = s
            if (focusRect != null) {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    (focusRect.x + focusRect.width / 2f).dp.toPx() / size.width,
                    (focusRect.y + focusRect.height / 2f).dp.toPx() / size.height
                )
            }
        }
        Box(withIcon) {
            SelectionGlow(focusRect.takeIf { focusedTile != null }, intensity = glowIn, alpha = AppGlowAlpha)
            SelectionGlow(focusRect.takeIf { focusedTile != null }, intensity = glowIn, alpha = 0.3f, inflate = 40f)
        }
        SelectionGlow(focusRect.takeIf { focusedTile == null })
        slots.forEachIndexed { i, s ->
            val tile = tiles.getOrNull(i)
            val focused = i == focusedSlot
            key(tile?.key ?: "empty$i") {
                val glide = tween<Float>(durationMillis = 260, easing = EaseInOut)
                val x by animateFloatAsState(s.x.value, glide, label = "iconX")
                val y by animateFloatAsState(s.y.value, glide, label = "iconY")
                when (tile) {
                    is AppTileApp -> {
                        // The cover belongs to the app (not to the slot), so a moved icon never shows another one's picture for a moment.
                        val cover = rememberCover(tile.app.coverUri, IconSize.toInt())
                        AppIcon(tile.app.title, cover, x, y, i, arrival, focused = focused, held = held && focused, pressTick = pressTick, backdrop = backdrop)
                    }
                    is AppFolder -> FolderBubble(
                        tile, x, y, i, arrival, focused = focused, held = held && focused, pressTick = pressTick, backdrop = backdrop,
                        alpha = if (i == fadedSlot) fadedAlpha else NoFade,
                    )
                    null -> EmptySlot(x, y, editMode = editMode)
                }
            }
        }
        for ((gone, index) in leaving) key(gone.id) { LeavingIcon(gone, slots[index].x.value, slots[index].y.value) }
        // The outline glides between icons and grows with the focused icon.
        val ringScale by animateFloatAsState(
            when {
                focusedTile == null -> 1f
                held -> 1.2f
                else -> FocusedScale
            },
            tween(160),
            label = "ringScale"
        )
        val press = rememberPressScale(pressTick, true)
        // The same white-to-transparent, outside-in outline as the XMB covers; it also marks the focus in Edit Mode
        // and on an empty slot.
        Box(withIcon) {
            FocusRing(
                focusRect, color = Color.White, intensity = glowIn, scale = { ringScale * press.value }, fadeDepth = AppOutlineDepth,
                underStrength = if (focusedTile != null) UnfocusedOutline * FocusedOutlineGain else 0f
            )
        }
    }
}

/** The focused icon is a little larger than the rest, in Edit Mode or not. */
/** How far the apps' fading outline reaches into the circle (Apps page, edit mode and the app picker). */
internal val AppOutlineDepth = 34.dp

private const val FocusedScale = 1.12f
private const val IconSize = 212f
private const val ArrivalRise = 26f // how far below its place an icon starts when the screen comes in
private const val BackdropSize = 360f // the dark backdrop behind an unfocused icon (ribbon wave): solid under the icon, fading out to here
private const val TapRadius = 120f
private const val SwipeDistance = 100f
// A single layer can't go past fully opaque, so the extra brightness over 0.68 is a second, wider layer.
private const val AppGlowAlpha = 1f
private val PlaceholderIcon = Color(0xFF2A2A2A)

// The open folder (as designed): a 1360 x 940 panel with round ends, centred, its name near the top and the page's layout at
// 87% inside it; a quarter opaque, with the background outline reaching three times as far in as on other panels.
private const val FolderCenterX = 960f
private const val FolderCenterY = 541f
private const val FolderWidth = 1360f
private const val FolderHeight = 940f
private const val FolderCorner = FolderHeight / 2f
private val FolderOutlineDepth = 84.dp
private const val FolderTitleY = 148f
private const val FolderLayoutScale = 0.87f
private const val PageLayoutCenterY = 535f // the middle row of the page's layout
private val FolderPanelFill = Color(0x40141414)
// The panel only shows once the page has faded away (and, closing, is gone before the page's icons come back), so its
// outline never lies over them.
private val FolderPanelIn = FolderOpenMotion.FadeEnd
private const val FolderPanelInOpening = 0.2f
private const val FolderWarmAlpha = 0.003f
private const val FolderStartScale = 0.75f
private const val FolderStartDrift = 0.25f // how far toward the bubble the panel starts (a share of the way)

/**
 * How the page (and the status pill, which moves the same way) gets out of the way of an opening folder, as the opening goes
 * from 0 to 1: the same step in as launching a game or an app (the screen swells by [LaunchZoom] around the bubble as it
 * fades), over the first part of the opening, about as long as a launch's.
 */
internal object FolderOpenMotion {
    const val Millis = 420
    const val FadeEnd = 0.45f

    private fun away(t: Float) = (t / FadeEnd).coerceIn(0f, 1f)
    fun zoom(t: Float): Float = 1f + com.minimaui.app.ui.components.LaunchZoom * EaseInOut.transform(away(t))
    fun alpha(t: Float): Float = 1f - away(t)
}
// The small icons in a closed folder's bubble, as the Vita packs them: the folder's layout squeezed so that neighbours in a
// row are 62 apart and the rows 55 (diagonal neighbours 63), the middle row's ends and the corners running under the edge.
private const val MiniIconSize = 48f
private const val MiniGridScaleX = 62f / 367f // 367: between neighbours in a row of the page's layout
private const val MiniGridScaleY = 55f / 263f // 263: between its rows
// Full strength over most of the bubble, fading to 40% at its edge.
private val MiniEdgeFade = Brush.radialGradient(
    0f to Color.Black,
    0.68f to Color.Black,
    1f to Color.Black.copy(alpha = 0.4f),
)

@Composable
private fun AppIcon(title: String, cover: ImageBitmap?, x: Float, y: Float, index: Int, arrival: () -> Float, focused: Boolean, held: Boolean, pressTick: Int, backdrop: Boolean) {
    val press = rememberPressScale(pressTick, focused)
    val scale by animateFloatAsState(
        targetValue = when {
            held -> 1.2f
            focused -> FocusedScale
            else -> 1f
        },
        animationSpec = tween(if (held) 160 else 200),
        label = "appScale"
    )
    val labelShift by animateFloatAsState(if (focused) 16f else 0f, tween(200), label = "labelShift")
    // Every icon keeps the neutral soft outline; the focused one has the brighter, blinking ring over it.

    // With the ribbon wave behind the page, an unfocused icon sits on a soft dark backdrop that fades out around it (and
    // under its name), so the wave's lines don't run through it; the focused one has its glow instead.
    val backdropAlpha by animateFloatAsState(if (backdrop && !focused) 1f else 0f, tween(200), label = "iconBackdrop")
    if (backdropAlpha > 0f) IconBackdrop(x, y, index, arrival) { backdropAlpha }
    Box(
        modifier = Modifier
            .offset(x.dp, y.dp)
            .size(IconSize.dp)
            .graphicsLayer {
                val settled = cascade(arrival(), index)
                alpha = settled
                translationY = (1f - settled) * ArrivalRise.dp.toPx()
                scaleX = scale * press.value * (0.9f + 0.1f * settled)
                scaleY = scale * press.value * (0.9f + 0.1f * settled)
                // Rendered once into a texture: an unmoving icon (cover, outline) is then only composited each frame.
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .clip(CircleShape)
            .background(PlaceholderIcon)
            .softOutline(depth = AppOutlineDepth, gain = FocusedOutlineGain) { UnfocusedOutline }
    ) {
        cover?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
    IconLabel(title, x, y, index, arrival, focused, labelShift)
}

/** The name under an icon or bubble; it scrolls while focused. */
@Composable
private fun IconLabel(text: String, x: Float, y: Float, index: Int, arrival: () -> Float, focused: Boolean, labelShift: Float) {
    MarqueeText(
        text = text,
        color = TextPrimary,
        fontSize = 30.sp,
        scrolling = focused,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .offset((x + IconSize / 2f - 150f).dp, (y + IconSize + 6f + labelShift).dp)
            .size(300.dp, 42.dp)
            .graphicsLayer {
                val settled = cascade(arrival(), index)
                alpha = settled
                translationY = (1f - settled) * ArrivalRise.dp.toPx()
            }
    )
}

/**
 * A closed folder: a glass bubble (a faint fill that brightens toward the rim, and the icons' soft outline) holding its
 * apps as small, slightly see-through icons in their slots of the folder's layout, with the folder's name under it.
 */
@Composable
private fun FolderBubble(
    folder: AppFolder, x: Float, y: Float, index: Int, arrival: () -> Float, focused: Boolean, held: Boolean, pressTick: Int, backdrop: Boolean,
    alpha: () -> Float = NoFade,
) {
    val press = rememberPressScale(pressTick, focused)
    val scale by animateFloatAsState(
        targetValue = when {
            held -> 1.2f
            focused -> FocusedScale
            else -> 1f
        },
        animationSpec = tween(if (held) 160 else 200),
        label = "bubbleScale"
    )
    val labelShift by animateFloatAsState(if (focused) 16f else 0f, tween(200), label = "bubbleLabelShift")
    val backdropAlpha by animateFloatAsState(if (backdrop && !focused) 1f else 0f, tween(200), label = "bubbleBackdrop")
    if (backdropAlpha > 0f) IconBackdrop(x, y, index, arrival) { backdropAlpha }
    Box(
        modifier = Modifier
            .offset(x.dp, y.dp)
            .size(IconSize.dp)
            .graphicsLayer {
                val settled = cascade(arrival(), index)
                this.alpha = settled * alpha()
                translationY = (1f - settled) * ArrivalRise.dp.toPx()
                scaleX = scale * press.value * (0.9f + 0.1f * settled)
                scaleY = scale * press.value * (0.9f + 0.1f * settled)
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .clip(CircleShape)
            .background(BubbleGlass)
            .softOutline(depth = AppOutlineDepth, gain = FocusedOutlineGain) { UnfocusedOutline }
    ) {
        // The folder's apps in their own slots of its layout (rows of 3, 4 and 3), packed close like the Vita's: the outer
        // ones run under the bubble's edge, and they all fade a little toward it.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(MiniEdgeFade, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                }
        ) {
        folder.apps.forEachIndexed { i, app ->
            if (app == null) return@forEachIndexed
            val slot = BentoLayouts.Apps[i]
            val dx = (slot.centerX - FolderCenterX) * MiniGridScaleX
            val dy = (slot.centerY - PageLayoutCenterY) * MiniGridScaleY
            val cover = rememberCover(app.coverUri, MiniIconSize.toInt())
            Box(
                Modifier
                    .offset((IconSize / 2f + dx - MiniIconSize / 2f).dp, (IconSize / 2f + dy - MiniIconSize / 2f).dp)
                    .size(MiniIconSize.dp)
                    .clip(CircleShape)
                    .background(PlaceholderIcon)
            ) {
                cover?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
            }
        }
        }
    }
    Box(Modifier.graphicsLayer { this.alpha = alpha() }) { IconLabel(folder.name, x, y, index, arrival, focused, labelShift) }
}

private val NoFade = { 1f }

/** A bubble's glass: faint in the middle, brighter toward the rim (premultiplied white over whatever is behind). */
private val BubbleGlass = Brush.radialGradient(
    0f to Color.White.copy(alpha = 0.04f),
    0.72f to Color.White.copy(alpha = 0.07f),
    1f to Color.White.copy(alpha = 0.18f),
)

/** The soft dark backdrop behind an unfocused icon or bubble with the ribbon wave behind the page. */
@Composable
private fun IconBackdrop(x: Float, y: Float, index: Int, arrival: () -> Float, amount: () -> Float) {
    Box(
        modifier = Modifier
            .offset((x - (BackdropSize - IconSize) / 2f).dp, (y - (BackdropSize - IconSize) / 2f).dp)
            .size(BackdropSize.dp)
            .graphicsLayer { alpha = amount() * cascade(arrival(), index) }
            .background(
                Brush.radialGradient(
                    0f to Color.Black.copy(alpha = 0.3f),
                    0.58f to Color.Black.copy(alpha = 0.3f),
                    0.8f to Color.Black.copy(alpha = 0.12f),
                    1f to Color.Transparent,
                )
            )
    )
}

/** An app that was taken off the page: its icon shrinks a little and fades out in its place. */
@Composable
private fun LeavingIcon(app: AppEntry, x: Float, y: Float) {
    val cover = rememberCover(app.coverUri, IconSize.toInt())
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) { com.minimaui.app.ui.components.withHalfRateFrames { progress.animateTo(0f, tween(240, easing = EaseInOut)) } }
    if (progress.value <= 0f) return
    Box(
        modifier = Modifier
            .offset(x.dp, y.dp)
            .size(IconSize.dp)
            .graphicsLayer {
                alpha = progress.value
                val s = 0.85f + 0.15f * progress.value
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(PlaceholderIcon)
    ) {
        cover?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}

/** An empty slot: only in Edit Mode, as an empty bubble (an outline) that the cursor can stop on to add apps there. */
@Composable
private fun EmptySlot(x: Float, y: Float, editMode: Boolean) {
    if (!editMode) return
    Box(modifier = Modifier.offset(x.dp, y.dp).size(IconSize.dp).backgroundOutline(depth = 14.dp))
}
