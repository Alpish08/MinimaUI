package com.minimaui.app.audio

import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Loops the user's background track (or, in shuffle mode, plays the files of MinimaUI/Music in a random order, each once before
 * any repeats) while the frontend is on screen and the toggle is on.
 * Playback pauses (position kept) when the app leaves the foreground, e.g. while a game runs. The music fades
 * out as a game launches and fades back in when the frontend returns (or when it first starts).
 */
class BgmPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var loadedUri: String? = null
    private var prepared = false

    private var enabled = false
    private var uri: String? = null
    private var shuffle = false
    // What the loaded player was made for, so a change of mode or track loads a new one.
    private var loadedShuffle = false
    private var queue = mutableListOf<String>()
    private var lastPlayed: String? = null
    private var failures = 0
    private var foreground = false
    private var volume = 1f

    // 0..1, animated: the music is faded by this on top of the user's volume.
    private var fade = 1f
    private var fadeTarget = 1f
    private var fader: ValueAnimator? = null

    fun update(enabled: Boolean, uri: String?, shuffle: Boolean = false) {
        this.enabled = enabled
        this.uri = uri
        this.shuffle = shuffle
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
        val custom = uri
        if (custom == null && !shuffle) {
            release()
            return
        }
        if (!(enabled && foreground)) {
            player?.takeIf { prepared && it.isPlaying }?.pause()
            fader?.cancel()
            return
        }
        if (player == null || loadedShuffle != shuffle || (!shuffle && loadedUri != custom)) {
            failures = 0
            load()
        }
        player?.takeIf { prepared && !it.isPlaying }?.let(::startWithFade)
    }

    /** The audio files of MinimaUI/Music. */
    private fun musicFiles(): List<String> =
        File(Environment.getExternalStorageDirectory(), "MinimaUI/Music").listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
            ?.map { Uri.fromFile(it).toString() }
            .orEmpty()

    /** The next shuffled track, or null if the folder has no music. Every file plays once before the order is shuffled again. */
    private fun nextShuffled(): String? {
        if (queue.isEmpty()) {
            val files = musicFiles()
            if (files.isEmpty()) return null
            queue = files.shuffled().toMutableList()
            // Don't start a new round with the track that just ended.
            if (queue.size > 1 && queue.first() == lastPlayed) queue.add(queue.removeAt(0))
        }
        return queue.removeAt(0).also { lastPlayed = it }
    }

    /** Loads the track to play now: the next shuffled one, or the picked track on repeat (also when the folder has no music). */
    private fun load() {
        loadedShuffle = shuffle
        val shuffled = if (shuffle) nextShuffled() else null
        if (shuffled != null) load(shuffled, looping = false) else uri?.let { load(it, looping = true) } ?: release()
    }

    private fun load(track: String, looping: Boolean) {
        release()
        loadedShuffle = shuffle
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, Uri.parse(track))
                isLooping = looping
                setOnPreparedListener {
                    prepared = true
                    failures = 0
                    if (enabled && foreground && (loadedShuffle == shuffle)) startWithFade(it)
                }
                // A shuffled track that ends is followed by the next one.
                if (!looping) setOnCompletionListener { if (enabled && foreground && shuffle) load() }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Background music failed ($what/$extra)")
                    release()
                    // A file that can't be played is skipped (a few in a row means the folder is no use).
                    if (!looping && shuffle && ++failures < MAX_FAILURES && enabled && foreground) load()
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
        const val MAX_FAILURES = 4
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "ogg", "wav", "flac", "aac", "opus")
    }
}
