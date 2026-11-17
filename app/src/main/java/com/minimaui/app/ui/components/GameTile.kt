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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.fillMaxSize
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
            // The cover blurs and darkens toward its bottom edge, under the title.
            if (tileSize != TileSize.Small) CoverBottomBlur(it, tileSize, size, Modifier.align(Alignment.BottomCenter))
        }
        when (tileSize) {
            TileSize.Large -> TileTitle(
                title = title,
                focused = focused,
                hasCover = cover != null,
                fontSize = 32.sp,
                width = size.width - TileSize.Large.corner * 2,
                height = 56.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).graphicsLayer { alpha = titleAlpha() }
            )
            TileSize.Medium -> TileTitle(
                title = title,
                focused = focused,
                hasCover = cover != null,
                fontSize = 21.sp,
                width = size.width - TileSize.Medium.corner * 2,
                height = 38.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).graphicsLayer { alpha = titleAlpha() }
            )
            TileSize.Small -> Unit
        }
    }
}

/**
 * The bottom of the cover again, blurred and darkened, fading in from [CoverBlurReach] of the way up the tile to full at its
 * bottom edge. Only that band is drawn and blurred (the cover's matching slice, plus the blur's reach above it for it to draw
 * from), in one layer that also clips to the tile's rounded bottom corners, shades and fades it.
 */
@Composable
private fun CoverBottomBlur(cover: ImageBitmap, tileSize: TileSize, tile: DpSize, modifier: Modifier) {
    val inset = 1.5.dp // as the cover: a hair inside the tile, so its edge stays under the border
    val corner = tileSize.corner - inset
    val blur = if (tileSize == TileSize.Large) 32.dp else 20.dp
    val innerHeight = tile.height - inset * 2
    val ramp = innerHeight * CoverBlurReach
    val band = ramp + blur
    val shape = RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
    Box(
        modifier
            .padding(start = inset, end = inset, bottom = inset)
            .fillMaxWidth()
            .height(band)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                this.shape = shape
                clip = true
            }
            .drawWithContent {
                drawContent()
                drawRect(Color.Black.copy(alpha = CoverBlurShade))
                // Nothing over the blur's extra reach, then a smooth ramp (eased at both ends) so the blur has no start line.
                val top = blur.toPx() / size.height
                val at = { share: Float -> top + (1f - top) * share }
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        top to Color.Transparent,
                        at(0.45f) to Color.Black.copy(alpha = 0.6f),
                        at(0.75f) to Color.Black,
                        1f to Color.Black,
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        Spacer(
            Modifier
                .matchParentSize()
                .blur(blur, BlurredEdgeTreatment.Rectangle)
                .drawBehind {
                    // The slice of the cover (cropped to the tile like the cover itself) that lies under this band.
                    val fullHeight = innerHeight.toPx()
                    val scale = maxOf(size.width / cover.width, fullHeight / cover.height)
                    val srcWidth = size.width / scale
                    val srcLeft = (cover.width - srcWidth) / 2f
                    val srcTop = (cover.height - fullHeight / scale) / 2f + (fullHeight - size.height) / scale
                    drawImage(
                        cover,
                        srcOffset = IntOffset(srcLeft.toInt(), srcTop.toInt().coerceAtLeast(0)),
                        srcSize = IntSize(srcWidth.toInt(), (size.height / scale).toInt().coerceAtLeast(1)),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.Low
                    )
                }
        )
    }
}

/**
 * A tile's title, on the blurred bottom of its cover: [width] wide (the tile less its rounded corners), so a long title only
 * fades out near the tile's sides. The focused one scrolls; the others end in "…".
 */
@Composable
private fun TileTitle(title: String, focused: Boolean, hasCover: Boolean, fontSize: TextUnit, width: Dp, height: Dp, modifier: Modifier) {
    Box(modifier.size(width, height), contentAlignment = Alignment.Center) {
        MarqueeText(
            text = title,
            color = when {
                focused -> TextPrimary
                hasCover -> TextPrimary.copy(alpha = 0.85f)
                else -> TextDim
            },
            fontSize = fontSize,
            scrolling = focused,
            startIndent = 12.dp,
            endIndent = 12.dp,
            centerWhenFits = true
        )
    }
}

private const val CoverBlurReach = 0.36f // how far up the tile the blur reaches (a share of its height)
private const val CoverBlurShade = 0.55f // how much the blurred band is darkened, at the bottom
