package com.minimaui.app.ui.components

import androidx.compose.runtime.getValue
import android.graphics.RuntimeShader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How much brighter the focused outline's fade is than the plain curve (its edge stays at full). */
const val FocusedOutlineGain = 1.25f

/**
 * The unfocused version of the soft outline: the same fade as the focused one, but neutral and steady, and a little
 * darker than the focused outline at its brightest (which blinks up to 1). Use it on every
 * cover, tile, pill and icon that is not focused.
 */
const val UnfocusedOutline = 0.36f

/**
 * The third, dimmest state: the steady outline of things in the background (panels, dialogs, the status pill, empty
 * slots). It never blinks and never changes, so it has no strength to animate.
 */
const val BackgroundOutline = 0.22f

// Distance to the rounded rectangle's edge, turned into the fade: strength * (1 - depth fraction)^2, the same curve as the
// stacked strokes this replaced, but in a single draw (the output is premultiplied alpha).
private const val OutlineAgsl = """
uniform float2 size;
uniform float radius;
uniform float depth;
uniform float strength;
uniform float3 tint;

half4 main(float2 p) {
    float2 h = size * 0.5;
    float2 q = abs(p - h) - (h - float2(radius));
    float outside = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float coverage = clamp(0.5 - outside, 0.0, 1.0);
    if (coverage <= 0.0) return half4(0.0);
    float t = clamp(-outside / depth, 0.0, 1.0);
    float a = min(strength * (1.0 - t) * (1.0 - t), 0.99) * coverage;
    return half4(half3(tint) * a, a);
}
"""

// Every outline sets its own values right before it draws; a shader's recorded draw keeps the values it had then.
private val outlineShader by lazy { RuntimeShader(OutlineAgsl) }

// Android's "on" quick-settings tile: a lifted dark interior with a bright rim fading inward (edge 0.63 to the status pill's fill, 0.047, over
// the ramp depth), plus a faint white rim over it. Opaque, scaled in alpha by [amount]; the output is premultiplied alpha.
private const val QuickTileOnAgsl = """
uniform float2 size;
uniform float radius;
uniform float rampDepth;
uniform float ringDepth;
uniform float ringStrength;
uniform float rampStrength;
uniform float amount;

half4 main(float2 p) {
    float2 h = size * 0.5;
    float2 q = abs(p - h) - (h - float2(radius));
    float outside = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float coverage = clamp(0.5 - outside, 0.0, 1.0);
    if (coverage <= 0.0) return half4(0.0);
    float d = max(-outside, 0.0);
    float t = clamp(d / rampDepth, 0.0, 1.0);
    float g = 0.047 + (0.63 - 0.047) * (1.0 - t) * (1.0 - t) * rampStrength;
    float tr = clamp(d / ringDepth, 0.0, 1.0);
    float ring = ringStrength * (1.0 - tr) * (1.0 - tr);
    float3 col = mix(float3(g), float3(1.0), ring);
    float a = coverage * amount;
    return half4(half3(col * a), half(a));
}
"""

private val quickTileOnShader by lazy { RuntimeShader(QuickTileOnAgsl) }

/**
 * The look of an "on" quick-settings tile over the whole rounded rectangle (see [QuickTileOnAgsl]); [scale] sizes the rim
 * for bigger tiles (1 is a quick tile's own), [amount] fades it in (0..1), and [ring] scales its faint white rim and the
 * bright edges that fade inward (a blink).
 */
fun DrawScope.drawQuickTileOn(radius: Float, scale: Float, amount: Float, ring: Float = 1f) {
    if (amount <= 0.01f) return
    val shader = quickTileOnShader
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("radius", radius.coerceIn(0f, minOf(size.width, size.height) / 2f))
    shader.setFloatUniform("rampDepth", 14.dp.toPx() * scale)
    shader.setFloatUniform("ringDepth", 10.dp.toPx() * scale)
    shader.setFloatUniform("ringStrength", 0.26f * ring)
    shader.setFloatUniform("rampStrength", ring)
    shader.setFloatUniform("amount", amount.coerceIn(0f, 1f))
    drawRect(ShaderBrush(shader))
}

/**
 * A soft outline along the edge of a rounded rectangle that is [color] at the very edge and fades to transparent
 * toward the inside over [depth] px (like the Vita's focus glow). [radius] is the shape's corner radius and [strength]
 * scales the whole thing (use it to blink or fade it).
 */
fun DrawScope.drawFadingOutline(radius: Float, depth: Float, strength: Float, color: Color = Color.White, gain: Float = FocusedOutlineGain) {
    if (strength <= 0.01f || depth < 1f) return
    val shader = outlineShader
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("radius", radius.coerceIn(0f, minOf(size.width, size.height) / 2f))
    shader.setFloatUniform("depth", depth)
    shader.setFloatUniform("strength", strength * gain)
    shader.setFloatUniform("tint", color.red, color.green, color.blue)
    drawRect(ShaderBrush(shader))
}

/** Draws the soft outline over the content, [strength] read at draw time; [corner] null means a circle. */
fun Modifier.softOutline(depth: Dp, corner: Dp? = null, gain: Float = 1f, strength: () -> Float): Modifier = drawWithContent {
    drawContent()
    val s = strength()
    if (s > 0.01f) {
        drawFadingOutline(corner?.toPx() ?: (minOf(size.width, size.height) / 2f), depth.toPx(), s, gain = gain)
    }
}

/** Like [softOutline] but behind the content (so text and icons stay clean), for pills and rows whose focus changes. */
fun Modifier.pillOutline(depth: Dp, corner: Dp? = null, strength: () -> Float): Modifier = drawBehind {
    val s = strength()
    if (s > 0.01f) {
        drawFadingOutline(corner?.toPx() ?: (minOf(size.width, size.height) / 2f), depth.toPx(), s, gain = 1f)
    }
}

/** The gray outline of an item that can take the focus: eases out while [focused] (the focus ring takes over) and back in after. */
@Composable
fun Modifier.focusFadeOutline(focused: Boolean, depth: Dp, corner: Dp? = null): Modifier {
    val strength by animateFloatAsState(if (focused) 0f else UnfocusedOutline, tween(200), label = "focusFadeOutline")
    return pillOutline(depth, corner) { strength }
}

/** The steady, gray outline of an item that is not focused and never will be at the moment (static, behind the content). */
fun Modifier.unfocusedOutline(depth: Dp, corner: Dp? = null): Modifier = drawBehind {
    drawFadingOutline(corner?.toPx() ?: (minOf(size.width, size.height) / 2f), depth.toPx(), UnfocusedOutline, gain = 1f)
}

/** The dim, static outline of a panel, dialog or pill in the background (behind the content). It cannot blink. */
fun Modifier.backgroundOutline(depth: Dp, corner: Dp? = null): Modifier = drawBehind {
    drawFadingOutline(corner?.toPx() ?: (minOf(size.width, size.height) / 2f), depth.toPx(), BackgroundOutline, gain = 1f)
}

/** The fade depth that suits an item of [height]: deeper on bigger items, between 14 and 24 dp (the covers use 15 to 28). */
fun outlineDepthFor(height: Float): Dp = (height * 0.2f).coerceIn(14f, 24f).dp
