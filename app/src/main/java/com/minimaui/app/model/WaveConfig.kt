package com.minimaui.app.model

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.random.Random

/** Which white lines of the XMB wave get an under-fade (the red line always does). */
enum class WaveFadeTarget(val key: String) {
    All("all"), Top("top"), Bottom("bottom"), Near("near")
}

/**
 * One value of the wave: its [key] in the text form, the range and [step], the [default], and how Randomize picks it: a value
 * anywhere in [randMin]..[randMax], or, for an effect, only one time in [randOn] (otherwise it is off, at 0). Values in px are
 * in a 960 x 540 design space.
 */
enum class WaveKey(
    val key: String,
    val min: Float, val max: Float, val step: Float, val default: Float,
    val randMin: Float, val randMax: Float,
    val randOn: Float = 1f,
) {
    RedOpacity("rop", 0f, 100f, 1f, 100f, 70f, 100f),
    RedFade("fade", 0f, 100f, 1f, 69f, 0f, 70f),
    RedFadeDepth("depth", 20f, 500f, 10f, 330f, 60f, 300f),
    RedFadeFalloff("soft", 0.5f, 8f, 0.1f, 5.0f, 1.2f, 4f),
    WhiteFade("wfade", 0f, 100f, 1f, 45f, 0f, 70f),
    WhiteFadeDepth("wdepth", 10f, 400f, 10f, 40f, 30f, 200f),
    WhiteFadeFalloff("wsoft", 0.5f, 8f, 0.1f, 2.2f, 1.2f, 4f),
    Dither("dither", 0f, 10f, 1f, 3f, 3f, 3f),
    Speed("speed", 0f, 200f, 5f, 75f, 15f, 140f),
    Randomness("rand", 0f, 100f, 5f, 100f, 30f, 90f),
    Lines("lines", 1f, 10f, 1f, 1f, 1f, 7f),
    Spread("spread", 0f, 60f, 1f, 20f, 0f, 40f),
    Amplitude("amp", 0f, 150f, 1f, 40f, 5f, 85f),
    Wavelength("freq", 30f, 300f, 5f, 100f, 110f, 240f),
    Height("height", 20f, 95f, 1f, 65f, 45f, 80f),
    WhiteOpacity("wop", 0f, 100f, 1f, 50f, 25f, 55f),
    RedWidth("rw", 0.5f, 6f, 0.1f, 1.0f, 1.2f, 3.2f),
    WhiteWidth("ww", 0.5f, 6f, 0.1f, 1.0f, 0.8f, 2.4f),

    // The sheets and their light. Every default leaves the look exactly as it was before these existed (an old wave file reads the same).
    // A line can be a ribbon: a second edge this many design px below it, the band between them filled, with threads in between.
    RibbonWidth("rwide", 0f, 60f, 1f, 0f, 0f, 60f),
    // How much the ribbon's width swings, through zero (where the ribbon twists over and its edges bunch up), 0..100%.
    Twist("twist", 0f, 100f, 1f, 0f, 0f, 100f),
    RibbonFill("rfill", 0f, 100f, 1f, 0f, 0f, 50f),
    Threads("threads", 0f, 3f, 1f, 0f, 0f, 3f),
    // A soft halo around every line (light trails).
    Glow("glow", 0f, 100f, 1f, 0f, 0f, 60f),
    // Depth of field: lines further from the red one get blurrier and dimmer.
    Focus("dof", 0f, 100f, 1f, 0f, 0f, 60f, randOn = 0.65f),
    // A wide glow with a pale core along the middle of the wave.
    Band("band", 0f, 100f, 1f, 0f, 0f, 70f),
    // A highlight that slides slowly along the lines.
    Sheen("sheen", 0f, 100f, 1f, 0f, 0f, 100f),
    Particles("dust", 0f, 100f, 1f, 0f, 10f, 70f, randOn = 0.45f),
    // How far in from the left and right edge everything fades into black, in % of half the width; 0 = no fade. Always the default.
    EdgeFade("edge", 0f, 100f, 1f, 45f, 45f, 45f),
    // The whole wave rises toward one side (-100..100).
    Tilt("tilt", -100f, 100f, 5f, 0f, -35f, 15f),
    // A dark graphite gradient behind the wave instead of pure black.
    Background("bglift", 0f, 100f, 1f, 0f, 0f, 50f),
    // 1 draws the middle line in MinimaUI's red (0 in white too; always on); 1 makes the wave travel the other way.
    RedLine("red", 0f, 1f, 1f, 1f, 1f, 1f),
    Reverse("reverse", 0f, 1f, 1f, 0f, 0f, 1f);

    /** [v] snapped to this slider's step and kept in range. */
    fun snap(v: Float): Float {
        val snapped = min + ((v - min) / step).roundToInt() * step
        return (Math.round(snapped.coerceIn(min, max) * 100f) / 100f)
    }

}

/** How many numbers a seed takes: the phase (0..2 pi) and three 0..1 factors that make a line unlike its neighbours. */
private const val SeedSize = 4
const val WaveSeedCount = 16

/**
 * The look of the XMB wave: a value for every [WaveKey], the [target] of the white under-fades and the [seeds] that make the
 * lines differ (with Randomness at 100% the look depends on them heavily). Immutable; every change makes a new one.
 */
class WaveConfig(val values: FloatArray, val target: WaveFadeTarget, val seeds: FloatArray) {
    operator fun get(key: WaveKey): Float = values[key.ordinal]

    val lineCount get() = this[WaveKey.Lines].toInt() + 1 // the white lines and the red one

    /**
     * A new random look, nothing kept from this one: every value is drawn evenly, on its own, from a range that keeps the
     * wave looking right ([WaveKey.randMin]..[WaveKey.randMax]; depth of field and particles only come with some looks, see
     * [WaveKey.randOn]), with new seeds and a random fade target. The edge fade and the red line always stay as built in.
     * The lines are only kept close enough together that all of them fit on the screen.
     */
    fun randomized(random: Random = Random.Default): WaveConfig {
        fun draw(lo: Float, hi: Float) = lo + random.nextFloat() * (hi - lo)
        val values = FloatArray(WaveKey.entries.size) {
            val key = WaveKey.entries[it]
            val v = when {
                key.randOn < 1f && random.nextFloat() >= key.randOn -> 0f
                // Whole steps (lines, threads, the switches) each as likely as the others.
                key.step >= 1f && key.max - key.min <= 10f -> kotlin.math.floor(draw(key.randMin, key.randMax + 1f)).coerceAtMost(key.randMax)
                else -> draw(key.randMin, key.randMax)
            }
            key.snap(v)
        }
        val lines = values[WaveKey.Lines.ordinal]
        values[WaveKey.Spread.ordinal] = WaveKey.Spread.snap(values[WaveKey.Spread.ordinal].coerceAtMost(260f / lines))
        return WaveConfig(values, WaveFadeTarget.entries.random(random), newSeeds(random))
    }

    /** This look with [key] set to [value] (snapped to its step and range). */
    fun with(key: WaveKey, value: Float): WaveConfig = WaveConfig(values.copyOf().also { it[key.ordinal] = key.snap(value) }, target, seeds)

    /** This look with the white fades on [target]. */
    fun with(target: WaveFadeTarget): WaveConfig = WaveConfig(values, target, seeds)

    /** True if [other] is the same look (every value, the fade target and the seeds). */
    fun sameAs(other: WaveConfig): Boolean = values.contentEquals(other.values) && target == other.target && seeds.contentEquals(other.seeds)

    /** The block of `key = value` lines (and the seeds) that [parse] reads back. */
    fun toText(): String = buildString {
        appendLine("whiteFadeTarget = ${target.key}")
        for (key in WaveKey.entries) appendLine("${key.key} = ${formatPlain(this@WaveConfig[key])}")
        append("seeds = ${seeds.joinToString(",") { formatPlain(it, 4) }}")
    }

    /** Writes every line's (phase, amplitude, wavelength, offset in design px) into [out], four numbers per line from the top, at wave time [t]. */
    fun fillLines(t: Float, out: FloatArray) {
        val n = lineCount
        val k = this[WaveKey.Randomness] / 100f
        for (i in 0 until n) {
            val s = (i % WaveSeedCount) * SeedSize
            val phase0 = seeds[s]
            val a = seeds[s + 1]
            val b = seeds[s + 2]
            val c = seeds[s + 3]
            val ci = i - (n - 1) / 2f
            out[i * 4] = t * (1f + k * (b - 0.5f) * 1.6f) + i * 0.1f * (1f - k) + k * phase0
            out[i * 4 + 1] = this[WaveKey.Amplitude] * (1f + ci * 0.05f) * (1f + k * (a - 0.5f) * 0.9f)
            out[i * 4 + 2] = (100f / this[WaveKey.Wavelength]) * (1f + k * (c - 0.5f) * 0.6f)
            out[i * 4 + 3] = ci * this[WaveKey.Spread]
        }
    }

    companion object {
        /** The look a fresh install starts with: the author's own wave (its text is [DefaultWaveText]; Reseed or Randomize make others). */
        val Default: WaveConfig = parse(DefaultWaveText) ?: error("The built-in wave look is broken")

        /** The text of a custom wave backup file: a note on top, then [toText] (plain `key = value` lines, so it can be edited by hand). */
        fun backupText(config: WaveConfig): String =
            "# MinimaUI custom wave: restore it from Backup / Restore (nothing else changes), or edit the values to fine tune it.\n" +
                "# Keep the seeds line as it is; it decides the exact shape of the lines.\n" + config.toText() + "\n"

        /** The wave in a backup file's [text], or null if it isn't one (it must name at least one of the wave's values). */
        fun fromBackupText(text: String): WaveConfig? {
            val known = WaveKey.entries.map { it.key } + "seeds" + "whiteFadeTarget"
            val named = text.lineSequence().any { line -> line.substringBefore('=', "").trim() in known }
            return if (named) parse(text) else null
        }

        /** Reads text made by [toText] (or typed by hand); lines it doesn't know are skipped, missing ones keep their default. */
        fun parse(text: String): WaveConfig? {
            // (While the built-in look itself is being read there is no Default yet; its text always has valid seeds.)
            val pairs = text.lineSequence().mapNotNull { line ->
                val at = line.indexOf('=')
                if (at < 0) null else line.substring(0, at).trim() to line.substring(at + 1).trim()
            }.toMap()
            if (pairs.isEmpty()) return null
            val values = FloatArray(WaveKey.entries.size) { WaveKey.entries[it].let { key -> pairs[key.key]?.toFloatOrNull()?.let(key::snap) ?: key.default } }
            val target = WaveFadeTarget.entries.firstOrNull { it.key == pairs["whiteFadeTarget"] } ?: WaveFadeTarget.All
            val parsed = pairs["seeds"]?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
            val seeds = if (parsed != null && parsed.size == WaveSeedCount * SeedSize) parsed.toFloatArray() else Default.seeds
            return WaveConfig(values, target, seeds)
        }

        private fun newSeeds(random: Random) = FloatArray(WaveSeedCount * SeedSize).also { for (i in 0 until WaveSeedCount) fillSeed(it, i, random) }

        private fun fillSeed(out: FloatArray, i: Int, random: Random) {
            out[i * SeedSize] = random.nextFloat() * (2f * PI.toFloat())
            for (j in 1 until SeedSize) out[i * SeedSize + j] = random.nextFloat()
        }

        private fun formatPlain(v: Float, decimals: Int = 2): String =
            if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else "%.${decimals}f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
    }
}

/** The built-in wave look (the author's own, 2026-11-07), in the same text form as a custom wave backup file. */
private const val DefaultWaveText = """
whiteFadeTarget = near
rop = 96
fade = 52
depth = 250
soft = 2.3
wfade = 23
wdepth = 190
wsoft = 3.6
dither = 3
speed = 20
rand = 60
lines = 6
spread = 20
amp = 43
freq = 170
height = 54
wop = 48
rw = 2.9
ww = 1.7
rwide = 24
twist = 86
rfill = 5
threads = 3
glow = 8
dof = 21
band = 34
sheen = 86
dust = 63
edge = 45
tilt = -35
bglift = 42
red = 1
reverse = 1
seeds = 5.9667,0.0875,0.5092,0.486,4.2796,0.2511,0.0063,0.3207,2.6268,0.2161,0.6013,0.8665,4.8654,0.2847,0.1705,0.0375,1.1354,0.1747,0.9152,0.7171,3.6642,0.3083,0.8679,0.2882,5.7926,0.1428,0.7686,0.2316,2.5381,0.1009,0.3722,0.9125,4.7422,0.2046,0.991,0.0258,1.7831,0.8448,0.7406,0.1534,4.9521,0.7778,0.8702,0.5635,4.638,0.8499,0.349,0.7985,2.7083,0.5608,0.6559,0.9663,3.6789,0.2056,0.3892,0.6736,1.9183,0.356,0.7694,0.5896,1.3627,0.061,0.5348,0.0606
"""
