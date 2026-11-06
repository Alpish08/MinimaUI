package com.minimaui.app.ui.allgames

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.runtime.key
import com.minimaui.app.ui.components.EaseInOut
import com.minimaui.app.ui.components.drawFadingOutline
import com.minimaui.app.ui.components.MarqueeText
import com.minimaui.app.ui.components.drawCornerBrackets
import com.minimaui.app.ui.components.ScreenLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.minimaui.app.ui.components.OverlayMotion
import androidx.compose.ui.layout.ContentScale
import com.minimaui.app.ui.components.rememberCover
import com.minimaui.app.ui.theme.PillSurface
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.minimaui.app.ui.components.LocalFocusPulse
import com.minimaui.app.ui.components.rememberTextWidth
import com.minimaui.app.ui.components.FocusedOutlineGain
import com.minimaui.app.ui.components.BackgroundOutline
import com.minimaui.app.ui.components.UnfocusedOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaui.app.model.GameEntry
import com.minimaui.app.model.Platform
import com.minimaui.app.ui.components.GlowRect
import com.minimaui.app.ui.components.SelectionGlow
import com.minimaui.app.ui.components.WaveOriginal
import com.minimaui.app.ui.components.XmbWave
import com.minimaui.app.ui.components.drawQuickTileOn
import com.minimaui.app.ui.components.rememberPressScale
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.pow


// A critically damped spring keeps its velocity when the target moves again, so holding a direction
// glides continuously instead of restarting a fixed-length animation on every step.
// The glide settles to within this much of a step (animateFloatAsState's default 0.01 is a few px of the row: it ended and snapped that far).
private const val GlideThreshold = 0.0005f
private val GlideSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f, visibilityThreshold = GlideThreshold)

/** A finger drag moves the selection one platform / game per this many dp. */
private const val PlatformDragStep = 150f
private const val GameDragStep = 130f
private const val DragStartDp = 20f
internal const val ListCenterX = 535f
private const val RowCenterY = 195f
// The edge dimming of the platform row covers this far above and below the row's centre (it must stay clear of the title).
private const val RowDimHalfHeight = 150f
private const val PlatformGap = 74f

// Each platform has its own tile shape at the selected size: the logo width below plus fixed padding,
// so wide logos (3DS) get a long thin tile and squarer ones (Wii U) a narrower, taller one.
// The whole platform row is drawn at this share of the sizes below (logo widths and paddings).
private const val RowScale = 0.8f
private const val TilePadX = 52f * RowScale
private const val TilePadY = 68f * RowScale
// Steam's tile is tighter vertically, so the gap above and below its circle matches the gap to its left.
private val TilePadYOverrides = mapOf("steam" to TilePadX)
// A logo nudged down inside its outline, in design px at the selected size.
private val LogoDown = emptyMap<String, Float>()
// The selected platform wears the look of an "on" quick-settings tile; its rim is this many times a quick tile's (the tile is bigger).
private const val QuickTileScale = 2.2f
private const val DefaultLogoWidth = 480f
private val LogoWidths = mapOf(
    "psp" to 504f,
    "steam" to 460f,
    "switch" to 480f,
    "android" to 480f,
    "vita" to 480f,
    "ps2" to 484f,
    "n3ds" to 520f,
    "wiiu" to 400f,
    "xbox" to 560f,
    "windows" to 480f,
    "ps1" to 484f,
    "ps3" to 440f,
    "gc" to 500f,
    "wii" to 380f,
    "n64" to 440f,
    "nds" to 560f,
    "gb" to 520f,
    "snes" to 440f,
    "dreamcast" to 520f,
    "xbox360" to 520f,
)

// Corner radius as a fraction of tile height, picked to echo each logo's own shape (1/2 = a pill).
private const val DefaultCornerRatio = 0.25f
private val CornerRatios = mapOf(
    "psp" to 0.20f,
    "ps2" to 0.20f,
    "windows" to 0.22f,
    "xbox" to 0.24f,
    "n3ds" to 0.24f,
    "vita" to 0.26f,
    "wiiu" to 0.38f,
    "switch" to 0.40f,
    "android" to 0.50f,
    "steam" to 0.50f,
    "ps1" to 0.20f,
    "ps3" to 0.22f,
    "gc" to 0.24f,
    "wii" to 0.30f,
    "n64" to 0.24f,
    "nds" to 0.22f,
    "gb" to 0.24f,
    "snes" to 0.24f,
    "dreamcast" to 0.20f,
    "xbox360" to 0.24f,
)

private fun logoWidth(platform: Platform) = (LogoWidths[platform.id] ?: DefaultLogoWidth) * RowScale

private fun tileWidth(platform: Platform) = logoWidth(platform) + 2 * TilePadX

/** Size multiplier for a tile [distance] places from the selected one; interpolated, so it glides. */
private fun scaleAt(distance: Float): Float {
    val d = abs(distance) // the tiles on both sides of the selected one shrink the same way
    return when {
        d < 1f -> 1f - d * 0.34f
        d < 2f -> 0.66f - (d - 1f) * 0.24f
        d < 3f -> 0.42f - (d - 2f) * 0.12f
        else -> 0.30f
    }
}

private fun alphaAt(distance: Float): Float = when {
    distance <= -1f -> 0.6f
    distance < 0f -> 0.6f + (distance + 1f) * 0.4f
    else -> 1f
}

/** Left edge of tile [index] when the row starts at x = 0 (tiles keep a fixed gap between them). */
private fun leftEdge(widths: FloatArray, position: Float, index: Int): Float {
    var x = 0f
    for (m in 0 until index) x += widths[m] * scaleAt(m - position) + PlatformGap
    return x
}

/**
 * Screen x of tile [index]: the row is shifted so the (possibly in-between) selected tile is
 * centred over the game list, whatever its width.
 */
private fun tileX(widths: FloatArray, position: Float, index: Int): Float {
    val k = position.toInt().coerceIn(0, widths.lastIndex)
    val t = (position - k).coerceIn(0f, 1f)
    val here = leftEdge(widths, position, k)
    val next = leftEdge(widths, position, k + 1)
    val selectedWidth = widths[k] + t * (widths[minOf(k + 1, widths.lastIndex)] - widths[k])
    return (ListCenterX - selectedWidth / 2f) - (here + t * (next - here)) + leftEdge(widths, position, index)
}

/** XMB-style browser: platforms across the top (D-pad left/right), that platform's games below (up/down). */
@Composable
fun AllGamesScreen(
    platforms: List<Platform>,
    platformIndex: Int,
    gamesFor: (Platform) -> List<GameEntry>,
    gameIndexFor: (Platform) -> Int,
    showPlus: (Platform) -> Boolean,
    modifier: Modifier = Modifier,
    /** The image the user picked for a platform's tile, if any (the built-in logo otherwise). */
    platformIcon: (Platform) -> String? = { null },
    /** A title the user gave a platform: its tile then shows that text instead of the built-in logo. */
    platformTitle: (Platform) -> String? = { null },
    wave: Int = WaveOriginal,
    ribbon: Boolean = false,
    /** The Minima wave fills the screen behind the page: the fades then take the content away instead of painting black. */
    coloredBackground: Boolean = false,
    pressTick: Int = 0,
    editMode: Boolean = false,
    held: Boolean = false,
    onTapPlatform: (Int) -> Unit = {},
    onTapGame: (Int) -> Unit = {},
    onSwipe: (dx: Int, dy: Int) -> Unit = { _, _ -> },
    onEditHoldPlatform: (Int) -> Unit = {},
    onEditDragPlatform: (Int) -> Unit = {},
    onEditRelease: () -> Unit = {},
) {
    val density = LocalDensity.current.density
    val platformPosition = animateFloatAsState(
        targetValue = platformIndex.toFloat(),
        animationSpec = GlideSpring,
        visibilityThreshold = GlideThreshold,
        label = "platformPosition"
    )
    val widths = remember(platforms) { FloatArray(platforms.size) { tileWidth(platforms[it]) } }
    val logos = platforms.map { key(it.id) { rememberPlatformLogo(it, platformIcon(it), platformTitle(it)) } }
    val heights = remember(platforms, logos) { FloatArray(platforms.size) { tileHeightFor(platforms[it], logos[it]) } }
    // While a platform is picked up in Edit Mode it grows a little and takes the glow and the blinking border; the game cover does not.
    val heldAmount = animateFloatAsState(if (editMode && held) 1f else 0f, tween(160), label = "platformHeld")
    val editAmount = animateFloatAsState(if (editMode) 1f else 0f, tween(200), label = "platformEdit")

    Box(
        modifier = modifier
            .fillMaxSize()
            // Touch: tap a platform to select it, tap a cover (the focused one launches), swipe to move along the row or the list.
            .pointerInput(platforms, platformIndex, gamesFor) {
                detectTapGestures { offset ->
                    val x = offset.x / density
                    val y = offset.y / density
                    val p = platformPosition.value
                    for (i in platforms.indices) {
                        val tileScale = scaleAt(i - p)
                        val left = tileX(widths, p, i)
                        if (x >= left && x <= left + widths[i] * tileScale && abs(y - RowCenterY) <= heights[i] * tileScale / 2f) {
                            onTapPlatform(i)
                            return@detectTapGestures
                        }
                    }
                    val platform = platforms.getOrNull(platformIndex) ?: return@detectTapGestures
                    val rows = gamesFor(platform).size + if (showPlus(platform)) 1 else 0
                    val selected = gameIndexFor(platform)
                    for (row in selected..minOf(selected + 2, rows - 1)) {
                        val d = (row - selected).toFloat()
                        val coverWidth = CoverWidth * slotValue(SlotScale, d)
                        val coverHeight = coverWidth * CoverAspect
                        if (abs(x - ListCenterX) <= coverWidth / 2f && abs(y - slotValue(SlotCenterY, d)) <= coverHeight / 2f) {
                            onTapGame(row)
                            return@detectTapGestures
                        }
                    }
                }
            }
            // Touch: drag along the row or down the list; the selection follows the finger a step at a time. In Edit Mode a long
            // press picks a platform up, and dragging it over the others moves it along the row.
            .pointerInput(editMode, platforms) {
                if (editMode) {
                    fun platformAt(position: Offset): Int? {
                        val x = position.x / density
                        val y = position.y / density
                        val p = platformPosition.value
                        return platforms.indices.firstOrNull { i ->
                            val tileScale = scaleAt(i - p)
                            val left = tileX(widths, p, i)
                            x >= left && x <= left + widths[i] * tileScale && abs(y - RowCenterY) <= heights[i] * tileScale / 2f
                        }
                    }
                    detectDragGesturesAfterLongPress(
                        onDragStart = { position -> platformAt(position)?.let(onEditHoldPlatform) },
                        onDrag = { change, _ ->
                            change.consume()
                            platformAt(change.position)?.let(onEditDragPlatform)
                        },
                        onDragEnd = { onEditRelease() },
                        onDragCancel = { onEditRelease() },
                    )
                } else {
                    var dx = 0f
                    var dy = 0f
                    var axis = 0 // 0: not decided yet, 1: along the row, 2: down the list
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f; axis = 0 },
                        onDragEnd = { axis = 0 },
                        onDragCancel = { axis = 0 },
                    ) { change, drag ->
                        change.consume()
                        dx -= drag.x
                        dy -= drag.y
                        if (axis == 0 && maxOf(abs(dx), abs(dy)) > DragStartDp * density) {
                            axis = if (abs(dx) >= abs(dy)) 1 else 2
                            if (axis == 1) dy = 0f else dx = 0f
                        }
                        // Dragging left or up moves on to the next platform or game.
                        if (axis == 1) {
                            val step = PlatformDragStep * density
                            while (dx >= step) { onSwipe(1, 0); dx -= step }
                            while (dx <= -step) { onSwipe(-1, 0); dx += step }
                        } else if (axis == 2) {
                            val step = GameDragStep * density
                            while (dy >= step) { onSwipe(0, 1); dy -= step }
                            while (dy <= -step) { onSwipe(0, -1); dy += step }
                        }
                    }
                }
            }
    ) {
        // The XMB wave, behind everything.
        XmbWave(style = wave, modifier = Modifier.fillMaxSize())

        // One game list per platform, sliding along with its tile. Only the selected platform and its
        // neighbours exist at a time: a column fades out as it slides away from the selected position.
        if (platforms.isNotEmpty()) Box(Modifier.fillMaxSize().fadeContent(coloredBackground) { listFadeBrush() }) {
            for (i in maxOf(platformIndex - 1, 0)..minOf(platformIndex + 1, platforms.lastIndex)) {
                val platform = platforms[i]
                key(platform.id) {
                    GameColumn(
                        index = i,
                        widths = widths,
                        heights = heights,
                        platformPosition = platformPosition,
                        games = gamesFor(platform),
                        showPlus = showPlus(platform),
                        selected = gameIndexFor(platform),
                        isCurrent = i == platformIndex,
                        pressTick = pressTick,
                        heldAmount = heldAmount,
                        backdrop = ribbon,
                        editMode = editMode
                    )
                }
            }
        }


        // The picked-up platform's glow, behind its tile.
        val glowRect = if (editMode && held && platforms.isNotEmpty()) {
            val i = platformIndex.coerceIn(0, platforms.lastIndex)
            val grow = 1f + PlatformHeldGrow
            val w = widths[i] * grow
            val h = heights[i] * grow
            GlowRect(ListCenterX - w / 2f, RowCenterY - h / 2f, w, h, h * (CornerRatios[platforms[i].id] ?: DefaultCornerRatio))
        } else {
            null
        }
        SelectionGlow(target = glowRect, adaptiveBlur = true)

        // The platform tiles are solid black and drawn over the covers, so covers sliding up never show through them.
        if (platforms.isNotEmpty()) Box(Modifier.fillMaxSize().fadeContent(coloredBackground) { rowDimBrush() }) {
            for (i in maxOf(platformIndex - 4, 0)..minOf(platformIndex + 5, platforms.lastIndex)) {
                key(platforms[i].id) { PlatformTile(platform = platforms[i], logo = logos[i], index = i, widths = widths, position = platformPosition, heldAmount = heldAmount, editAmount = editAmount) }
            }
        }

        // The list fades to black at the bottom of the screen, where the third cover runs off it.
        if (!coloredBackground) Box(
            modifier = Modifier
                .offset(0.dp, ListFadeTop.dp)
                .size(Dimens.DesignWidth.dp, (Dimens.DesignHeight - ListFadeTop).dp)
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black))
        )

        // The Edit Mode label comes into focus when the mode starts and blurs away when it ends.
        AnimatedVisibility(
            visible = editMode,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 46.dp, bottom = Dimens.HintBarBottom)
        ) {
            OverlayMotion { ScreenLabel(text = "Edit Mode", fontSize = 41.5.sp) }
        }

        // Edit Mode: corner marks around the selected platform; its place in the row can be changed.
        if (editMode && platforms.isNotEmpty()) {
            PlatformBrackets(widths, heights, platformPosition, held)
        }

        // Dims the platform icons toward the screen edges; on the right it starts just past the selected
        // tile, so every icon after it is slightly dimmer than the one before.
        if (!coloredBackground) Box(
            modifier = Modifier
                .offset(0.dp, (RowCenterY - RowDimHalfHeight).dp)
                .size(Dimens.DesignWidth.dp, (2 * RowDimHalfHeight).dp)
                .background(RowDimStops.let { Brush.horizontalGradient(*it) })
        )
    }
}

private fun tileHeightFor(platform: Platform, logo: ImageBitmap): Float =
    logoWidth(platform) * logo.height / logo.width + 2 * (TilePadYOverrides[platform.id] ?: TilePadY)

// Edit Mode marks around a platform tile: the same shape on every platform (a small, fixed corner radius), only their
// spacing follows the tile's size.
private const val BracketGap = 23f
private const val BracketArm = 45f
private const val BracketRadius = 12f
private const val PlatformHeldGrow = 0.08f
private const val BracketHeldScale = 1f + PlatformHeldGrow
private val BracketIdle = Color(0xFFB4B4B4)

@Composable
private fun PlatformBrackets(widths: FloatArray, heights: FloatArray, position: State<Float>, held: Boolean) {
    val pulse = LocalFocusPulse.current
    val scale by animateFloatAsState(if (held) BracketHeldScale else 1f, tween(160), label = "platformBracketScale")
    // In a layer of its own, so the pulse redraws only the marks.
    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
        val p = position.value.coerceIn(0f, widths.lastIndex.toFloat())
        val k = p.toInt().coerceAtMost(widths.lastIndex)
        val next = minOf(k + 1, widths.lastIndex)
        val t = p - k
        // The marks glide with the row: blend the rectangles of the two tiles the selection is between.
        fun extent(i: Int, f: (left: Float, top: Float, right: Float, bottom: Float) -> Float): Float {
            val tileScale = scaleAt(i - p)
            val w = widths[i] * tileScale
            val h = heights[i] * tileScale
            val left = tileX(widths, p, i)
            return f(left, RowCenterY - h / 2f, left + w, RowCenterY + h / 2f)
        }
        fun blend(f: (Float, Float, Float, Float) -> Float) = extent(k, f) + (extent(next, f) - extent(k, f)) * t
        val cx = (blend { l, _, r, _ -> (l + r) / 2f }).dp.toPx()
        val cy = (blend { _, tp, _, b -> (tp + b) / 2f }).dp.toPx()
        val gap = BracketGap.dp.toPx()
        val halfW = (blend { l, _, r, _ -> (r - l) / 2f }).dp.toPx() * scale + gap
        val halfH = (blend { _, tp, _, b -> (b - tp) / 2f }).dp.toPx() * scale + gap
        val strokeWidth = Dimens.FocusedBorderWidth.toPx()
        val color = (if (held) Color.White else BracketIdle).copy(alpha = pulse())
        drawCornerBrackets(
            cx - halfW, cy - halfH, cx + halfW, cy + halfH,
            radius = BracketRadius.dp.toPx(), arm = BracketArm.dp.toPx(),
            stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
            color = color
        )
    }
}

/**
 * The platform's tile image: the picked image once it has loaded, otherwise its name as text (the user's own title, or the
 * platform's name). The text tile has the proportions set by the platform's tileAspect, so tiles keep their usual shapes.
 */
@Composable
private fun rememberPlatformLogo(platform: Platform, uri: String?, title: String?): ImageBitmap {
    val textLogo = rememberTitleLogo(title ?: platform.menuLabel, platform.tileAspect, logoWidth(platform))
    return rememberCover(uri, PlatformIconPx) ?: textLogo
}

/**
 * [title] as a white, bold, one-line text image of the given proportions ([aspect] = height / width), the text in the middle,
 * so a text tile is laid out like any logo. Every platform's text has the same size on screen ([TitleFontSize] at the
 * selected size, whatever the width of the tile, which is [tileLogoWidth]); only a name too long for its tile gets smaller.
 */
@Composable
private fun rememberTitleLogo(title: String, aspect: Float, tileLogoWidth: Float): ImageBitmap {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = LocalTextStyle.current
    return remember(title, aspect, tileLogoWidth, density) {
        val layout = measurer.measure(
            title,
            base.merge(TextStyle(color = Color.White, fontSize = 100.sp, fontWeight = FontWeight.Bold)),
            softWrap = false,
            maxLines = 1
        )
        val textWidth = layout.size.width.coerceAtLeast(1).toFloat()
        val textHeight = layout.size.height.coerceAtLeast(1).toFloat()
        val imageWidth = TitleImagePx
        val imageHeight = (imageWidth * aspect).toInt().coerceAtLeast(1)
        // The image is drawn [tileLogoWidth] wide on screen, so one design pixel is imageWidth / tileLogoWidth bitmap pixels.
        val wanted = TitleFontSize / 100f * imageWidth / tileLogoWidth
        val fit = minOf(wanted, imageWidth * TitleTextShare / textWidth, imageHeight * TitleTextHeightShare / textHeight)
        val bitmap = ImageBitmap(imageWidth, imageHeight)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(imageWidth.toFloat(), imageHeight.toFloat())) {
            scale(fit, fit, pivot = Offset.Zero) {
                drawText(layout, topLeft = Offset((imageWidth / fit - textWidth) / 2f, (imageHeight / fit - textHeight) / 2f))
            }
        }
        bitmap
    }
}

private const val PlatformIconPx = 600

// The text of a platform tile fills this share of the tile image's width (and at most this share of its height).
private const val TitleTextShare = 0.95f
private const val TitleTextHeightShare = 1f

// The size of a platform name on its tile, in design px at the selected size (the same on every platform).
private const val TitleFontSize = 44f
private const val TitleImagePx = 1200

@Composable
private fun PlatformTile(platform: Platform, logo: ImageBitmap, index: Int, widths: FloatArray, position: State<Float>, heldAmount: State<Float>, editAmount: State<Float>) {
    val pulse = LocalFocusPulse.current
    val logoWidth = logoWidth(platform)
    val logoHeight = logoWidth * logo.height / logo.width
    val tileHeight = tileHeightFor(platform, logo)
    val cornerRatio = CornerRatios[platform.id] ?: DefaultCornerRatio
    // When the row is rearranged in Edit Mode the tile slides to its new place instead of jumping there.
    val slide by animateFloatAsState(index.toFloat(), tween(260, easing = EaseInOut), label = "platformTileIndex")
    val ownWidth = tileWidth(platform)

    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val distance = slide - position.value
                val baseScale = scaleAt(distance)
                val scale = baseScale * (1f + PlatformHeldGrow * heldAmount.value * (1f - abs(distance)).coerceIn(0f, 1f))
                val width = (ownWidth * scale).dp.roundToPx()
                val height = (tileHeight * scale).dp.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(width, height))
                val x = (tileXAt(widths, position.value, slide) - ownWidth * (scale - baseScale) / 2f).dp.roundToPx()
                val y = (RowCenterY - tileHeight * scale / 2f).dp.roundToPx()
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
            }
            .graphicsLayer {
                alpha = alphaAt(slide - position.value)
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawBehind {
                // Pure black fill, then the soft outline: white and steady on the selected platform (it only blinks in Edit Mode), gray on the others, blending as it glides past.
                val radius = CornerRadius(size.height * cornerRatio)
                drawRoundRect(color = Color.Black, cornerRadius = radius)
                val selectedness = (1f - abs(slide - position.value)).coerceIn(0f, 1f)
                // In Edit Mode the selected platform's bright edges and rim breathe with the focus pulse (it is the focus there).
                val blink = if (editAmount.value > 0f) 1f + (pulse().coerceIn(0f, 1f) - 1f) * editAmount.value else 1f
                // The selected platform looks like an "on" quick-settings tile instead; the gray outline fades out as it fades in.
                drawQuickTileOn(radius.x, scale = QuickTileScale, amount = selectedness, ring = blink)
                drawFadingOutline(
                    radius.x,
                    (size.height * 0.2f).coerceIn(14.dp.toPx(), 24.dp.toPx()),
                    UnfocusedOutline * (1f - selectedness),
                    gain = 1f
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Laid out at the selected size and scaled down by graphicsLayer, so shrinking never relayouts.
        Image(
            bitmap = logo,
            contentDescription = null,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .wrapContentSize(unbounded = true)
                .size(logoWidth.dp, logoHeight.dp)
                .graphicsLayer {
                    val scale = scaleAt(slide - position.value) * (1f + PlatformHeldGrow * heldAmount.value * (1f - abs(slide - position.value)).coerceIn(0f, 1f))
                    scaleX = scale
                    scaleY = scale
                    translationY = (LogoDown[platform.id] ?: 0f).dp.toPx() * scale
                }
        )
    }
}

/** [tileX] for a tile that is between two places in the row (sliding to a new one). */
private fun tileXAt(widths: FloatArray, position: Float, slide: Float): Float {
    val lo = slide.toInt().coerceIn(0, widths.lastIndex)
    val hi = minOf(lo + 1, widths.lastIndex)
    val t = (slide - lo).coerceIn(0f, 1f)
    return tileX(widths, position, lo) * (1f - t) + tileX(widths, position, hi) * t
}

/** Centre x of tile [index], so its game list can sit under it. */
private fun tileCenter(widths: FloatArray, position: Float, index: Int): Float =
    tileX(widths, position, index) + widths[index] * scaleAt(index - position) / 2f

// ---- The game list: 16:9 covers (the focused game and the two after it) beside the focused game's title pill ----

// Vertical centre of the focused cover and of its title pill (the focused cover and the two below it are 20% larger than the original design).
internal const val FocusCenterY = 548f

// Covers: the focused one is CoverWidth across; the two below it are smaller, with a constant gap between them.
private const val CoverWidth = 500f
private const val CoverAspect = 9f / 17f
private const val CoverGap = 40f
/** How the platform row dims toward the screen edges (black at these strengths across the screen's width). */
private val RowDimStops = arrayOf(
    0f to Color(0xE6000000),
    0.085f to Color.Transparent,
    0.46f to Color.Transparent,
    0.62f to Color(0x40000000),
    0.82f to Color(0x99000000),
    1f to Color(0xF2000000)
)

/** The row dim as an alpha mask, in px, for [fadeContent]: the band around the row, dimming toward the edges as [RowDimStops] say. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.rowDimBrush(): Pair<Brush, androidx.compose.ui.geometry.Rect> {
    val top = (RowCenterY - RowDimHalfHeight).dp.toPx()
    return Brush.horizontalGradient(*RowDimStops, startX = 0f, endX = size.width) to
        androidx.compose.ui.geometry.Rect(0f, top, size.width, top + (2 * RowDimHalfHeight).dp.toPx())
}

/** The list's fade at the bottom of the screen as an alpha mask, in px, for [fadeContent]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.listFadeBrush(): Pair<Brush, androidx.compose.ui.geometry.Rect> {
    val top = ListFadeTop.dp.toPx()
    return Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = top, endY = size.height) to
        androidx.compose.ui.geometry.Rect(0f, top, size.width, size.height)
}

/**
 * With [on], takes the content away where [mask] (a brush and the rect it covers) is opaque, instead of painting black over it:
 * the same look over a black background, but the Minima wave shows through rather than a black band.
 */
private fun Modifier.fadeContent(on: Boolean, mask: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Pair<Brush, androidx.compose.ui.geometry.Rect>): Modifier =
    if (!on) this else this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val (brush, rect) = mask()
            drawRect(brush, topLeft = rect.topLeft, size = rect.size, blendMode = BlendMode.DstOut)
        }

private const val ListFadeTop = 930f // the fade to black under the list starts here and is solid at the screen's bottom
private const val CoverDecodePx = 340

// The cover's outline is white at the very edge and fades to nothing this far (at full size) toward the inside.
private const val OutlineDepth = 19.6f

// Per slot, for a cover 1 row above the focus (-1), on it (0) and 1, 2 and 3 rows below it. In between they interpolate,
// so the covers glide. One cover shows above the focus (dim, partly behind the platform row) and two below it, all the
// same size (276 across) against the focus' 500, as in the XMB mockup.
// The first slot is an invisible step above the cover that shows above the focus, so a cover leaving upwards keeps sliding while it fades.
private const val FocusSlot = 2 // the slot of the focused cover (two covers can be above it)
private val SlotScale = floatArrayOf(0.552f, 0.552f, 1f, 0.552f, 0.552f, 0.552f)
private val SlotAlpha = floatArrayOf(0f, 0.4f, 1f, 0.55f, 0.34f, 0f)
private val SlotCenterY: FloatArray = run {
    val heights = FloatArray(SlotScale.size) { CoverWidth * SlotScale[it] * CoverAspect }
    val centers = FloatArray(SlotScale.size)
    centers[FocusSlot] = FocusCenterY
    for (k in FocusSlot + 1 until centers.size) centers[k] = centers[k - 1] + heights[k - 1] / 2f + CoverGap + heights[k] / 2f
    for (k in FocusSlot - 1 downTo 0) centers[k] = centers[k + 1] - heights[k + 1] / 2f - CoverGap - heights[k] / 2f
    centers
}

/**
 * How opaque a cover is whose brightness (its [SlotAlpha]) is [brightness]: fully, on every slot that shows (they are darkened
 * instead, see CoverSlot); only between the last shown slot and the invisible one past it does it fade out.
 */
private fun slotOpacity(brightness: Float): Float = (brightness / DimmestShownSlot).coerceIn(0f, 1f)

private val DimmestShownSlot = SlotAlpha.filter { it > 0f }.min()

private fun slotValue(values: FloatArray, distance: Float): Float {
    val x = (distance + FocusSlot).coerceIn(0f, values.lastIndex.toFloat())
    val k = x.toInt().coerceAtMost(values.lastIndex - 1)
    return values[k] + (values[k + 1] - values[k]) * (x - k)
}

/** The corner radius of a cover [scale] times full size, in dp: 26 at full size, a little tighter on the smaller ones. */
private fun coverRadius(scale: Float) = 18f + 8f * scale.coerceIn(0.2f, 1.2f)

/** Everything from [topPx] down (the covers are clipped to it). */
private class BelowRow(val topPx: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, topPx, size.width, size.height))
}

private object CoverShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val scale = size.width / with(density) { CoverWidth.dp.toPx() }
        val radius = with(density) { coverRadius(scale).dp.toPx() }
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
    }
}

// The title pill: its left end is always next to the cover (dissolving into it), and it grows to the right with the title
// up to a limit, after which the title scrolls. The text sits at the pill's right end. It looks like the status pill.
private const val PillLeft = 773f
private const val PillMaxWidth = 744f
private const val PillMinWidth = 420f // short titles never make the pill shorter than this
private const val PillHeight = 82f
private const val PillCorner = 27f
private const val PillRightPad = 33f
private const val PillLeadShare = 0.3f // the empty run before the text, as a share of the pill's width
private const val PillFadeShare = 0.56f // how much of the pill's width its left end takes to fade in (longer = more gradual)
private const val BackdropReachX = 90f // how far the ribbon wave's dark backdrop reaches past the title pill sideways ...
private const val BackdropReachY = 60f // ... and up and down
// The glow behind the focused cover: stronger at the cover's edge than the default, with a slightly shorter reach.
private const val CoverGlowAlpha = 0.7f
private const val CoverGlowBlurShare = 0.18f
private val PlusTileFill = Color(0xFF262626) // the "+" tile's fill (Edit Mode on Android and GameHub)
private const val PillFillAlpha = 0.85f // the title pill's fill is a little see-through, so the wave shows under it
private const val PillGlowAlpha = 0.14f // the title's glow is faint: the cover has the main one
private const val PillPitch = 121f
private val PillFont = 31.sp

/** The pill's width in design px, and where its text goes: [window] wide, at the right end. */
private class PillSpec(val width: Float, val overflows: Boolean = false) {
    val leadIn get() = width * PillLeadShare
    val window get() = width - leadIn - PillRightPad
}

/**
 * The left-end fade as gradient stops: nothing at all over the rounded end, then a smooth S-curve (slow start and finish)
 * up to solid at [lengthDp] from the pill's left end. Positions are fractions of [spanDp].
 */
private fun fadeStops(lengthDp: Float, spanDp: Float): Array<Pair<Float, Color>> {
    val zero = PillCorner + 6f
    val stops = ArrayList<Pair<Float, Color>>()
    stops += 0f to Color.Transparent
    stops += (zero / spanDp) to Color.Transparent
    for (i in 1..16) {
        val t = i / 16f
        stops += ((zero + t * (lengthDp - zero)) / spanDp) to Color.Black.copy(alpha = t * t * (3f - 2f * t))
    }
    if (lengthDp < spanDp) stops += 1f to Color.Black
    return stops.toTypedArray()
}

@Composable
private fun rememberPillSpec(title: String?): PillSpec {
    val density = LocalDensity.current
    val textPx = if (title != null) rememberTextWidth(title, PillFont) else 0
    if (title == null) return PillSpec(PillMinWidth)
    // A little slack so a title that fits is never mistaken for one that scrolls.
    val textWidth = textPx / density.density + 6f
    val wanted = (textWidth + PillRightPad) / (1f - PillLeadShare)
    return PillSpec(wanted.coerceIn(PillMinWidth, PillMaxWidth), overflows = wanted > PillMaxWidth)
}

@Composable
private fun GameColumn(
    index: Int,
    widths: FloatArray,
    heights: FloatArray,
    platformPosition: State<Float>,
    games: List<GameEntry>,
    showPlus: Boolean,
    selected: Int,
    isCurrent: Boolean,
    pressTick: Int,
    heldAmount: State<Float>,
    backdrop: Boolean,
    editMode: Boolean,
) {
    val press = rememberPressScale(pressTick, isCurrent)
    // The covers a scroll is about to bring in are decoded ahead (the current platform: a window around the selection, the others: the
    // first few), so they are in place when they arrive instead of popping in a moment later.
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(games, selected, isCurrent) {
        val window = if (isCurrent) (selected - 2).coerceAtLeast(0)..(selected + 8) else 0..4
        for (i in window) games.getOrNull(i)?.let { com.minimaui.app.ui.components.prefetchCover(context, it.xmbCoverUri, CoverDecodePx) }
    }
    val listPosition = animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = GlideSpring,
        visibilityThreshold = GlideThreshold,
        label = "listPosition"
    )
    // 1 when this column's platform is the selected one, fading to 0 a full step away.
    val columnAlpha = { (1f - abs(index - platformPosition.value)).coerceIn(0f, 1f) }
    val rows = games.size + if (showPlus) 1 else 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset {
                IntOffset((tileCenter(widths, platformPosition.value, index) - ListCenterX).dp.roundToPx(), 0)
            }
    ) {
        // The glow behind the focused title pill: faint and steady (the blinking belongs to the cover), and faded toward
        // the pill's left end the way the pill is, so no haze shows in its faded region. It glides when the pill changes length.
        val focusedSpec = rememberPillSpec(games.getOrNull(selected)?.title)
        val pillGlow = if (isCurrent && rows > 0) {
            GlowRect(PillLeft, FocusCenterY - PillHeight / 2f, focusedSpec.width, PillHeight, PillCorner)
        } else {
            null
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.horizontalGradient(
                            colorStops = fadeStops(PillFadeShare * focusedSpec.width, PillFadeShare * focusedSpec.width),
                            startX = PillLeft.dp.toPx(),
                            endX = (PillLeft + PillFadeShare * focusedSpec.width).dp.toPx()
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            SelectionGlow(target = null /* pillGlow: off for now */, pulsing = false, alpha = PillGlowAlpha, intensity = columnAlpha, dithered = true)
        }

        // Title pills: only the focused one is visible; each slides with the list and fades out as it leaves the focus.
        val visibleRows = maxOf(selected - 2, 0)..minOf(selected + 4, rows - 1)
        for (i in visibleRows) {
            key(games.getOrNull(i)?.id ?: "plus") {
                // The "+" row (Edit Mode on Android and GameHub) has no title, only its tile among the covers.
                if (games.getOrNull(i) != null) {
                    if (backdrop) {
                        TitleBackdrop(title = games.getOrNull(i)?.title, index = i, position = listPosition, columnAlpha = columnAlpha)
                    }
                    TitlePill(
                        title = games.getOrNull(i)?.title,
                        index = i,
                        scrolling = isCurrent && i == selected,
                        position = listPosition,
                        columnAlpha = columnAlpha
                    )
                }
            }
        }

        // The focused cover's glow (none in Edit Mode, where the focus is on the platform, except for the "+" tile): drawn over the
        // title pill's faded end (after the pills) and under the covers.
        val plusFocused = showPlus && selected == games.size
        val focusedHasCover = !editMode && games.getOrNull(selected)?.xmbCoverUri != null
        val coverGlow = if (focusedHasCover || plusFocused) {
            val height = CoverWidth * CoverAspect
            GlowRect(ListCenterX - CoverWidth / 2f, FocusCenterY - height / 2f, CoverWidth, height, coverRadius(1f))
        } else {
            null
        }
        SelectionGlow(target = coverGlow, adaptiveBlur = true, blurShare = CoverGlowBlurShare, alpha = CoverGlowAlpha, intensity = { columnAlpha() * (1f - heldAmount.value) }, dithered = true)

        // No cover is ever seen above the platform row: they are cut off at the selected platform's lower edge (just under it).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = platformPosition.value.coerceIn(0f, heights.lastIndex.toFloat())
                    val k = p.toInt().coerceAtMost(heights.lastIndex)
                    val t = p - k
                    val height = heights[k] + (heights[minOf(k + 1, heights.lastIndex)] - heights[k]) * t
                    clip = true
                    shape = BelowRow((RowCenterY + height / 2f - 6f).dp.toPx())
                }
        ) {
            // With the ribbon wave behind the page, the unfocused covers sit on soft dark backdrops as the Apps page's icons do,
            // so the wave's lines don't run through them; all of them first, so none is drawn over a neighbouring cover.
            if (backdrop) {
                val last = if (showPlus) games.size else games.size - 1
                CoverBackdrops(rows = visibleRows.first..minOf(visibleRows.last, last), position = listPosition, columnAlpha = columnAlpha)
            }
            for (i in visibleRows) {
                val game = games.getOrNull(i)
                if (game == null) {
                    // The "+" row: a cover-sized tile with a plus in it.
                    if (showPlus && i == games.size) {
                        key("plus") {
                            CoverSlot(uri = null, plus = true, quiet = editMode, index = i, position = listPosition, columnAlpha = columnAlpha, press = press, heldAmount = heldAmount)
                        }
                    }
                    continue
                }
                // A game without a cover gets a plain placeholder (the status pill's colour) in the same slot.
                key(game.id) {
                    CoverSlot(uri = game.xmbCoverUri, quiet = editMode, index = i, position = listPosition, columnAlpha = columnAlpha, press = press, heldAmount = heldAmount)
                }
            }
        }
    }
}

/** How far the dark backdrop behind an unfocused cover reaches, as a multiple of the cover's size (the Apps page's 360 / 212). */
private const val CoverBackdropReach = 360f / 212f

/**
 * The soft dark backdrops behind the unfocused covers in [rows] (the same as the Apps page's behind its icons, stretched to a
 * cover's shape): 30% black under a cover, fading out to [CoverBackdropReach] times its size. Each fades out as its cover
 * reaches the focus (which has its glow instead) and with the cover at the ends of the list. All drawn by one node, in its
 * own layer, so they cost no composition and only redraw while the list moves.
 */
@Composable
private fun CoverBackdrops(rows: IntRange, position: State<Float>, columnAlpha: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = columnAlpha() }
            .drawBehind {
                for (i in rows) {
                    val distance = i - position.value
                    val alpha = abs(distance).coerceIn(0f, 1f) * slotOpacity(slotValue(SlotAlpha, distance))
                    if (alpha <= 0.005f) continue
                    val width = (CoverWidth * slotValue(SlotScale, distance) * CoverBackdropReach).dp.toPx()
                    val height = width * CoverAspect
                    val center = Offset(ListCenterX.dp.toPx(), slotValue(SlotCenterY, distance).dp.toPx())
                    val radius = width / 2f
                    // A round gradient squashed to the cover's proportions.
                    scale(1f, height / width, center) {
                        drawCircle(CoverBackdropBrush(center, radius), radius, center, alpha = alpha)
                    }
                }
            }
    )
}

private fun CoverBackdropBrush(center: Offset, radius: Float) = Brush.radialGradient(
    0f to Color.Black.copy(alpha = 0.3f),
    0.58f to Color.Black.copy(alpha = 0.3f),
    0.8f to Color.Black.copy(alpha = 0.12f),
    1f to Color.Transparent,
    center = center,
    radius = radius,
)

/** One 16:9 cover in the list: big on the focus, smaller and dimmer around it. */
@Composable
private fun CoverSlot(uri: String?, index: Int, position: State<Float>, columnAlpha: () -> Float, press: State<Float>, heldAmount: State<Float>, plus: Boolean = false, quiet: Boolean = false) {
    val bitmap = rememberCover(uri, CoverDecodePx)
    val slideIndex by animateFloatAsState(index.toFloat(), tween(260, easing = EaseInOut), label = "coverIndex")
    val pulse = LocalFocusPulse.current
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val distance = slideIndex - position.value
                val width = CoverWidth * slotValue(SlotScale, distance)
                val height = width * CoverAspect
                val placeable = measurable.measure(Constraints.fixed(width.dp.roundToPx(), height.dp.roundToPx()))
                val x = (ListCenterX - width / 2f).dp.roundToPx()
                val y = (slotValue(SlotCenterY, distance) - height / 2f).dp.roundToPx()
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
            }
            .graphicsLayer {
                alpha = slotOpacity(slotValue(SlotAlpha, slideIndex - position.value)) * columnAlpha()
                // The press-in when A launches the game: only the focused cover dips.
                val pressed = 1f + (press.value - 1f) * (1f - abs(slideIndex - position.value)).coerceIn(0f, 1f)
                scaleX = pressed
                scaleY = pressed
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .clip(CoverShape)
            .then(if (plus) Modifier.background(PlusTileFill) else if (uri == null) Modifier.background(PillSurface) else Modifier)
            .drawWithContent {
                drawContent()
                if (plus) {
                    // The plus, in the middle of the tile.
                    val arm = size.width * 0.07f
                    val stroke = size.width * 0.024f
                    drawLine(TextPrimary, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), stroke, StrokeCap.Round)
                    drawLine(TextPrimary, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), stroke, StrokeCap.Round)
                }
                if (uri == null && !plus) {
                    // The placeholder: the status pill's fill and its dim background outline, no glow and no white outline.
                    val radius = coverRadius(size.width / CoverWidth.dp.toPx()).dp.toPx()
                    drawFadingOutline(radius, 24.dp.toPx(), BackgroundOutline, gain = 1f)
                } else {
                    // The white-to-transparent outline: steady and neutral when unfocused, brighter and blinking when focused.
                    val focus = (1f - abs(slideIndex - position.value)).coerceIn(0f, 1f) * (1f - heldAmount.value)
                    // Edit Mode is about the platforms, so a game cover doesn't blink there (the "+" tile still does).
                    // Only the focused cover reads the pulse: the others don't change, so they aren't redrawn with it.
                    val level = if (focus <= 0f || (quiet && !plus)) 1f else pulse().coerceIn(0f, 1f)
                    val strength = UnfocusedOutline + focus * (level - UnfocusedOutline)
                    val scale = size.width / CoverWidth.dp.toPx()
                    drawFadingOutline(coverRadius(scale).dp.toPx(), OutlineDepth.dp.toPx() * scale.coerceAtLeast(0.6f), strength, gain = 1f + (FocusedOutlineGain - 1f) * focus)
                }
                // The covers around the focus are darker, not see-through: black over them (outline and all), so nothing
                // behind them (the wave) shows through.
                val dim = 1f - slotValue(SlotAlpha, slideIndex - position.value)
                if (dim > 0.005f) drawRect(Color.Black.copy(alpha = dim.coerceIn(0f, 1f)))
            }
    ) {
        Crossfade(targetState = bitmap, animationSpec = tween(300), label = "coverSwap") { image ->
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * With the ribbon wave behind the page, a faint dark patch that fades out on all sides sits under the title pill, so the
 * wave's lines don't run through the title. It slides and fades with the pill (but isn't clipped to it).
 */
@Composable
private fun TitleBackdrop(title: String?, index: Int, position: State<Float>, columnAlpha: () -> Float) {
    val slideIndex by animateFloatAsState(index.toFloat(), tween(260, easing = EaseInOut), label = "backdropIndex")
    val spec = rememberPillSpec(title)
    Box(
        modifier = Modifier
            .offset {
                val distance = slideIndex - position.value
                IntOffset((PillLeft - BackdropReachX).dp.roundToPx(), (FocusCenterY - PillHeight / 2f - BackdropReachY + distance * PillPitch).dp.roundToPx())
            }
            .size((spec.width + 2 * BackdropReachX).dp, (PillHeight + 2 * BackdropReachY).dp)
            .graphicsLayer { alpha = (1f - abs(slideIndex - position.value)).coerceIn(0f, 1f) * columnAlpha() }
            .drawBehind {
                // A circle gradient stretched into an ellipse that fills the box.
                scale(size.width / size.height, 1f, pivot = center) {
                    drawRect(
                        Brush.radialGradient(
                            0f to Color.Black.copy(alpha = 0.3f),
                            0.6f to Color.Black.copy(alpha = 0.3f),
                            0.82f to Color.Black.copy(alpha = 0.12f),
                            1f to Color.Transparent,
                            center = center,
                            radius = size.height / 2f
                        ),
                        topLeft = Offset(center.x - size.height / 2f, 0f),
                        size = Size(size.height, size.height)
                    )
                }
            }
    )
}

/**
 * The focused game's title in a pill beside the covers, styled like the status pill. Its left end is anchored to the
 * cover and dissolves into it; the right end follows the title's length (up to a limit, after which the title scrolls).
 */
@Composable
private fun TitlePill(
    title: String?,
    index: Int,
    scrolling: Boolean,
    position: State<Float>,
    columnAlpha: () -> Float,
) {
    val slideIndex by animateFloatAsState(index.toFloat(), tween(260, easing = EaseInOut), label = "pillIndex")
    val spec = rememberPillSpec(title)
    Box(
        modifier = Modifier
            .offset {
                val distance = slideIndex - position.value
                IntOffset(PillLeft.dp.roundToPx(), (FocusCenterY - PillHeight / 2f + distance * PillPitch).dp.roundToPx())
            }
            .size(spec.width.dp, PillHeight.dp)
            .graphicsLayer { alpha = (1f - abs(slideIndex - position.value)).coerceIn(0f, 1f) * columnAlpha() }
    ) {
        // The pill itself (the status pill's fill and border), in its own layer so its left end can be faded out alone.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    val corner = PillCorner.dp.toPx()
                    drawRoundRect(PillSurface, cornerRadius = CornerRadius(corner), alpha = PillFillAlpha)
                    drawFadingOutline(corner, 14.dp.toPx(), BackgroundOutline, gain = 1f)
                    // Nothing over the rounded end, then a curve up to solid.
                    drawRect(
                        Brush.horizontalGradient(colorStops = fadeStops(PillFadeShare * spec.width, spec.width)),
                        blendMode = BlendMode.DstIn
                    )
                }
        )
        val textModifier = Modifier.offset(spec.leadIn.dp, 0.dp).size(spec.window.dp, PillHeight.dp)
        if (title != null && spec.overflows) {
            // A title too long for the pill starts at its indent, fully visible, and scrolls on past that point into the
            // faded left end, fading with the background (so the text layer gets the same fade, ending at the indent).
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.horizontalGradient(colorStops = fadeStops(spec.leadIn, spec.width)),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                MarqueeText(
                    text = title,
                    color = TextPrimary,
                    fontSize = PillFont,
                    scrolling = scrolling,
                    modifier = Modifier.fillMaxSize(),
                    startIndent = spec.leadIn.dp,
                    endIndent = PillRightPad.dp,
                    alignment = Alignment.CenterStart
                )
            }
        } else if (title != null) {
            MarqueeText(
                text = title,
                color = TextPrimary,
                fontSize = PillFont,
                scrolling = scrolling,
                modifier = textModifier,
                alignment = Alignment.CenterEnd
            )
        } else {
            Box(textModifier, contentAlignment = Alignment.CenterEnd) {
                Canvas(modifier = Modifier.size(26.dp)) {
                    val stroke = 5.dp.toPx()
                    drawLine(TextPrimary, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke, StrokeCap.Round)
                    drawLine(TextPrimary, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), stroke, StrokeCap.Round)
                }
            }
        }
    }
}
