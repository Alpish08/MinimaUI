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

private val PlusFill = Color(0xFF292929)
private val PlusColor = Color(0xFFD8D8D8)

/**
 * The Apps page: round icons with their names, ten to a page (rows of 3, 4 and 3), pages stacked vertically.
 * Empty slots are thin outlines, or "+" buttons while [editMode] is on.
 */
@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    page: Int,
    slot: Int,
    pageCount: Int,
    editMode: Boolean,
    held: Boolean,
    modifier: Modifier = Modifier,
    wave: Int = WaveOriginal,
    ribbon: Boolean = false,
    pressTick: Int = 0,
    onTap: (slot: Int) -> Unit = {},
    onSwipe: (delta: Int) -> Unit = {},
    onEditHold: (slot: Int) -> Unit = {},
    onEditDrag: (slot: Int) -> Unit = {},
    onEditEdge: (delta: Int) -> Unit = {},
    onEditRelease: () -> Unit = {},
) {
    val density = LocalDensity.current.density
    Box(
        modifier = modifier
            .fillMaxSize()
            // Touch: tap an icon to launch it, swipe up/down to change page (the owner ignores both in Edit Mode).
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val x = offset.x / density
                    val y = offset.y / density
                    BentoLayouts.Apps.indexOfFirst { slot ->
                        val dx = x - (slot.x.value + slot.width.value / 2f)
                        val dy = y - (slot.y.value + slot.height.value / 2f)
                        dx * dx + dy * dy <= TapRadius * TapRadius
                    }.takeIf { it >= 0 }?.let(onTap)
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
            // Touch, Edit Mode: hold an icon to pick it up, drag it over the others (near the top or bottom the page turns), let go to put it down.
            .pointerInput(editMode) {
                if (!editMode) return@pointerInput
                fun slotAt(position: Offset): Int {
                    val x = position.x / density
                    val y = position.y / density
                    return BentoLayouts.Apps.indexOfFirst { slot ->
                        val dx = x - (slot.x.value + slot.width.value / 2f)
                        val dy = y - (slot.y.value + slot.height.value / 2f)
                        dx * dx + dy * dy <= TapRadius * TapRadius
                    }
                }
                detectDragGesturesAfterLongPress(
                    onDragStart = { position -> slotAt(position).takeIf { it >= 0 }?.let(onEditHold) },
                    onDrag = { change, _ ->
                        change.consume()
                        slotAt(change.position).takeIf { it >= 0 }?.let(onEditDrag)
                        val y = change.position.y / density
                        if (y < 110f) onEditEdge(-1) else if (y > 1080f - 110f) onEditEdge(1)
                    },
                    onDragEnd = { onEditRelease() },
                    onDragCancel = { onEditRelease() },
                )
            }
    ) {
        // The same wave as behind All Games, a little blurred and dimmer so the icons stay in front.
        XmbWave(style = wave, soft = 3f, brightness = AppsWaveBrightness, modifier = Modifier.fillMaxSize())
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
                AppsPage(
                    apps = apps,
                    page = shownPage,
                    focusedSlot = if (shownPage == page) slot else -1,
                    editMode = editMode,
                    held = held,
                    pressTick = pressTick,
                    backdrop = ribbon
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

/** One page of icons (ten slots). Icons are keyed by app, so reordering or removing glides them to their new slots. */
@Composable
private fun AppsPage(
    apps: List<AppEntry>,
    page: Int,
    focusedSlot: Int,
    editMode: Boolean,
    held: Boolean,
    pressTick: Int,
    backdrop: Boolean,
) {
    val slots = BentoLayouts.Apps
    // The icons settle in one after another as the screen comes in.
    val arrival = LocalArrival.current
    // An icon that left this page (removed, or moved off it) shrinks and fades where it was.
    val pageApps = slots.indices.map { apps.getOrNull(page * slots.size + it) }
    val before = remember { arrayOf(pageApps) }
    val leaving = remember(pageApps) {
        val ids = pageApps.mapNotNull { it?.id }.toSet()
        val gone = before[0].mapIndexedNotNull { i, a -> if (a != null && a.id !in ids) a to i else null }
        before[0] = pageApps
        gone
    }
    Box(modifier = Modifier.fillMaxSize()) {
        val focusedApp = apps.getOrNull(page * slots.size + focusedSlot)
        // Empty "+" slots (Edit Mode only) get the ring when focused too, and the plain menu-style glow and solid ring: there is no cover to match.
        val focusRect = slots.getOrNull(focusedSlot)?.takeIf { focusedApp != null || editMode }?.let {
            GlowRect(it.x.value, it.y.value, it.width.value, it.height.value, it.width.value / 2f)
        }
        // While the screen comes in, the glow and the ring ride along with the focused icon: they fade in, rise and grow as it does.
        val glowIn = { if (focusedApp != null) cascade(arrival(), focusedSlot) else 1f }
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
            SelectionGlow(focusRect.takeIf { focusedApp != null }, intensity = glowIn, alpha = AppGlowAlpha)
            SelectionGlow(focusRect.takeIf { focusedApp != null }, intensity = glowIn, alpha = 0.3f, inflate = 40f)
        }
        SelectionGlow(focusRect.takeIf { focusedApp == null })
        slots.forEachIndexed { i, s ->
            val app = apps.getOrNull(page * slots.size + i)
            val focused = i == focusedSlot
            key(app?.id ?: "empty$i") {
                val glide = tween<Float>(durationMillis = 260, easing = EaseInOut)
                val x by animateFloatAsState(s.x.value, glide, label = "iconX")
                val y by animateFloatAsState(s.y.value, glide, label = "iconY")
                if (app != null) {
                    // The cover belongs to the app (not to the slot), so a moved icon never shows another one's picture for a moment.
                    val cover = rememberCover(app.coverUri, IconSize.toInt())
                    AppIcon(app, cover, x, y, i, arrival, focused = focused, editMode = editMode, held = held && focused, pressTick = pressTick, backdrop = backdrop)
                } else {
                    EmptySlot(x, y, editMode = editMode)
                }
            }
        }
        for ((gone, index) in leaving) key(gone.id) { LeavingIcon(gone, slots[index].x.value, slots[index].y.value) }
        // The outline glides between icons and grows with the focused icon.
        val ringScale by animateFloatAsState(
            when {
                focusedApp == null -> 1f
                held -> 1.2f
                else -> FocusedScale
            },
            tween(160),
            label = "ringScale"
        )
        val press = rememberPressScale(pressTick, true)
        // The same white-to-transparent, outside-in outline as the XMB covers; it also marks the focus in Edit Mode
        // and on an empty "+" slot.
        Box(withIcon) {
            FocusRing(focusRect, color = Color.White, intensity = glowIn, scale = { ringScale * press.value }, fadeDepth = AppOutlineDepth)
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

@Composable
private fun AppIcon(app: AppEntry, cover: ImageBitmap?, x: Float, y: Float, index: Int, arrival: () -> Float, focused: Boolean, editMode: Boolean, held: Boolean, pressTick: Int, backdrop: Boolean) {
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
    // Unfocused icons carry the neutral soft outline; the focused one has the brighter, blinking ring instead.
    val outlineStrength by animateFloatAsState(if (focused) 0f else UnfocusedOutline, tween(200), label = "iconOutline")

    // With the ribbon wave behind the page, an unfocused icon sits on a soft dark backdrop that fades out around it (and
    // under its name), so the wave's lines don't run through it; the focused one has its glow instead.
    val backdropAlpha by animateFloatAsState(if (backdrop && !focused) 1f else 0f, tween(200), label = "iconBackdrop")
    if (backdropAlpha > 0f) {
        Box(
            modifier = Modifier
                .offset((x - (BackdropSize - IconSize) / 2f).dp, (y - (BackdropSize - IconSize) / 2f).dp)
                .size(BackdropSize.dp)
                .graphicsLayer { alpha = backdropAlpha * cascade(arrival(), index) }
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
            .softOutline(depth = AppOutlineDepth, gain = FocusedOutlineGain) { outlineStrength }
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
    MarqueeText(
        text = app.title,
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

@Composable
private fun EmptySlot(x: Float, y: Float, editMode: Boolean) {
    val base = Modifier.offset(x.dp, y.dp).size(IconSize.dp)
    if (!editMode) {
        Box(modifier = base.backgroundOutline(depth = 14.dp))
        return
    }
    Box(
        modifier = base
            .clip(CircleShape)
            .background(PlusFill)
            .unfocusedOutline(depth = AppOutlineDepth),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(46.dp)) {
            val stroke = 8.dp.toPx()
            drawLine(PlusColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke, StrokeCap.Round)
            drawLine(PlusColor, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), stroke, StrokeCap.Round)
        }
    }
}
