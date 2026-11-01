package com.minimaui.app.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** The app's motion language: a progressive blur with a fade for things appearing and leaving, ease in/out for slides. */
val EaseInOut = FastOutSlowInEasing

/** Blurs the content by [amount] (0 = sharp, 1 = [maxBlur]), read in the draw phase so animating it never recomposes. */
fun Modifier.progressiveBlur(maxBlur: Dp = 18.dp, amount: () -> Float): Modifier = graphicsLayer {
    val radius = amount().coerceIn(0f, 1f) * maxBlur.toPx()
    renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
}

/**
 * Where a switch goes, besides blurring and fading. [Forward] goes into something (the new screen grows into place while the old
 * one swells away, as a launched game does), [Back] comes out of it (the reverse), and [Left] / [Right] move between screens
 * that sit side by side (the content slides the way the new one lies). So depth always means "in" or "out", and sideways means "next to".
 */
enum class SwitchMotion { None, Forward, Back, Left, Right }

/** How far a game launch zooms the screen (1 = this much more than its size): in as it fades out, back out as the screen returns. */
const val LaunchZoom = 0.07f

private const val DepthScale = 0.06f

/**
 * Runs [block] with a frame clock that hands out every other frame, so the animations started in it step (and draw) at half the
 * display's rate: 60 on a 120 Hz screen. Only the animations run inside [block] are slowed, everything else keeps its rate.
 */
suspend fun <R> withHalfRateFrames(block: suspend () -> R): R {
    val base = coroutineContext[MonotonicFrameClock] ?: return block()
    return withContext(HalfRateClock(base)) { block() }
}

private class HalfRateClock(private val base: MonotonicFrameClock) : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        base.withFrameNanos { } // one frame goes by unused
        return base.withFrameNanos(onFrame)
    }
}

/**
 * Switches between [targetState]s: the old one blurs and fades out while the new one comes into focus, moving as [motion] says.
 * The move runs at half the display's rate (every other frame is drawn).
 */
@OptIn(ExperimentalTransitionApi::class)
@Composable
fun <T> BlurFadeSwitch(
    targetState: T,
    modifier: Modifier = Modifier,
    motion: (from: T, to: T) -> SwitchMotion = { _, _ -> SwitchMotion.None },
    content: @Composable (T) -> Unit
) {
    // Going in or out blurs more than moving sideways.
    val depth = remember { booleanArrayOf(false) }
    val seekable = remember { SeekableTransitionState(targetState) }
    // Moving sideways runs at the display's full rate (a big move shows every step); going in or out, at half.
    val sideways = remember { booleanArrayOf(false) }
    LaunchedEffect(targetState) {
        val going = motion(seekable.currentState, targetState)
        sideways[0] = going == SwitchMotion.Left || going == SwitchMotion.Right
        if (sideways[0]) seekable.animateTo(targetState) else withHalfRateFrames { seekable.animateTo(targetState) }
    }
    val switch = rememberTransition(seekable, label = "blurFadeSwitch")
    switch.AnimatedContent(
        modifier = modifier,
        transitionSpec = {
            val fadeIn = fadeIn(tween(280, easing = EaseInOut))
            val fadeOut = fadeOut(tween(220, easing = EaseInOut))
            val move = tween<Float>(340, easing = EaseInOut)
            val slide = tween<IntOffset>(420, easing = EaseInOut)
            val going = motion(this.initialState, this.targetState)
            depth[0] = going == SwitchMotion.Forward || going == SwitchMotion.Back
            when (going) {
                SwitchMotion.None -> fadeIn togetherWith fadeOut
                SwitchMotion.Forward ->
                    (fadeIn + scaleIn(move, initialScale = 1f - DepthScale)) togetherWith (fadeOut + scaleOut(move, targetScale = 1f + DepthScale))
                SwitchMotion.Back ->
                    (fadeIn + scaleIn(move, initialScale = 1f + DepthScale)) togetherWith (fadeOut + scaleOut(move, targetScale = 1f - DepthScale))
                SwitchMotion.Left ->
                    (fadeIn + slideInHorizontally(slide) { it / 6 }) togetherWith (fadeOut + slideOutHorizontally(slide) { -it / 6 })
                SwitchMotion.Right ->
                    (fadeIn + slideInHorizontally(slide) { -it / 6 }) togetherWith (fadeOut + slideOutHorizontally(slide) { it / 6 })
            }
        },
    ) { state ->
        val blur by transition.animateFloat(transitionSpec = { tween(260, easing = EaseInOut) }, label = "switchBlur") {
            if (it == EnterExitState.Visible) 0f else 1f
        }
        // How far this screen has come in (0..1, and it stays 1 while it leaves): the items on it settle in one after another ([cascade]).
        // It restarts whenever the screen comes in again, even if it was still on its way out.
        val arrival = remember { Animatable(if (transition.currentState == EnterExitState.Visible) 1f else 0f) }
        val left = remember { booleanArrayOf(false) }
        LaunchedEffect(transition.targetState) {
            if (transition.targetState == EnterExitState.Visible) {
                if (left[0] || transition.currentState != EnterExitState.Visible) {
                    left[0] = false
                    arrival.snapTo(0f)
                    if (sideways[0]) arrival.animateTo(1f, tween(CascadeMs.toInt(), easing = LinearEasing))
                    else withHalfRateFrames { arrival.animateTo(1f, tween(CascadeMs.toInt(), easing = LinearEasing)) }
                }
            } else {
                left[0] = true
            }
        }
        // A screen inside another one is only in when both are.
        val outer = LocalArrival.current
        val combined = remember(outer, arrival) { { outer() * arrival.value } }
        CompositionLocalProvider(LocalArrival provides combined) {
            Box(Modifier.progressiveBlur(if (depth[0]) 34.dp else 18.dp) { blur }) { content(state) }
        }
    }
}

/** How far the screen the items are on has come in (0..1); read it in a draw or layer block, never in composition. */
val LocalArrival = compositionLocalOf<() -> Float> { { 1f } }

private const val CascadeMs = 500f
private const val CascadeStepMs = 30f
private const val CascadeItemMs = 230f

/** How far item [index] has settled in (0..1, eased) while its screen arrives ([arrival] 0..1): each one starts a little after the one before. */
fun cascade(arrival: Float, index: Int): Float {
    val t = ((arrival * CascadeMs - index * CascadeStepMs) / CascadeItemMs).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** For overlays inside an AnimatedVisibility: they come into focus from a soft blur and a slightly smaller size. */
@Composable
fun AnimatedVisibilityScope.OverlayMotion(content: @Composable () -> Unit) {
    val amount by transition.animateFloat(transitionSpec = { tween(240, easing = EaseInOut) }, label = "overlayMotion") {
        if (it == EnterExitState.Visible) 0f else 1f
    }
    Box(
        Modifier
            .progressiveBlur(maxBlur = 22.dp) { amount }
            .graphicsLayer {
                val scale = 1f - 0.035f * amount
                scaleX = scale
                scaleY = scale
            }
    ) { content() }
}

/** A quick dip and spring back whenever [pressTick] changes while [active]: the "pressed" feel of confirming a tile. */
@Composable
fun rememberPressScale(pressTick: Int, active: Boolean): State<Float> {
    val scale = remember { Animatable(1f) }
    var seen by remember { mutableIntStateOf(pressTick) }
    val isActive by rememberUpdatedState(active)
    LaunchedEffect(pressTick) {
        if (pressTick == seen) return@LaunchedEffect
        seen = pressTick
        if (isActive) {
            scale.animateTo(0.94f, tween(70, easing = EaseInOut))
            scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
        }
    }
    return scale.asState()
}

/** How much a list row grows (in design px) at its full effect; the XMB and the settings pick their own amounts. */
const val ListGrow = 56f

/**
 * The growth of a row when the cursor is [distance] rows away: [full] on the focused row, [neighbor] of that on the
 * rows next to it, fading to nothing two rows away. Fractional distances (a gliding list) interpolate.
 */
fun listGrowth(distance: Float, full: Float = ListGrow, neighbor: Float = 0.5f): Float {
    val d = kotlin.math.abs(distance)
    return when {
        d < 1f -> full * (1f - d * (1f - neighbor))
        d < 2f -> full * neighbor * (2f - d)
        else -> 0f
    }
}

/** Growth that fades out evenly over three rows: [full] on the focused row, then 2/3, 1/3 and nothing. */
fun gradualGrowth(distance: Float, full: Float): Float = (full * (1f - kotlin.math.abs(distance) / 3f)).coerceAtLeast(0f)

private const val RadialBlurAgsl = """
uniform shader content;
uniform float2 center;
uniform float radius;
uniform float blur;
half4 main(float2 p) {
    float m = pow(1.0 - smoothstep(0.0, 1.0, distance(p, center) / radius), 1.6);
    float r = blur * m;
    if (r < 0.5) return content.eval(p);
    half4 sum = content.eval(p);
    float total = 1.0;
    // A golden-angle spiral, turned a little differently at every pixel, so the blur is smooth instead of dotted.
    float turn = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453) * 6.2831853;
    for (int i = 1; i <= 40; i++) {
        float t = float(i) / 40.0;
        float a = turn + float(i) * 2.3999632;
        float2 o = float2(cos(a), sin(a)) * (sqrt(t) * r);
        float w = 1.0 - 0.6 * t;
        sum += content.eval(p + o) * w;
        total += w;
    }
    return sum / total;
}
"""

/**
 * Blurs only the middle of the content: strongest at the centre, fading out toward [radiusFraction] of the height,
 * so the rest of the screen stays sharp. [amount] (0..1) scales it and is read at draw time.
 */
fun Modifier.centerBlur(maxBlur: Dp = 26.dp, radiusFraction: Float = 1.15f, amount: () -> Float): Modifier = graphicsLayer {
    val strength = amount().coerceIn(0f, 1f)
    renderEffect = if (strength > 0.01f) {
        val shader = android.graphics.RuntimeShader(RadialBlurAgsl)
        shader.setFloatUniform("center", size.width / 2f, size.height / 2f)
        shader.setFloatUniform("radius", size.height * radiusFraction)
        shader.setFloatUniform("blur", maxBlur.toPx() * strength)
        android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    } else {
        null
    }
}

private const val SpotBlurAgsl = """
uniform shader content;
uniform float2 center;
uniform float2 radii;
uniform float blur;
half4 main(float2 p) {
    float m = pow(1.0 - smoothstep(0.0, 1.0, length((p - center) / radii)), 1.6);
    float r = blur * m;
    if (r < 0.5) return content.eval(p);
    half4 sum = content.eval(p);
    float total = 1.0;
    float turn = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453) * 6.2831853;
    for (int i = 1; i <= 40; i++) {
        float t = float(i) / 40.0;
        float a = turn + float(i) * 2.3999632;
        float2 o = float2(cos(a), sin(a)) * (sqrt(t) * r);
        float w = 1.0 - 0.6 * t;
        sum += content.eval(p + o) * w;
        total += w;
    }
    return sum / total;
}
"""

/**
 * Like [centerBlur], but around a point of the screen and stretched into an ellipse of [radiusX] x [radiusY] design
 * px (the content is laid out in design px), for a wide, short spot such as the volume and brightness bars at the top.
 */
fun Modifier.spotBlur(centerX: Float, centerY: Float, radiusX: Float, radiusY: Float, maxBlur: Dp = 22.dp, amount: () -> Float): Modifier = graphicsLayer {
    val strength = amount().coerceIn(0f, 1f)
    renderEffect = if (strength > 0.01f) {
        val unit = size.width / 1920f
        val shader = android.graphics.RuntimeShader(SpotBlurAgsl)
        shader.setFloatUniform("center", centerX * unit, centerY * unit)
        shader.setFloatUniform("radii", radiusX * unit, radiusY * unit)
        shader.setFloatUniform("blur", maxBlur.toPx() * strength)
        android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    } else {
        null
    }
}
