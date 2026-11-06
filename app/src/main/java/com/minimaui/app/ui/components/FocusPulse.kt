package com.minimaui.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first

/** Brightness factor of the focus highlight, fading between [PulseMin] and 1 like the PSP's XMB. Read it at draw time. */
val LocalFocusPulse = staticCompositionLocalOf<() -> Float> { { 1f } }

/** True once nothing has been touched for a while (or the screen is dimmed): everything that moves on its own holds still. */
val LocalResting = compositionLocalOf { false }

/** True while the idle screen is dimmed: scrolling titles hold still then, so the screen can run at its slow rate. */
val LocalDimmed = staticCompositionLocalOf { false }

private const val PulseMin = 0.4f
private const val PulseHalfPeriodMillis = 900

// The pulse is slow, so it steps on the shared beat (30 a second, see Beat.kt) however fast the display runs.

/**
 * Runs one shared pulse for the whole UI, so every focused element breathes in step. It sleeps between steps (instead of
 * following every frame of a 120 Hz display), so a still screen is redrawn only as often as the pulse needs. While [idle] (no
 * input for a while) or [dimmed] it runs on to the brightest point, holds there and stops drawing; the first input starts it
 * again from that point, so the glow doesn't jump.
 */
@Composable
fun ProvideFocusPulse(dimmed: Boolean = false, idle: Boolean = false, content: @Composable () -> Unit) {
    val pulse = remember { mutableFloatStateOf(PulseMin) }
    val resting by rememberUpdatedState(idle || dimmed)
    LaunchedEffect(Unit) {
        var origin = 0L
        var resume = false
        while (true) {
            var atPeak = false
            var frame = 0L
            withFrameNanos { now ->
                frame = now
                val millis = now / 1_000_000L
                // Starting again after a rest: begin at the peak.
                if (resume) {
                    origin = millis - PulseHalfPeriodMillis
                    resume = false
                }
                val phase = ((millis - origin) % (2L * PulseHalfPeriodMillis)).toFloat() / PulseHalfPeriodMillis
                val t = if (phase <= 1f) phase else 2f - phase
                val eased = FastOutSlowInEasing.transform(t)
                pulse.floatValue = PulseMin + (1f - PulseMin) * eased
                atPeak = eased >= 0.99f
            }
            if (resting && atPeak) {
                snapshotFlow { resting }.first { !it }
                resume = true
            } else {
                awaitNextBeat(frame)
            }
        }
    }
    val reader = remember { { pulse.floatValue } }
    CompositionLocalProvider(LocalFocusPulse provides reader, LocalDimmed provides dimmed, LocalResting provides (idle || dimmed), content = content)
}
