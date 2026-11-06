package com.minimaui.app.ui.components

import android.graphics.RuntimeShader
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.ShaderBrush
import com.minimaui.app.model.WaveConfig
import com.minimaui.app.model.WaveFadeTarget
import com.minimaui.app.model.WaveKey
import kotlinx.coroutines.delay

/** Draws the XMB wave behind the All Games page (and, blurred and dimmer, the Apps page). This is the master switch; Customize has the one the user flips. */
const val XmbWaveEnabled = true

/** The shoulder buttons send ripples across the ribbon. Off for now (the code stays); the switch is here. Switching between All Games and Apps sends one regardless. */
const val WaveButtonRipples = false

/** The wave styles of the XMB Wave setting. */
const val WaveOff = 0
const val WaveOriginal = 1
const val WaveRibbon = 2

/** How bright the original wave is behind the Apps page (the icons stay in front); the ribbon isn't dimmed. */
const val AppsWaveBrightness = 0.66f

/** The wave is redrawn this many times a second (it moves slowly, so 30 is smooth and a quarter of the cost of 120). */
private const val WaveFps = 30

/** Wave time runs this fast at Speed 100%; it is added up per frame from the real time, so the pace doesn't depend on the frame rate. */
private const val TimePerSecond = 0.36f

/** The most lines the shader draws: ten white ones and the red one. */
private const val MaxLines = 11

/** The most ripples alive at once (a new one pushes out the oldest). */
private const val MaxPulses = 4

/**
 * The ripples reach the shader as a small table (a bitmap, [RippleCols] columns across the screen and one row per line, plus a last row
 * for the brightness lift): the work of the ripples is done here, once per column, not once per pixel. A displacement is stored as a
 * byte, 127 meaning none, [RippleRange] design px at either end.
 */
private const val RippleCols = 96
private const val RippleRange = 64f

/** A ripple lives this long, in seconds; it enters just outside one side, travels across the 960 x 540 design space and dies away. */
private const val PulseSeconds = 2.4f

/**
 * Ripples the shoulder buttons send across the ribbon: each enters from one side as a swell the lines rise in together, with a
 * small ripple riding on it, travels across (slowing as it goes) and brightens the lines a little, then fades. Each lines follows
 * it a little differently, differently for every ripple. Only used on the main thread.
 */
class WavePulses {
    private class Ripple(val fromRight: Boolean, val strength: Float, val startNanos: Long, val seed: Float)

    private val live = ArrayList<Ripple>()

    /** Sends a ripple in from the right side or the left; [strength] 1 is a button of the page flips, higher is bigger. */
    fun fire(fromRight: Boolean, strength: Float = 1f) {
        val now = System.nanoTime()
        live.removeAll { age(it, now) >= PulseSeconds }
        if (live.size >= MaxPulses) live.removeAt(0)
        live.add(Ripple(fromRight, strength, now, kotlin.random.Random.nextFloat()))
    }

    /** Writes the live ripples into [pixels] ([RippleCols] x (lines + 1), see above) and returns how many there are (0: the table is untouched). */
    fun fillTable(pixels: IntArray, heights: FloatArray): Int {
        val now = System.nanoTime()
        var count = 0
        java.util.Arrays.fill(heights, 0f)
        val rows = MaxLines + 1
        for (r in live) {
            val s = age(r, now) / PulseSeconds
            if (s >= 1f) continue
            val travel = 1f - Math.pow((1f - s).toDouble(), 1.6).toFloat()    // fast out of the side, slower as it spreads
            val front = if (r.fromRight) 1060f - 1150f * travel else -100f + 1150f * travel
            val strength = Math.pow((1f - s).toDouble(), 0.9).toFloat() * smooth(s / 0.12f)
            val ripple = 4f * r.strength * strength
            val swell = 15f * r.strength * strength
            val width = 130f + 80f * s
            val lift = 0.22f * r.strength.coerceAtMost(1.5f) * strength
            for (c in 0 until RippleCols) {
                val x = (c + 0.5f) / RippleCols * 960f
                val wide = (x - front) / (width * 2.2f)
                heights[MaxLines * RippleCols + c] += lift / (1f + 0.5f * wide * wide).let { it * it }
                for (i in 0 until MaxLines) {
                    // What each line does differently: its lag, ripple height, phase and swell height.
                    val k = i * 4f + r.seed * 101f
                    val u = x - front - (hash(k) - 0.5f) * 140f
                    val t = u / width
                    val e = 1f / (1f + 0.5f * t * t)
                    val w = t / 2.2f
                    val es = 1f / (1f + 0.5f * w * w)
                    heights[i * RippleCols + c] += ripple * (0.6f + 0.8f * hash(k + 1f)) * e * e *
                        Math.sin((u * 0.021f + (hash(k + 2f) - 0.5f) * 1.2f).toDouble()).toFloat() -
                        swell * (0.75f + 0.5f * hash(k + 3f)) * es * es
                }
            }
            count++
        }
        if (count == 0) return 0
        for (row in 0 until rows) for (c in 0 until RippleCols) {
            val v = heights[row * RippleCols + c]
            val byte = if (row == MaxLines) (v * 255f).toInt() else (v / RippleRange * 127f).toInt() + 127
            val b = byte.coerceIn(0, 255)
            pixels[row * RippleCols + c] = (0xFF shl 24) or (b shl 16) or (b shl 8) or b
        }
        return count
    }

    private fun hash(n: Float): Float = (Math.sin(n.toDouble() * 12.9898) * 43758.5453).let { (it - Math.floor(it)).toFloat() }

    private fun age(r: Ripple, now: Long) = (now - r.startNanos) / 1e9f

    private fun smooth(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}

/** The ribbon (0..3) drawn in MinimaUI's red instead of white, in the original wave. */
private const val OriginalRedLine = 1

// The original wave: four slow, overlapping ribbons: each is a thin bright line with a soft glow and a faint fill under it, in white on black
// (one of them in MinimaUI's red). The output is premultiplied, so it only ever lightens the black (the OLED stays off where
// there is no wave). Half a step of noise is added to the alpha so the very faint gradients don't band.
private val OriginalAgsl = """
uniform float2 size;
uniform float time;
uniform float soft;
uniform float gain;

half4 main(float2 p) {
    float2 uv = p / size;
    if (uv.y < 0.19) return half4(0.0);                 // above the highest ribbon's glow
    float px = size.y / 1080.0;
    float aw = 0.0;
    float ar = 0.0;
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float y = 0.66 + (fi - 1.5) * 0.045
            + 0.085 * sin(uv.x * 3.1 + time * 0.23 + fi * 1.3)
            + 0.045 * sin(uv.x * 5.7 - time * 0.17 + fi * 2.1)
            + 0.020 * sin(uv.x * 11.0 + time * 0.31 + fi * 0.7);
        float d = (uv.y - y) * size.y;                  // px below the ribbon's line
        float sl = 2.2 * soft;                          // soft > 1 blurs the line: wider, and dimmer to keep its energy
        float line = exp(-(d * d) / (2.0 * sl * sl * px * px)) / soft;
        float glow = exp(-abs(d) / (26.0 * sqrt(soft) * px));
        float under = smoothstep(0.0, 40.0 * px, d) * (1.0 - smoothstep(40.0 * px, 260.0 * px, d));
        float a = (line * 0.55 + glow * 0.07 + under * 0.045) / (1.0 + fi * 0.35);
        if (i == ${OriginalRedLine}) { ar += a; } else { aw += a; }
    }
    float edge = smoothstep(0.0, 0.18, uv.x) * (1.0 - smoothstep(0.82, 1.0, uv.x));
    float k = edge * 0.62 * gain;
    aw *= k;
    ar *= k;
    float t = aw + ar;
    if (t > 0.5) { aw *= 0.5 / t; ar *= 0.5 / t; t = 0.5; }
    float n = fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
    float tn = max(t + (n - 0.5) / 255.0, 0.0);
    float3 rgb = t > 0.0001 ? (float3(aw) + ar * float3(1.0, 0.227, 0.227)) * (tn / t) : float3(tn);
    return half4(half3(rgb), half(tn));
}
"""

/**
 * The custom wave: a few lines that sway across the screen, the red one in the middle of them, each with a fade under it,
 * in white and red on black. Everything is in a 960 x 540 design space ([scale] converts it to pixels).
 *
 * Per pixel: the lines' heights and slopes first (and nothing more for pixels well above all of them), then, source-over on
 * black: the background lift and the band glow, the white under-fades, the red one's, the white lines, the red line, the
 * particles. A line can be a ribbon (a second edge [rib].x below it whose distance swings through zero with the twist, the
 * band between them filled, threads in between), have a halo (glow), be blurred by the depth of field, and brighten where
 * the sheen passes. Lines too far from a pixel to light it are skipped. A fade is (strength * 0.35 * (1 - u) ^ falloff) with u the
 * distance below the line over the fade's depth; half a step of dither noise keeps it from banding. The output is premultiplied.
 * Every extra is skipped while its value is 0, so a plain look costs what it always did.
 */
private const val RibbonAgsl = """
uniform float2 size;
uniform float scale;
uniform float4 L[$MaxLines];   // per line: phase, amplitude, wavelength factor, offset
uniform float n;               // lines in all
uniform float mid;             // the red one
uniform float base;            // centre height, design px
uniform float4 red;            // fade strength, fade depth, fade falloff, line opacity
uniform float4 wht;            // the same for the white lines
uniform float4 misc;           // red width, white width, dither, brightness
uniform float2 fadeWho;        // which white lines fade (0 all, 1 top, 2 bottom, 3 near), and a factor for how many
uniform float blur;            // blur of the whole wave (a page's blur)
uniform float lineBlur;        // extra blur of every line, screen px
uniform float4 rib;            // ribbon width (design px), twist 0..1, fill 0..1, threads 0..3
uniform float4 lite;           // glow, band, sheen, depth of field (0..1 each)
uniform float4 more;           // particles 0..1, edge fade (share of the width, 0 = none), tilt -1..1, background 0..1
uniform float2 tone;           // red line 1 / white 0, clock (wave time)
uniform float np;              // ripples alive (0: none, the table is not used)
uniform shader rip;            // the ripples' table: one row per line, the brightness lift in the last (see RippleCols)

// The ripples' table at [x] (design px) in [row], 0..1, blended between its two nearest columns.
float rippleAt(float row, float x, float w) {
    float fx = clamp(x / w * $RippleCols.0 - 0.5, 0.0, $RippleCols.0 - 1.001);
    float c = floor(fx);
    return mix(rip.eval(float2(c + 0.5, row + 0.5)).r, rip.eval(float2(c + 1.5, row + 0.5)).r, fx - c);
}

float lineY(float4 l, float x, float w) {
    float a = x / w * 6.2831853 * l.z;
    return base + l.w + sin(a * 0.9 + l.x) * l.y + sin(a * 1.7 - l.x * 0.6) * l.y * 0.3 + more.z * (x / w - 0.5) * 120.0;
}

float lineSlope(float4 l, float x, float w) {
    float a = x / w * 6.2831853 * l.z;
    return (cos(a * 0.9 + l.x) * 0.9 * l.y + cos(a * 1.7 - l.x * 0.6) * 1.7 * l.y * 0.3) * (6.2831853 * l.z / w) + more.z * 120.0 / w;
}

bool fades(float i) {
    if (fadeWho.x < 0.5) return true;
    if (fadeWho.x < 1.5) return i < 0.5;
    if (fadeWho.x < 2.5) return abs(i - (n - 1.0)) < 0.5;
    return abs(abs(i - mid) - 1.0) < 0.5;
}

// How much of a pixel [distance] px from a line of [width] px the line covers. With [soft] (extra px of blur) it becomes a
// soft glow instead: a bell curve that is wider the more blur there is (the caller dims it a little).
float cover(float distance, float width, float soft) {
    if (soft <= 0.5) return clamp(width * 0.5 - abs(distance) + 0.5, 0.0, 1.0);
    float sigma = width * 0.5 + soft;
    return exp(-distance * distance / (2.0 * sigma * sigma));
}

// The light of one line [dy] design px above the pixel ([k] = 1 / sqrt(1 + slope^2) turns it into the distance across the
// line): its edge, its halo, and as a ribbon its second edge [wi] below, the threads between and the fill.
float sheet(float dy, float k, float wi, float widthPx, float softPx) {
    float px = dy * scale * k;
    float a = cover(px, widthPx, softPx);
    if (lite.x > 0.0) {
        float g = (16.0 + softPx / scale) * scale;
        a += lite.x * 0.35 * exp(-px * px / (2.0 * g * g));
    }
    if (rib.x > 0.0) {
        a += cover((dy - wi) * scale * k, widthPx, softPx) * 0.8;
        for (int t = 1; t <= 3; t++) {
            if (float(t) > rib.w) break;
            a += cover((dy - wi * float(t) / (rib.w + 1.0)) * scale * k, widthPx * 0.8, softPx) * 0.45;
        }
        if (rib.z > 0.0) {
            float lo = min(0.0, wi);
            float hi = max(0.0, wi);
            float e = 0.8 / scale;
            float inside = smoothstep(lo - e, lo + e, dy) * (1.0 - smoothstep(hi - e, hi + e, dy));
            float u = abs(wi) > 0.01 ? clamp(dy / wi, 0.0, 1.0) : 0.0;
            float edgeOn = clamp(12.0 / (abs(wi) + 4.0), 0.4, 2.2);
            a += rib.z * 0.4 * inside * (0.45 + 0.55 * (1.0 - u)) * edgeOn;
        }
    }
    return a;
}

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

half4 main(float2 p) {
    float2 d = p / scale;
    float w = size.x / scale;
    float ex = d.x / w;
    float clock = tone.y;
    float noise = (fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))) - 0.5) * misc.z / 255.0;
    float ys[$MaxLines];
    float lift = np > 0.5 ? rippleAt($MaxLines.0, d.x, w) : 0.0;
    float top = 1.0e5;
    float bottom = -1.0e5;
    float yb = base;   // the red line's height (arrays can only be indexed by the loop)
    for (int i = 0; i < $MaxLines; i++) {
        if (float(i) >= n) break;
        ys[i] = lineY(L[i], d.x, w);
        if (np > 0.5) ys[i] += (rippleAt(float(i), d.x, w) * 255.0 - 127.0) / 127.0 * $RippleRange;
        top = min(top, ys[i] - max(rib.x, 0.0));
        bottom = max(bottom, ys[i]);
        if (abs(float(i) - mid) < 0.5) yb = ys[i];
    }
    // Well above every line, and well below them and their fades, there is nothing to draw, unless the background is lit.
    float reach = 40.0 + lineBlur / scale * 3.0 + lite.x * 50.0 + lite.y * 110.0 + more.x * 110.0 + lite.w * 24.0 + (blur - 1.0) * 30.0;
    if (more.w <= 0.0) {
        if (d.y < top - reach) return half4(0.0);
        float deep = max(red.x > 0.0 ? red.y : 0.0, wht.x > 0.0 ? wht.y : 0.0);
        if (d.y > bottom + max(rib.x, 0.0) + max(deep, reach + lite.y * 120.0)) return half4(0.0);
    }

    // The sheen, sliding along the width.
    float sx = fract(clock * 0.012) * 1.6 - 0.3;
    float sh = lite.z * exp(-(ex - sx) * (ex - sx) / 0.015);
    float3 redC = mix(float3(1.0), float3(1.0, 0.227, 0.227), tone.x);

    float4 acc = float4(0.0);
    if (more.w > 0.0) {
        float g = more.w * 0.07 * smoothstep(0.0, 0.65, d.y / 540.0);
        acc = float4(float3(0.92, 0.92, 1.0) * g, g);
    }
    // The band glow: wide and soft around the red line, with a pale core.
    if (lite.y > 0.0) {
        float dy = d.y - yb;
        float sb = blur;
        float g = dy < 0.0 ? exp(-dy * dy / (2.0 * 30.0 * 30.0 * sb)) : exp(-dy * dy / (2.0 * 70.0 * 70.0 * sb));
        float core = exp(-dy * dy / (2.0 * 13.0 * 13.0 * sb * (1.0 + 0.5 * sh)));
        float a = lite.y * (g * 0.28 + core * (0.42 + 0.25 * sh)) * misc.w;
        acc = float4(float3(0.88, 0.88, 0.9) * a, a) + acc * (1.0 - a);
    }
    // The white under-fades.
    for (int i = 0; i < $MaxLines; i++) {
        float fi = float(i);
        if (fi >= n) break;
        if (abs(fi - mid) < 0.5 || !fades(fi)) continue;
        float u = (d.y - ys[i]) / wht.y;
        if (u > 0.0 && u < 1.0) {
            float a = max(wht.x * 0.35 * pow(1.0 - u, wht.z) * fadeWho.y + noise, 0.0) * misc.w * (1.0 + lift * 0.5);
            acc = float4(a, a, a, a) + acc * (1.0 - a);
        }
    }
    // The red one's.
    for (int i = 0; i < $MaxLines; i++) {
        if (float(i) >= n) break;
        if (abs(float(i) - mid) > 0.5) continue;
        float u = (d.y - ys[i]) / red.y;
        if (u > 0.0 && u < 1.0) {
            float a = max(red.x * 0.35 * pow(1.0 - u, red.z) + noise, 0.0) * misc.w * (1.0 + lift * 0.5);
            acc = float4(redC * a, a) + acc * (1.0 - a);
        }
    }
    float soft0 = lineBlur + (blur - 1.0) * 1.65 * scale;
    float boost = (1.0 + lift) * (1.0 + 1.2 * sh) / pow(blur, 0.7);
    // The white lines, a little fainter the further they are from the red one; then the red line over them.
    for (int pass = 0; pass < 2; pass++) {
        for (int i = 0; i < $MaxLines; i++) {
            float fi = float(i);
            if (fi >= n) break;
            bool isRed = abs(fi - mid) < 0.5;
            if (isRed != (pass == 1)) continue;
            float dof = lite.w * abs(fi - mid) * 3.0 * scale;
            float softPx = soft0 + dof;
            float widthPx = (isRed ? misc.x : misc.y) * scale;
            float wi = rib.x > 0.0 ? rib.x * ((1.0 - rib.y) + rib.y * sin(ex * 6.2831853 * 1.3 + L[i].x * 0.37 + fi * 2.7)) : 0.0;
            // A line too far above or below to light this pixel is skipped (most of them, for most pixels). How far its light
            // reaches straight up or down: its edge, blur and halo across the line, stretched by the line's steepest slope.
            float dy = d.y - ys[i];
            float steep = (1.41 * L[i].y * 6.2831853 * L[i].z + abs(more.z) * 120.0) / w;
            float span = (widthPx * 0.5 + 4.0 * softPx + 1.0 + (lite.x > 0.0 ? 3.5 * (16.0 * scale + softPx) : 0.0)) / scale * sqrt(1.0 + steep * steep);
            if (dy < min(0.0, wi) - span || dy > max(0.0, wi) + span) continue;
            float sl = lineSlope(L[i], d.x, w);
            float k = 1.0 / sqrt(1.0 + sl * sl);
            float c = sheet(dy, k, wi, widthPx, softPx);
            float dim = 1.0 / (1.0 + (lineBlur + dof) / scale * 0.12);
            float opacity = isRed ? red.w : wht.w * (0.5 + 0.5 * (1.0 - abs(fi - mid) / n));
            float a = min(c * opacity * misc.w * dim * boost, 1.0);
            float3 col = isRed ? redC : float3(1.0);
            acc = float4(col * a, a) + acc * (1.0 - a);
        }
    }
    // A few small particles drifting along the wave, twinkling.
    if (more.x > 0.0) {
        float cw = 28.0;
        float drift = clock * 9.0;
        float ci = floor((d.x + drift) / cw);
        for (int j = -1; j <= 1; j++) {
            float id = ci + float(j);
            float h = hash(float2(mod(id, 997.0), 1.7));
            if (h > more.x * 0.55) continue;
            float h2 = hash(float2(mod(id, 991.0) * 1.3, 5.1));
            float h3 = hash(float2(mod(id, 983.0) * 0.7, 8.3));
            float px = (id + h2) * cw - drift;
            float py = yb + (h3 - 0.5) * 150.0;
            float2 dv = float2(d.x - px, d.y - py) * scale;
            float r = (0.9 + 1.0 * h3) * scale;
            float tw = 0.35 + 0.65 * pow(0.5 + 0.5 * sin(clock * (0.6 + h2) * 2.0 + h * 30.0), 2.0);
            float dd = dot(dv, dv);
            float a = min((exp(-dd / (r * r)) * 0.7 + exp(-dd / (r * r * 9.0)) * 0.12) * tw * misc.w / blur, 1.0);
            acc = float4(float3(a), a) + acc * (1.0 - a);
        }
    }
    // Into black at the left and right edge.
    // Not a smoothstep: that stays near zero for the last stretch, so the lines would already be gone well before the edge.
    if (more.y > 0.0) {
        float edgeIn = clamp(min(ex, 1.0 - ex) / more.y, 0.0, 1.0);
        acc *= 1.0 - pow(1.0 - edgeIn, 1.5);
    }
    return half4(half3(acc.rgb), half(acc.a));
}
"""

/**
 * The wave behind a page: [style] 0 draws nothing, [WaveOriginal] the original four ribbons, [WaveRibbon] the custom wave as [config] says. [soft] blurs the lines (1 = as drawn,
 * 3 = a slight blur) and [brightness] scales everything (1 = full). [pulses] sends ripples across the ribbon (not the original wave).
 */
@Composable
fun XmbWave(
    style: Int,
    modifier: Modifier = Modifier,
    config: WaveConfig = WaveConfig.Default,
    soft: Float = 1f,
    brightness: Float = 1f,
    pulses: WavePulses? = null,
    lowRes: Boolean = false,
    timeline: WaveTimeline? = null,
    drives: Boolean = true,
) {
    if (!XmbWaveEnabled || style == WaveOff) return
    val ribbon = style == WaveRibbon
    val shader = remember(ribbon) { RuntimeShader(if (ribbon) RibbonAgsl else OriginalAgsl) }
    val lines = remember { FloatArray(MaxLines * 4) }
    // The ripples' table, in two bitmaps taken in turn (the one being drawn is never written to).
    val rippleBitmaps = remember { Array(2) { android.graphics.Bitmap.createBitmap(RippleCols, MaxLines + 1, android.graphics.Bitmap.Config.ARGB_8888) } }
    val rippleShaders = remember {
        Array(2) {
            android.graphics.BitmapShader(rippleBitmaps[it], android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                .apply { filterMode = android.graphics.BitmapShader.FILTER_MODE_NEAREST }
        }
    }
    val ripplePixels = remember { IntArray(RippleCols * (MaxLines + 1)) }
    val rippleHeights = remember { FloatArray(RippleCols * (MaxLines + 1)) }
    val rippleTurn = remember { intArrayOf(0) }
    val current by rememberUpdatedState(config)
    val livePulses by rememberUpdatedState(pulses)
    val resting by rememberUpdatedState(LocalResting.current)
    val waveClock = timeline ?: remember { WaveTimeline() }
    // Only the draw reads the time, so nothing recomposes. The frame clock stops by itself while the app is in the background
    // (and the screen off, or a game on top), and the first step after it resumes is not counted. A wave that follows
    // another's [timeline] doesn't run it.
    if (drives) LaunchedEffect(waveClock) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                waveClock.clock = now / 1e9f
                if (last != 0L) {
                    val seconds = ((now - last) / 1e9f).coerceAtMost(0.25f)
                    waveClock.time += seconds * TimePerSecond * current[WaveKey.Speed] / 100f * (if (current[WaveKey.Reverse] > 0.5f) -1f else 1f)
                }
                last = now
            }
            // Sleeping between frames (rather than skipping them) lets the app idle: it wakes WaveFps times a second, not on every vsync.
            delay(1000L / WaveFps - 4L)
            // With nothing touched for a while the wave stops where it is (nothing is drawn), until the first input.
            if (resting) {
                snapshotFlow { resting }.first { !it }
                last = 0L
            }
        }
    }
    Box(modifier) {
    Box(
        (if (lowRes) Modifier.halfResolution() else Modifier.fillMaxSize()).drawBehind {
            if (!ribbon) {
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("time", waveClock.clock % 6283f)
                shader.setFloatUniform("soft", soft.coerceAtLeast(1f))
                shader.setFloatUniform("gain", brightness)
                drawRect(ShaderBrush(shader))
                return@drawBehind
            }
            val c = current
            val scale = size.height / 540f
            val count = c.lineCount
            c.fillLines(waveClock.time, lines)
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("scale", scale)
            shader.setFloatUniform("L", lines)
            shader.setFloatUniform("n", count.toFloat())
            shader.setFloatUniform("mid", (count / 2).toFloat())
            shader.setFloatUniform("base", 540f * c[WaveKey.Height] / 100f)
            shader.setFloatUniform("red", c[WaveKey.RedFade] / 100f, c[WaveKey.RedFadeDepth], c[WaveKey.RedFadeFalloff], c[WaveKey.RedOpacity] / 100f)
            shader.setFloatUniform("wht", c[WaveKey.WhiteFade] / 100f, c[WaveKey.WhiteFadeDepth], c[WaveKey.WhiteFadeFalloff], c[WaveKey.WhiteOpacity] / 100f)
            shader.setFloatUniform("misc", c[WaveKey.RedWidth], c[WaveKey.WhiteWidth], c[WaveKey.Dither], brightness)
            // With target "all" the fades share their strength between the white lines.
            val whiteCount = (count - 1).coerceAtLeast(1)
            shader.setFloatUniform("fadeWho", c.target.ordinal.toFloat(), if (c.target == WaveFadeTarget.All) 1f / kotlin.math.sqrt(whiteCount.toFloat()) else 1f)
            shader.setFloatUniform("blur", soft.coerceAtLeast(1f))
            shader.setFloatUniform("lineBlur", c[WaveKey.Blur] * scale)
            val alive = livePulses?.fillTable(ripplePixels, rippleHeights) ?: 0
            if (alive > 0) {
                rippleTurn[0] = 1 - rippleTurn[0]
                rippleBitmaps[rippleTurn[0]].setPixels(ripplePixels, 0, RippleCols, 0, 0, RippleCols, MaxLines + 1)
            }
            shader.setInputShader("rip", rippleShaders[rippleTurn[0]])
            shader.setFloatUniform("np", alive.toFloat())
            shader.setFloatUniform("rib", c[WaveKey.RibbonWidth], c[WaveKey.Twist] / 100f, c[WaveKey.RibbonFill] / 100f, c[WaveKey.Threads])
            shader.setFloatUniform("lite", c[WaveKey.Glow] / 100f, c[WaveKey.Band] / 100f, c[WaveKey.Sheen] / 100f, c[WaveKey.Focus] / 100f)
            shader.setFloatUniform("more", c[WaveKey.Particles] / 100f, c[WaveKey.EdgeFade] / 200f, c[WaveKey.Tilt] / 100f, c[WaveKey.Background] / 100f)
            shader.setFloatUniform("tone", c[WaveKey.RedLine], kotlin.math.abs(waveClock.time) % 5000f)
            drawRect(ShaderBrush(shader))
        }
    )
    }
}

/**
 * The time of a wave, kept outside it: the wave keeps its place when it leaves the screen and comes back, and a second
 * wave (the frosted copy behind the Favorites dock) can follow it exactly.
 */
class WaveTimeline {
    var time by mutableFloatStateOf(0f)
        internal set
    var clock by mutableFloatStateOf(0f)
        internal set
}

/**
 * Lays the content out at half the width and height and draws it into its own layer, which is then shown scaled up to the
 * full size: a quarter of the pixels to shade, for a wave that is blurred anyway.
 */
private fun Modifier.halfResolution(): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed((width + 1) / 2, (height + 1) / 2))
    layout(width, height) {
        placeable.placeWithLayer(0, 0) {
            scaleX = 2f
            scaleY = 2f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
        }
    }
}
