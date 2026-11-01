package com.minimaui.app.model

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.random.Random

/** Which white lines of the XMB wave get an under-fade (the red line always does). */
enum class WaveFadeTarget(val key: String) {
    All("all"), Top("top"), Bottom("bottom"), Near("near")
}

/**
 * One value of the wave: its [key] in the text form, the range and [step], the [default], and the range Randomize draws
 * from ([randMin]..[randMax]). Values in px are in a 960 x 540 design space.
 */
enum class WaveKey(
    val key: String,
    val min: Float, val max: Float, val step: Float, val default: Float,
    val randMin: Float, val randMax: Float,
) {
    RedOpacity("rop", 0f, 100f, 1f, 100f, 62f, 80f),
    RedFade("fade", 0f, 100f, 1f, 69f, 12f, 34f),
    RedFadeDepth("depth", 20f, 500f, 10f, 330f, 130f, 260f),
    RedFadeFalloff("soft", 0.5f, 8f, 0.1f, 5.0f, 1.9f, 3.4f),
    WhiteFade("wfade", 0f, 100f, 1f, 45f, 8f, 32f),
    WhiteFadeDepth("wdepth", 10f, 400f, 10f, 40f, 40f, 140f),
    WhiteFadeFalloff("wsoft", 0.5f, 8f, 0.1f, 2.2f, 1.3f, 2.6f),
    Dither("dither", 0f, 10f, 1f, 3f, 3f, 3f),
    Speed("speed", 0f, 200f, 5f, 75f, 40f, 90f),
    Randomness("rand", 0f, 100f, 5f, 100f, 20f, 60f),
    Lines("lines", 1f, 10f, 1f, 1f, 1f, 7f),
    Spread("spread", 0f, 60f, 1f, 20f, 4f, 10f),
    Amplitude("amp", 0f, 150f, 1f, 40f, 32f, 65f),
    Wavelength("freq", 30f, 300f, 5f, 100f, 120f, 210f),
    Height("height", 20f, 95f, 1f, 65f, 48f, 62f),
    WhiteOpacity("wop", 0f, 100f, 1f, 50f, 18f, 40f),
    RedWidth("rw", 0.5f, 6f, 0.1f, 1.0f, 1.8f, 4.4f),
    WhiteWidth("ww", 0.5f, 6f, 0.1f, 1.0f, 1.2f, 2.6f),
    // How soft the lines are (a bell curve this many design px wide around each, red one too).
    Blur("blur", 0f, 20f, 1f, 1f, 1f, 4f);

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
     * A new random look: every value drawn from its [WaveKey.randMin]..[WaveKey.randMax], which are kept around a look that
     * works. Two things depend on how many lines there are, so a crowd never turns into a mess: a few lines can spread
     * well apart and be brighter, many sit close together and fainter. The white fades mostly cover all the white lines.
     */
    fun randomized(random: Random = Random.Default): WaveConfig {
        fun draw(lo: Float, hi: Float) = lo + random.nextFloat() * (hi - lo)
        val lines = kotlin.math.floor(draw(WaveKey.Lines.randMin, WaveKey.Lines.randMax + 1f)).coerceAtMost(WaveKey.Lines.randMax) // 1..7, each as likely
        val few = lines <= 2f
        val values = FloatArray(WaveKey.entries.size) {
            val key = WaveKey.entries[it]
            val v = when (key) {
                WaveKey.Lines -> lines
                WaveKey.Spread -> if (few) draw(8f, 26f) else draw(key.randMin, key.randMax)
                WaveKey.WhiteOpacity -> draw(key.randMin, (key.randMax - (lines - 1f) * 2f).coerceAtLeast(key.randMin + 4f))
                else -> draw(key.randMin, key.randMax)
            }
            key.snap(v)
        }
        val target = if (random.nextFloat() < 0.6f) WaveFadeTarget.All else WaveFadeTarget.entries.random(random)
        return WaveConfig(values, target, newSeeds(random))
    }

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
        val Default = WaveConfig(
            FloatArray(WaveKey.entries.size) { WaveKey.entries[it].default },
            WaveFadeTarget.All,
            FloatArray(WaveSeedCount * SeedSize).also {
                // Fixed seeds, so the default look is the same everywhere (Reseed or Randomize make others).
                val random = Random(20261027)
                for (i in 0 until WaveSeedCount) fillSeed(it, i, random)
            },
        )

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
