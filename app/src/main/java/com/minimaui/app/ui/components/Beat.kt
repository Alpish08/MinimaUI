package com.minimaui.app.ui.components

import kotlinx.coroutines.delay

/**
 * The shared beat of everything that moves on its own (the wave, the focus pulse, a scrolling title): 30 steps a second, all
 * on the same frames. Each runs its own loop, but they all sleep until the next step of one grid of frame times, so they wake
 * for the same frame and the screen is redrawn 30 times a second however many of them are moving (rather than up to three
 * times as often, as loops that each slept their own 1/30 s would drift apart).
 */
const val BeatFps = 30
/** Wakes this long before a step, so the loop's next frame request is in time for the frame on the step. */
private const val BeatLeadNanos = 4_000_000L

/**
 * Sleeps until shortly before the beat's next step after the frame at [frameNanos] (a frame time from withFrameNanos). A frame
 * that came a little early or late counts for the step nearest to it, so jitter never makes a loop run a step twice or skip one.
 * With [speed] 2 it steps twice a beat (60 a second), on every beat frame and one halfway between, for something moving fast.
 */
suspend fun awaitNextBeat(frameNanos: Long, speed: Int = 1) {
    // Steps at exact multiples of 1 s / (30 * speed), worked out in whole numbers so the grids never drift apart: every other
    // step of speed 2 is exactly a step of speed 1.
    val perSecond = BeatFps.toLong() * speed.coerceIn(1, 4)
    val step = (frameNanos * perSecond + 500_000_000L) / 1_000_000_000L + 1
    val wait = step * 1_000_000_000L / perSecond - BeatLeadNanos - System.nanoTime()
    if (wait > 0) delay(wait / 1_000_000L)
}
