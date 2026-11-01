package com.minimaui.app.audio

import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

/**
 * Loops the user's background track while the frontend is on screen and the toggle is on.
 * Playback pauses (position kept) when the app leaves the foreground, e.g. while a game runs. The music fades
 * out as a game launches and fades back in when the frontend returns (or when it first starts).
 */
class BgmPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var loadedUri: String? = null
    private var prepared = false

    private var enabled = false
    private var uri: String? = null
    private var foreground = false
    private var volume = 1f

    // 0..1, animated: the music is faded by this on top of the user's volume.
    private var fade = 1f
    private var fadeTarget = 1f
    private var fader: ValueAnimator? = null

    fun update(enabled: Boolean, uri: String?) {
        this.enabled = enabled
        this.uri = uri
        apply()
    }

    // Half volume while the screen is dimmed for being idle.
    private var duck = 1f

    private var duckAnimator: ValueAnimator? = null

    /** Eases the music to half volume while the screen is dimmed, and back when it wakes. */
    fun setDimmed(dimmed: Boolean) {
        duckAnimator?.cancel()
        duckAnimator = ValueAnimator.ofFloat(duck, if (dimmed) 0.5f else 1f).apply {
            duration = 1000L
            addUpdateListener {
                duck = it.animatedValue as Float
                applyVolume()
            }
            start()
        }
    }

    fun setVolume(volume: Float) {
        this.volume = volume
        applyVolume()
    }

    /** Fades the music out, e.g. as a game is starting. */
    fun fadeOut() = fadeTo(0f, FADE_OUT_MS)

    /** Brings the music back if it was faded out (a launch that didn't happen, or a game that closed at once). */
    fun fadeIn() {
        if (fadeTarget != 1f) fadeTo(1f, FADE_IN_MS)
    }

    private fun fadeTo(target: Float, millis: Long) {
        fader?.cancel()
        fadeTarget = target
        fader = ValueAnimator.ofFloat(fade, target).apply {
            duration = millis
            addUpdateListener {
                fade = it.animatedValue as Float
                applyVolume()
            }
            start()
        }
    }

    // Squared, so the fade sounds even instead of dropping off at the end.
    private fun applyVolume() {
        val level = volume * duck * fade * fade
        player?.setVolume(level, level)
    }

    /** Starts playback from silence and fades up to the user's volume. */
    private fun startWithFade(player: MediaPlayer) {
        fader?.cancel()
        fade = 0f
        applyVolume()
        player.start()
        fadeTo(1f, FADE_IN_MS)
    }

    fun setForeground(foreground: Boolean) {
        this.foreground = foreground
        apply()
    }

    fun release() {
        player?.release()
        player = null
        loadedUri = null
        prepared = false
    }

    private fun apply() {
        val track = uri
        if (track == null) {
            release()
            return
        }
        if (!(enabled && foreground)) {
            player?.takeIf { prepared && it.isPlaying }?.pause()
            fader?.cancel()
            return
        }
        if (player == null || loadedUri != track) load(track)
        player?.takeIf { prepared && !it.isPlaying }?.let(::startWithFade)
    }

    private fun load(track: String) {
        release()
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, Uri.parse(track))
                isLooping = true
                setOnPreparedListener {
                    prepared = true
                    if (enabled && foreground && uri == track) startWithFade(it)
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Background music failed ($what/$extra)")
                    release()
                    true
                }
                prepareAsync()
            }
            loadedUri = track
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't load background music", e)
            release()
        }
    }

    private companion object {
        const val TAG = "BgmPlayer"
        const val FADE_OUT_MS = 400L
        const val FADE_IN_MS = 1200L
    }
}
