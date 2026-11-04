package com.minimaui.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Environment
import android.os.SystemClock
import java.io.File

/**
 * The UI sounds MinimaUI can play; each is `<file>.wav` (or .ogg/.mp3/.m4a) in MinimaUI/Sounds. A missing one falls back to the
 * sound named in [fallback] if that one is there, else to the stock click, except an [optional] one, which is then just silent.
 */
enum class Sfx(val file: String, val fallback: Sfx? = null, val optional: Boolean = false) {
    Navigate("navigation"),
    MenuNavigate("settings_menu_navigation"),
    /** Opening the launcher menu (the nine-dot one). */
    Menu("menu", fallback = MenuNavigate),
    /** Pushing against the end of a page, list or menu (switched off for now: see EdgeSoundOn in HomeRoot). */
    Edge("edge", optional = true),
    /** Something went wrong: a game that won't launch, a failed backup or restore. Silent without the file. */
    Error("error", optional = true),
    /** Entering Edit Mode. */
    EditMode("edit_mode", optional = true),
    PageSwitch("page_switch"),
    AppLaunch("app_launch"),
    GameLaunch("game_launch"),
    AppExit("app_exit"),
    GameExit("game_exit"),
    CancelConfirm("cancel_confirm"),
    Keyboard("keyboard"),
    Toast("toast_menu"),
    ModeSwitch("mode_switch"),
}

/**
 * UI sounds at a fixed 65% volume: the user's own files from MinimaUI/Sounds when present, otherwise the stock
 * Android click (its own sample, so no bundled audio and no dependence on the system "touch sounds" setting).
 */
class UiSounds(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val clickId: Int? = CLICK_FILES.firstNotNullOfOrNull { path ->
        File(path).takeIf { it.canRead() }?.let { pool.load(path, 1) }
    }
    private val custom = mutableMapOf<Sfx, Int>()
    private val loadedStamp = mutableMapOf<Sfx, Long>()
    private var lastPlayed = 0L

    init {
        loadCustom()
    }

    /** (Re)reads MinimaUI/Sounds; call again after storage access is granted. Reloads any file that changed. */
    fun loadCustom() {
        val folder = File(Environment.getExternalStorageDirectory(), "MinimaUI/Sounds")
        for (sfx in Sfx.entries) {
            val file = EXTENSIONS.map { File(folder, "${sfx.file}.$it") }.firstOrNull { it.canRead() } ?: continue
            // Reload a file the user has replaced since it was loaded.
            val stamp = file.lastModified() xor file.length()
            if (sfx in custom && loadedStamp[sfx] == stamp) continue
            custom[sfx]?.let { pool.unload(it) }
            custom[sfx] = pool.load(file.path, 1)
            loadedStamp[sfx] = stamp
        }
    }

    /** Plays [sfx] (or the stock click when there is no file for it), at most once every [MIN_GAP_MS] for the cursor sounds. */
    fun play(sfx: Sfx) {
        val id = custom[sfx] ?: sfx.fallback?.let { custom[it] }
        if (id != null) {
            pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
        } else if (!sfx.optional) {
            playClick()
        }
    }

    /** Plays the click, at most once every [MIN_GAP_MS] so holding a direction doesn't buzz. */
    fun playClick() {
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayed < MIN_GAP_MS) return
        lastPlayed = now
        val id = clickId
        if (id != null) {
            pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
        } else {
            // No readable sample on this ROM: fall back to the system effect (honours "touch sounds").
            audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, VOLUME)
        }
    }

    fun release() = pool.release()

    private companion object {
        const val VOLUME = 0.65f
        const val MIN_GAP_MS = 60L
        val EXTENSIONS = listOf("wav", "ogg", "mp3", "m4a")
        val CLICK_FILES = listOf("Effect_Tick.ogg", "KeypressStandard.ogg").flatMap { name ->
            listOf("/product", "/system", "/system_ext", "/vendor").map { root -> "$root/media/audio/ui/$name" }
        }
    }
}
