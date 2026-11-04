package com.minimaui.app.overlay

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * Draws MinimaUI's volume / brightness bar over whatever app is in front (the home screen draws its own). It does nothing
 * until the system changes a level, then adds the bar's window for a couple of seconds and removes it again, so it costs
 * next to nothing while idle. Auto-brightness drifts on its own, so it shows no bar.
 */
class LevelOverlayService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var audio: AudioManager
    private var bar: LevelBarView? = null
    private var fade: ValueAnimator? = null
    private var lastBrightness = -1

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) == AudioManager.STREAM_MUSIC) show(volume = true)
        }
    }
    private val brightnessObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            val now = brightness()
            if (now == lastBrightness) return
            lastBrightness = now
            val auto = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            if (auto) return
            show(volume = false)
        }
    }
    private val hide = Runnable { fadeTo(0f, 1000L) { remove() } }

    override fun onServiceConnected() {
        windowManager = getSystemService(WindowManager::class.java)
        audio = getSystemService(AudioManager::class.java)
        lastBrightness = brightness()
        ContextCompat.registerReceiver(
            this, volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_EXPORTED
        )
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, brightnessObserver)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    private val restart = Runnable {
        // MinimaUI in front restarts itself; from anywhere else, it is brought up and told to start fresh.
        onRestartRequested?.invoke() ?: startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setClassName(packageName, "com.minimaui.app.MainActivity")
                .putExtra(com.minimaui.app.EXTRA_RESTART, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    /** The RP6's Home button (scan code 102) arrives as MOVE_HOME; held for [RESTART_HOLD_MS] it restarts MinimaUI. The key is never consumed. */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MOVE_HOME) {
            if (event.action == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(restart)
            } else if (event.repeatCount == 0) {
                handler.removeCallbacks(restart)
                handler.postDelayed(restart, RESTART_HOLD_MS)
            }
        }
        return false
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        fade?.cancel()
        remove()
        runCatching { unregisterReceiver(volumeReceiver) }
        contentResolver.unregisterContentObserver(brightnessObserver)
        super.onDestroy()
    }

    private fun brightness() = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)

    private fun show(volume: Boolean) {
        if (homeActive) return
        val view = bar ?: LevelBarView(this).also {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    // Accessibility overlays draw in software unless asked otherwise, which the outline's shader does not support.
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
            it.barAlpha = 0f
            windowManager.addView(it, params)
            bar = it
        }
        val fraction = if (volume) {
            audio.getStreamVolume(AudioManager.STREAM_MUSIC) / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1).toFloat()
        } else {
            brightness().coerceAtLeast(0) / 255f
        }
        view.set("${if (volume) "Volume" else "Brightness"}: ${(fraction.coerceIn(0f, 1f) * 100f).roundToInt()}%", fraction)
        handler.removeCallbacks(hide)
        fadeTo(1f, 500L)
        handler.postDelayed(hide, 900L)
    }

    private fun fadeTo(target: Float, duration: Long, done: (() -> Unit)? = null) {
        val view = bar ?: return
        fade?.cancel()
        fade = ValueAnimator.ofFloat(view.barAlpha, target).apply {
            this.duration = duration
            interpolator = FastOutSlowIn
            addUpdateListener { view.barAlpha = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) done?.invoke() }
            })
            start()
        }
    }

    private fun remove() {
        bar?.let { runCatching { windowManager.removeView(it) } }
        bar = null
    }

    companion object {
        /** Compose's default easing, which the home screen's bars use, so both fade the same way. */
        private val FastOutSlowIn = PathInterpolator(0.4f, 0f, 0.2f, 1f)

        /** True while MinimaUI is in front, which draws the bars itself. */
        @Volatile
        var homeActive = false

        /** Set by MinimaUI while it is in front: what holding the Home button does there. */
        @Volatile
        var onRestartRequested: (() -> Unit)? = null

        private const val RESTART_HOLD_MS = 5_000L
    }
}
