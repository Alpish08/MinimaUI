package com.minimaui.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import android.graphics.RuntimeShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minimaui.app.ui.theme.GlowColor
import com.minimaui.app.ui.theme.OutlineFocused

/** A highlighted rectangle in the coordinate space of the Box hosting a [SelectionGlow], in design pixels. */
class GlowRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val corner: Float = height / 2f,
)

private val EmptyGlow = GlowRect(0f, 0f, 0f, 0f)
private val GlowBlurRadius = 80.dp
private const val GlowAlpha = 0.55f
private const val GlowInflate = 12f

/** With adaptive blur, the glow's softness (sigma) as a share of the smaller side of what it surrounds. */
private const val AdaptiveBlurShare = 0.22f

/** The animated state of a highlight gliding between rectangles; shared so a glow and its ring always move as one. */
internal class Highlight(
    val x: State<Float>,
    val y: State<Float>,
    val width: State<Float>,
    val height: State<Float>,
    val corner: State<Float>,
    val fade: State<Float>,
    /** Where the highlight is going (its target's corner), to tell when it has arrived. */
    val targetX: Float,
    val targetY: Float,
)

@Composable
internal fun rememberHighlight(target: GlowRect?): Highlight {
    val last = remember { arrayOf(EmptyGlow) }
    if (target != null) last[0] = target
    val rect = last[0]
    val spec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
    return Highlight(
        x = animateFloatAsState(rect.x, spec, label = "hlX"),
        y = animateFloatAsState(rect.y, spec, label = "hlY"),
        width = animateFloatAsState(rect.width, spec, label = "hlW"),
        height = animateFloatAsState(rect.height, spec, label = "hlH"),
        corner = animateFloatAsState(rect.corner, spec, label = "hlCorner"),
        fade = animateFloatAsState(if (target != null) 1f else 0f, tween(180), label = "hlFade"),
        targetX = rect.x,
        targetY = rect.y,
    )
}

private fun Modifier.placeHighlight(h: Highlight, inflate: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints.fixed(
            (h.width.value + 2 * inflate).dp.roundToPx(),
            (h.height.value + 2 * inflate).dp.roundToPx()
        )
    )
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place((h.x.value - inflate).dp.roundToPx(), (h.y.value - inflate).dp.roundToPx())
    }
}

/**
 * The focus highlight: a soft bloom that glides to [target] (and fades out when it is null).
 *
 * Place it as the first child of a full-size Box, *behind* the items it highlights, and give those
 * items translucent fills so the bloom shows through and bleeds into their neighbours.
 *
 * Position, size and fade are read in layout/draw only, so moving the selection never recomposes.
 * [intensity] scales it further (e.g. to fade a whole column) and is read at draw time too. The fade is baked
 * into the draw colour rather than a graphicsLayer alpha, because a faded layer is composited offscreen and
 * clipped to its bounds, which would cut the bloom off. With [adaptiveBlur] the bloom's softness follows the size
 * of what it surrounds ([blurShare] of its smaller side), so a small tile gets a tight glow instead of a big blob.
 */
@Composable
fun SelectionGlow(
    target: GlowRect?,
    intensity: () -> Float = { 1f },
    color: Color = GlowColor,
    pulsing: Boolean = true,
    adaptiveBlur: Boolean = false,
    blurShare: Float = AdaptiveBlurShare,
    alpha: Float = GlowAlpha,
    inflate: Float = GlowInflate,
    dithered: Boolean = false,
) {
    val pulse = LocalFocusPulse.current
    val h = rememberHighlight(target)
    // Once fully faded out there is nothing to draw, so don't keep a blurred layer around for it.
    val visible by remember { derivedStateOf { h.fade.value > 0.01f } }
    if (!visible) return
    val blurRadius = if (adaptiveBlur) (minOf(h.width.value, h.height.value) * blurShare).coerceIn(30f, 80f).dp else GlowBlurRadius

    // A blurred rounded rectangle drawn in one go with a blur mask filter, which the GPU renders from a small cached
    // template. The earlier version blurred a whole layer every frame, which cost far more (the glow is always on screen).
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Box(
        modifier = Modifier
            .placeHighlight(h, inflate)
            .drawBehind {
                val sigma = blurRadius.toPx()
                if (dithered) {
                    // A faint, wide glow in 8-bit bands and gets cut off at the blur filter's bounds; this one is computed per pixel.
                    val a = alpha * h.fade.value * intensity().coerceIn(0f, 1f) * (if (pulsing) pulse() else 1f)
                    val reach = sigma * DitheredReach
                    ditheredGlow.setFloatUniform("center", size.width / 2f, size.height / 2f)
                    ditheredGlow.setFloatUniform("halfSize", size.width / 2f, size.height / 2f)
                    ditheredGlow.setFloatUniform("sigma", sigma)
                    ditheredGlow.setFloatUniform("tint", color.red, color.green, color.blue, a)
                    drawRect(ShaderBrush(ditheredGlow), Offset(-reach, -reach), Size(size.width + 2 * reach, size.height + 2 * reach))
                    return@drawBehind
                }
                paint.maskFilter = maskFilterFor(sigma)
                paint.color = color.copy(alpha = alpha * h.fade.value * intensity().coerceIn(0f, 1f) * (if (pulsing) pulse() else 1f)).toArgb()
                val radius = (h.corner.value + inflate).dp.toPx()
                drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint) }
            }
    )
}

private const val DitheredReach = 3.6f

/** How close (design px) a gliding ring must be to its item to count as being on it. */
private const val ArrivedWithin = 30f

// The Gaussian blur of a rectangle is the product of two one-dimensional profiles (the difference of two normal CDFs); the
// CDF uses the tanh approximation of erf (as a logistic: AGSL has no tanh). Noise of half a step is added to the alpha before it is rounded to 8 bits (the dither).
private const val DitheredGlowAgsl = """
uniform float2 center;
uniform float2 halfSize;
uniform float sigma;
uniform float4 tint;

float cdf(float z) {
    float x = z * 0.70710678;
    return 1.0 / (1.0 + exp(-clamp(1.59576912 * (x + 0.044715 * x * x * x), -30.0, 30.0)));
}

half4 main(float2 p) {
    float2 d = p - center;
    float ax = cdf((halfSize.x - d.x) / sigma) - cdf((-halfSize.x - d.x) / sigma);
    float ay = cdf((halfSize.y - d.y) / sigma) - cdf((-halfSize.y - d.y) / sigma);
    float a = tint.a * ax * ay;
    float n = fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
    a = max(a + (n - 0.5) / 255.0, 0.0);
    return half4(half3(tint.rgb * a), half(a));
}
"""

private val ditheredGlow by lazy { RuntimeShader(DitheredGlowAgsl) }

private var cachedSigma = -1f
private var cachedFilter: android.graphics.BlurMaskFilter? = null

/** A blur mask filter with the softness of a Gaussian blur of standard deviation [sigma] px (reused while the size stays put). */
private fun maskFilterFor(sigma: Float): android.graphics.BlurMaskFilter {
    if (sigma != cachedSigma || cachedFilter == null) {
        cachedSigma = sigma
        // BlurMaskFilter's radius r gives sigma = 0.57735 r + 0.5.
        cachedFilter = android.graphics.BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(1f), android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    return cachedFilter!!
}

/**
 * The focus outline: the covers' soft outline, [color] at the item's edge and fading to nothing [fadeDepth] toward the
 * inside (see [drawFadingOutline]), that glides from item to item (instead of one outline vanishing and another
 * appearing). Place it after the items it outlines, in the same Box as their [SelectionGlow]. [scale] follows
 * an item that grows when picked up or pressed.
 *
 * Items keep their steady gray outline even while focused (so none disappears while the ring is on its way to the next one). Give
 * [underStrength] and [underDepth] (the outline's strength at its edge, and how far it reaches in) and the ring, once it is on its item,
 * is drawn so that the two together look exactly like the ring alone.
 */
@Composable
fun FocusRing(
    target: GlowRect?,
    fadeDepth: Dp,
    modifier: Modifier = Modifier,
    color: Color = OutlineFocused,
    pulsing: Boolean = true,
    intensity: () -> Float = { 1f },
    scale: () -> Float = { 1f },
    underStrength: Float = 0f,
    underDepth: Dp = fadeDepth,
) {
    val pulse = LocalFocusPulse.current
    val h = rememberHighlight(target)
    val visible by remember { derivedStateOf { h.fade.value > 0.01f } }
    if (!visible) return

    Box(
        modifier = modifier
            .placeHighlight(h, 0f)
            .graphicsLayer {
                scaleX = scale()
                scaleY = scale()
            }
            .drawBehind {
                val strength = h.fade.value * intensity().coerceIn(0f, 1f) * (if (pulsing) pulse() else 1f)
                // The steady outline only counts once the ring has arrived on its item (on the way, it lies between two of them).
                val arrived = if (underStrength > 0f) {
                    (1f - (kotlin.math.abs(h.x.value - h.targetX) + kotlin.math.abs(h.y.value - h.targetY)) / ArrivedWithin).coerceIn(0f, 1f)
                } else {
                    0f
                }
                drawFadingOutline(
                    h.corner.value.dp.toPx(), fadeDepth.toPx(), strength, color,
                    underStrength = underStrength * arrived, underDepth = underDepth.toPx()
                )
            }
    )
}
