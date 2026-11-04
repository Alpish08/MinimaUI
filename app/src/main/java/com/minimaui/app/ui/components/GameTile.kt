package com.minimaui.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import com.minimaui.app.ui.favorites.focusScaleFor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaui.app.ui.theme.EditTileFill
import com.minimaui.app.ui.theme.TextDim
import com.minimaui.app.ui.theme.TextPrimary
import com.minimaui.app.ui.theme.TileFillIdle
import com.minimaui.app.ui.theme.TileSurfaceFocused

enum class TileSize(val corner: Dp, val designSize: Dp) {
    Large(44.dp, 744.dp),
    Medium(38.dp, 344.dp),
    Small(30.dp, 158.dp),
}

/**
 * A cover tile in the Favorites bento (or the folder view). Small tiles are too small for a title pill.
 * In [editMode] resting tiles go darker, and a [held] tile (picked up to be moved) lifts slightly.
 */
@Composable
fun GameTile(
    title: String,
    coverUri: String?,
    tileSize: TileSize,
    size: DpSize,
    focused: Boolean,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    held: Boolean = false,
    pressTick: Int = 0,
    titleAlpha: () -> Float = { 1f },
) {
    val shape = RoundedCornerShape(tileSize.corner)
    val press = rememberPressScale(pressTick, focused)
    val focusScale by animateFloatAsState(if (focused) focusScaleFor(tileSize) else 1f, tween(200), label = "tileFocusScale")
    val cover = rememberCover(coverUri, with(LocalDensity.current) { tileSize.designSize.roundToPx() })
    Box(
        modifier = modifier
            .graphicsLayer {
                val lift = (if (held) 1.05f else 1f) * press.value * focusScale
                scaleX = lift
                scaleY = lift
                // Rendered once into a texture (cover, frosted title pill, outline), composited each frame.
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .clip(shape)
            .background(
                when {
                    focused -> TileSurfaceFocused
                    editMode -> EditTileFill
                    else -> TileFillIdle
                }
            )
            // Every tile keeps the neutral soft outline; the focused one has the brighter, blinking ring over it.
            .softOutline(
                depth = when (tileSize) {
                    TileSize.Large -> 28.dp
                    TileSize.Medium -> 21.dp
                    TileSize.Small -> 15.dp
                },
                corner = tileSize.corner
            ) { UnfocusedOutline }
    ) {
        cover?.let {
            // Inset a hair and rounded a little tighter than the tile, so its edge stays under the border.
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .padding(1.5.dp)
                    .clip(RoundedCornerShape(tileSize.corner - 1.5.dp))
            )
        }
        when (tileSize) {
            TileSize.Large -> TitlePill(
                title = title,
                focused = focused,
                fontSize = 32.sp,
                cover = cover,
                tileSize = size,
                pillSize = DpSize(416.dp, 71.dp),
                bottomPadding = 72.dp,
                trim = 20.dp,
                modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = titleAlpha() }
            )
            TileSize.Medium -> TitlePill(
                title = title,
                focused = focused,
                fontSize = 21.sp,
                cover = cover,
                tileSize = size,
                pillSize = DpSize(246.dp, 48.dp),
                bottomPadding = 37.dp,
                modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = titleAlpha() }
            )
            TileSize.Small -> Unit
        }
    }
}

/**
 * The title pill on a tile. It frosts whatever cover is behind it (the cover is drawn again inside the
 * pill, blurred and darkened) and has no outline unless it is focused, when it gets a white one.
 */
@Composable
fun TitlePill(
    title: String,
    focused: Boolean,
    fontSize: TextUnit,
    cover: ImageBitmap?,
    tileSize: DpSize,
    pillSize: DpSize,
    bottomPadding: Dp,
    trim: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    // A short title gets a short pill (its text plus the same breathing room a scrolling title has); long ones keep the full width.
    val density = LocalDensity.current
    val sidePad = (pillSize.height + trim) * 0.7f
    val textWidth = with(density) { rememberTextWidth(title, fontSize).toDp() }
    val pillWidth = (textWidth + sidePad * 2 + 6.dp).coerceIn(minOf(pillSize.height * 2.5f, pillSize.width), pillSize.width)
    val pillLeft = (tileSize.width - pillWidth) / 2
    val pillTop = tileSize.height - bottomPadding - pillSize.height
    val frosted = cover != null
    Box(
        modifier = modifier
            .padding(bottom = bottomPadding)
            .size(pillWidth, pillSize.height)
            .clip(CircleShape)
            .background(if (cover == null) Color(0x33000000) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (frosted) {
            Image(
                bitmap = cover!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .placeAt(-pillLeft, -pillTop, tileSize.width, tileSize.height)
                    .blur(BlurRadius, BlurredEdgeTreatment.Unbounded)
            )
            Box(Modifier.matchParentSize().background(PillScrim))
        }
        MarqueeText(
            text = title,
            color = when {
                focused -> TextPrimary
                cover != null -> TextPrimary.copy(alpha = 0.85f)
                else -> TextDim
            },
            fontSize = fontSize,
            scrolling = focused,
            startIndent = (pillSize.height + trim) * 0.7f,
            endIndent = (pillSize.height + trim) * 0.45f,
            centerWhenFits = true
        )
    }
}

private val BlurRadius = 18.dp
private val PillScrim = Color(0x66000000)

/** Places the child at a fixed size and offset inside its parent, letting it overflow (the parent clips). */
private fun Modifier.placeAt(offsetX: Dp, offsetY: Dp, width: Dp, height: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(Constraints.fixed(width.roundToPx(), height.roundToPx()))
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(offsetX.roundToPx(), offsetY.roundToPx()) }
    }
