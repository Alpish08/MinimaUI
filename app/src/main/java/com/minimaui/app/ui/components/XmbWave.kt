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
/** The Minima wave: a full-screen background (see [MinimaWave]), behind every page, Favorites included. */
const val WaveMinima = 3

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

/** How far in from the left and right edge (a share of the width) the ribbon fades into black. */
private const val RibbonEdgeFade = 0.2f

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
 * The Minima ribbon: a few lines that sway across the screen, the red one in the middle of them, each with a fade
 * under it, in white and red on black. Everything is in a 960 x 540 design space ([scale] converts it to pixels).
 *
 * Per pixel: the lines' heights and slopes first, then, source-over on black: the white under-fades, the red one's, the
 * white lines, the red line. A fade is (strength * 0.35 * (1 - u) ^ falloff) with u the distance below the line over
 * the fade's depth; half a step of dither noise keeps it from banding. The output is premultiplied.
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
uniform float blur;            // blur of the whole wave (the Apps page)
uniform float lineBlur;        // extra blur of every line, screen px
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
    return base + l.w + sin(a * 0.9 + l.x) * l.y + sin(a * 1.7 - l.x * 0.6) * l.y * 0.3;
}

float lineSlope(float4 l, float x, float w) {
    float a = x / w * 6.2831853 * l.z;
    return (cos(a * 0.9 + l.x) * 0.9 * l.y + cos(a * 1.7 - l.x * 0.6) * 1.7 * l.y * 0.3) * (6.2831853 * l.z / w);
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

half4 main(float2 p) {
    float2 d = p / scale;
    float w = size.x / scale;
    float noise = (fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))) - 0.5) * misc.z / 255.0;
    float ys[$MaxLines];
    float ss[$MaxLines];
    float lift = np > 0.5 ? rippleAt($MaxLines.0, d.x, w) : 0.0;
    for (int i = 0; i < $MaxLines; i++) {
        if (float(i) >= n) break;
        ys[i] = lineY(L[i], d.x, w);
        ss[i] = lineSlope(L[i], d.x, w);
        if (np > 0.5) ys[i] += (rippleAt(float(i), d.x, w) * 255.0 - 127.0) / 127.0 * $RippleRange;
    }
    float4 acc = float4(0.0);
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
            acc = float4(float3(1.0, 0.227, 0.227) * a, a) + acc * (1.0 - a);
        }
    }
    // The white lines, a little fainter the further they are from the red one.
    for (int i = 0; i < $MaxLines; i++) {
        float fi = float(i);
        if (fi >= n) break;
        if (abs(fi - mid) < 0.5) continue;
        float c = cover((d.y - ys[i]) * scale / sqrt(1.0 + ss[i] * ss[i]), misc.y * scale, lineBlur + (blur - 1.0) * 3.3);
        float a = min(c * wht.w * (0.5 + 0.5 * (1.0 - abs(fi - mid) / n)) * misc.w / pow(blur, 0.7) / (1.0 + lineBlur / scale * 0.12) * (1.0 + lift), 1.0);
        acc = float4(a, a, a, a) + acc * (1.0 - a);
    }
    // The red line.
    for (int i = 0; i < $MaxLines; i++) {
        if (float(i) >= n) break;
        if (abs(float(i) - mid) > 0.5) continue;
        float c = cover((d.y - ys[i]) * scale / sqrt(1.0 + ss[i] * ss[i]), misc.x * scale, lineBlur + (blur - 1.0) * 3.3);
        float a = min(c * red.w * misc.w / pow(blur, 0.7) / (1.0 + lineBlur / scale * 0.12) * (1.0 + lift), 1.0);
        acc = float4(float3(1.0, 0.227, 0.227) * a, a) + acc * (1.0 - a);
    }
    // Into black at the left and right edge.
    // Not a smoothstep: that stays near zero for the last stretch, so the lines would already be gone well before the edge.
    float ex = d.x / w;
    float edgeIn = clamp(min(ex, 1.0 - ex) / $RibbonEdgeFade, 0.0, 1.0);
    acc *= 1.0 - pow(1.0 - edgeIn, 1.5);
    return half4(half3(acc.rgb), half(acc.a));
}
"""

/**
 * The wave behind a page: [style] 0 draws nothing, [WaveOriginal] the original four ribbons, [WaveRibbon] the Minima ribbon as [config] says. [soft] blurs the lines (1 = as drawn,
 * 3 = a slight blur) and [brightness] scales everything (1 = full). [pulses] sends ripples across the ribbon (not the original wave).
 */
@Composable
fun XmbWave(style: Int, modifier: Modifier = Modifier, config: WaveConfig = WaveConfig.Default, soft: Float = 1f, brightness: Float = 1f, pulses: WavePulses? = null) {
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
    var time by remember { mutableFloatStateOf(0f) }
    var clock by remember { mutableFloatStateOf(0f) }
    // Only the draw reads [time], so nothing recomposes. The frame clock stops by itself while the app is in the background
    // (and the screen off, or a game on top), and the first step after it resumes is not counted.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                clock = now / 1e9f
                if (last != 0L) {
                    val seconds = ((now - last) / 1e9f).coerceAtMost(0.25f)
                    time += seconds * TimePerSecond * current[WaveKey.Speed] / 100f
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
    Box(
        modifier.drawBehind {
            if (!ribbon) {
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("time", clock % 6283f)
                shader.setFloatUniform("soft", soft.coerceAtLeast(1f))
                shader.setFloatUniform("gain", brightness)
                drawRect(ShaderBrush(shader))
                return@drawBehind
            }
            val c = current
            val scale = size.height / 540f
            val count = c.lineCount
            c.fillLines(time, lines)
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
            drawRect(ShaderBrush(shader))
        }
    )
}

/** How bright the Minima wave is behind the Apps page, and behind Favorites (where it is also blurred by [MinimaFavoritesSoft]). */
const val MinimaAppsDim = 0.6f
const val MinimaFavoritesDim = 0.5f
const val MinimaFavoritesSoft = 4.5f

/** The Minima wave starts this many seconds (at most) into its motion, so it doesn't look the same after every restart. */
private const val MinimaStartSpread = 600f

// The Minima wave, after the PS Vita home screen's wave in MinimaUI's colours: OLED black, a wide soft glow across the lower third with a pale core, and six white
// satin sheets woven through it, one of them in MinimaUI's red. Each sheet has a glossy edge and a soft body under it; the back
// ones are softer, fainter and slower than the front ones, and a sheen slides slowly along the band. Everything is measured on a
// 720 px tall screen (so it looks the same at any size). [dim] darkens it all (Apps, Favorites); [soft] above 1 blurs it (Favorites).
private val MinimaAgsl = """
uniform float2 size;
uniform float time;
uniform float dim;
uniform float soft;

const float3 bgMid = float3(0.0);
const float3 bgLow = float3(0.0);
const float3 bgCorner = float3(0.0);
const float3 sheetC = float3(0.949, 0.949, 0.957);
const float3 coreC = float3(0.863, 0.863, 0.886);
const float3 under = float3(0.290, 0.294, 0.322);
const float3 red = float3(1.0, 0.227, 0.227);

float wave(float x, float t, float k, float a) {
    return a * (0.55 * sin(x * 2.1 + t * 0.19 + k) + 0.30 * sin(x * 3.7 - t * 0.13 + k * 1.7) + 0.15 * sin(x * 6.3 + t * 0.23 + k * 0.6));
}

half4 main(float2 p) {
    float2 uv = p / size;
    float ar = size.x / size.y;
    float x = uv.x * ar;
    float t = time;
    float yc = 0.71 - 0.05 * uv.x + wave(x, t * 0.6, 0.0, 0.035);
    float sx = mod(t * 0.07, ar + 1.6) - 0.8;
    float sheen = exp(-(x - sx) * (x - sx) / 0.30);
    float3 col = mix(float3(0.0), bgMid, smoothstep(0.0, 0.55, uv.y));
    col = mix(col, bgLow, smoothstep(0.85, 1.0, uv.y));
    float cx = uv.x - 1.05;
    float cy = uv.y + 0.05;
    col += bgCorner * exp(-(cx * cx * 2.2 + cy * cy * 6.0));
    float dc = uv.y - yc;
    // Black again well above and below the band.
    col *= 1.0 - 0.6 * max(smoothstep(-0.05, -0.42, dc), smoothstep(0.12, 0.40, dc) * 0.85);
    float g = dc < 0.0 ? exp(-dc * dc / (0.00405 * soft)) : exp(-dc * dc / (0.0198 * soft));
    col = mix(col, under, clamp(g * 0.7, 0.0, 1.0));
    float core = exp(-dc * dc / (0.00144 * soft * (1.0 + 0.5 * sheen))) * (0.72 + 0.22 * sin(x * 1.1 - t * 0.1) + 0.25 * sheen);
    col = mix(col, coreC, clamp(core * 0.68, 0.0, 1.0));
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float z = fi / 5.0;
        float off = (fi - 2.5) * 0.028;
        float yi = yc + off * (0.7 + 0.6 * sin(x * 1.3 + fi * 1.1 + t * 0.05)) + wave(x * 1.15 + fi * 0.7, t * mix(0.62, 1.3, z), fi * 2.4, 0.03 + 0.008 * fi);
        float d = uv.y - yi;
        float dp = d * 720.0;
        // Depth of field focused on the red sheet: the further a sheet is from it, the blurrier (and dimmer) its line.
        float f = abs(z - 0.6) / 0.6;
        float sig = mix(3.6, 38.0, f * f) * soft * soft;
        float body = d > 0.0 ? exp(-d / ((0.035 + 0.02 * mod(fi, 3.0)) * soft)) : 0.0;
        // A light trail: a thin bright core with a wide soft halo around it.
        float edgeL = exp(-dp * dp / sig) * mix(0.95, 0.5, f) + exp(-dp * dp / ((260.0 + 500.0 * f) * soft * soft)) * 0.2;
        float s = 0.5 + 0.5 * sin(x * 2.4 - t * 0.35 + fi * 1.9);
        float a = (body * (0.07 + 0.05 * s + 0.05 * sheen) + edgeL * (0.22 + 0.45 * s * s + 0.55 * sheen)) * 0.9 / mix(1.0, soft, 0.5);
        bool isRed = i == 3;
        col = mix(col, isRed ? red : sheetC, clamp(a * mix(0.55, 1.0, z) * (isRed ? 1.6 : 1.0), 0.0, 1.0));
    }
    float sd = (uv.y - (yc - 0.012)) * 720.0;
    col = mix(col, float3(1.0), clamp(sheen * exp(-sd * sd / (30.0 * soft)) * 0.32, 0.0, 1.0));
    col *= dim;
    float n = fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
    col += (n - 0.5) / 255.0;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

/**
 * The Minima wave behind every page. [dim] scales its brightness (1 on All Games, lower on Apps and Favorites) and [soft]
 * blurs it (1 sharp; Favorites more). Same frame pace and idle freeze as the other waves.
 */
@Composable
fun MinimaWave(dim: Float, soft: Float, modifier: Modifier = Modifier) {
    val shader = remember { RuntimeShader(MinimaAgsl) }
    val resting by rememberUpdatedState(LocalResting.current)
    var time by remember { mutableFloatStateOf(kotlin.random.Random.nextFloat() * MinimaStartSpread) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) time += ((now - last) / 1e9f).coerceAtMost(0.25f)
                last = now
            }
            delay(1000L / WaveFps - 4L)
            if (resting) {
                snapshotFlow { resting }.first { !it }
                last = 0L
            }
        }
    }
    Box(
        modifier.drawBehind {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("dim", dim)
            shader.setFloatUniform("soft", soft.coerceAtLeast(1f))
            drawRect(ShaderBrush(shader))
        }
    )
}
