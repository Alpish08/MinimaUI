package com.minimaui.app.audio

import android.content.Context
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager

/** A weak tick from the device's vibration motor, for page and platform changes. */
class Haptics(context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private var last = 0L

    fun tick() {
        val now = SystemClock.uptimeMillis()
        if (now - last < MIN_GAP_MS) return
        last = now
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(DURATION_MS, AMPLITUDE)) }
    }

    private companion object {
        const val MIN_GAP_MS = 70L
        const val DURATION_MS = 10L
        const val AMPLITUDE = 91 // out of 255: a faint tick
    }
}
