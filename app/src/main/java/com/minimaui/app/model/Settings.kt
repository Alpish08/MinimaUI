package com.minimaui.app.model

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.minimaui.app.data.LibraryStore

/** User-adjustable app settings shown in the start menu. */
class Settings(platforms: List<Platform>, private val store: LibraryStore) {
    var menuSounds by mutableStateOf(store.loadFlag(KEY_MENU_SOUNDS, true))
        private set
    var backgroundMusic by mutableStateOf(store.loadFlag(KEY_BACKGROUND_MUSIC, true))
        private set

    /** The animated wave behind the All Games and Apps pages: 0 off, 1 the original wave (ON), 2 the Minima ribbon (CUSTOM, see [WaveConfig]). */
    var waveStyle by mutableIntStateOf(loadWaveStyle())
        private set

    /** The look of the XMB wave (see [WaveConfig]); saved as its text form, so a randomized look survives restarts and goes into backups. */
    var wave by mutableStateOf(store.loadString(KEY_WAVE)?.let(WaveConfig::parse) ?: WaveConfig.Default)
        private set

    /** Touch input (taps, swipes, drags) works; off ignores the screen altogether, for use with the buttons only. */
    var touchGestures by mutableStateOf(store.loadFlag(KEY_TOUCH_GESTURES, true))
        private set

    /** The display's refresh rate the UI asks for: 60 or 120 Hz. */
    var uiFps by mutableIntStateOf(store.loadString(KEY_UI_FPS)?.toIntOrNull()?.takeIf { it in UiFpsChoices } ?: UiFpsChoices.last())
        private set

    /** Master volume for background music and menu sounds, 0..100. */
    var volume by mutableIntStateOf(store.loadString(KEY_VOLUME)?.toIntOrNull()?.coerceIn(0, 100) ?: 100)
        private set

    /** Persisted URI of the user's chosen background track, or null for none. */
    var customBgmUri by mutableStateOf(store.loadString(KEY_BGM_URI))
        private set

    private val enabledPlatforms = mutableStateListOf<String>()

    /** The order of the platforms in All Games (the user can rearrange it in Edit Mode); starts alphabetical. */
    val platformOrder = mutableStateListOf<String>()

    /** All Games lists a platform's games by most recently played first (otherwise alphabetically). */
    var sortByRecent by mutableStateOf(store.loadFlag(KEY_SORT_RECENT, false))
        private set

    /** Persisted tree URIs of the folders the user added as ROM sources. */
    val romFolders = mutableStateListOf<String>()

    init {
        val known = platforms.map { it.id }.toSet()
        val seeded = store.loadEnabledPlatforms() ?: known
        enabledPlatforms.addAll(seeded.filter { it in known })
        romFolders.addAll(store.loadList(KEY_ROM_FOLDERS))
        val saved = store.loadList(KEY_PLATFORM_ORDER).filter { it in known }
        // Platforms missing from the saved order (a new one) go after it, alphabetically.
        platformOrder.addAll(saved + platforms.sortedBy { it.menuLabel.lowercase() }.map { it.id }.filter { it !in saved })
    }

    /** Puts platform [id] where [targetId] is, shifting the ones in between. */
    fun movePlatform(id: String, targetId: String) {
        val from = platformOrder.indexOf(id)
        val to = platformOrder.indexOf(targetId)
        if (from < 0 || to < 0 || from == to) return
        platformOrder.removeAt(from)
        platformOrder.add(to, id)
        store.saveList(KEY_PLATFORM_ORDER, platformOrder)
    }

    fun toggleSortByRecent() {
        sortByRecent = !sortByRecent
        store.saveFlag(KEY_SORT_RECENT, sortByRecent)
    }

    fun addRomFolder(uri: String) {
        if (uri in romFolders) return
        romFolders.add(uri)
        store.saveList(KEY_ROM_FOLDERS, romFolders)
    }

    fun removeRomFolder(index: Int) {
        if (index !in romFolders.indices) return
        romFolders.removeAt(index)
        store.saveList(KEY_ROM_FOLDERS, romFolders)
    }

    fun isPlatformEnabled(id: String): Boolean = id in enabledPlatforms

    fun setPlatformEnabled(id: String, enabled: Boolean) {
        if (enabled) {
            if (id !in enabledPlatforms) enabledPlatforms.add(id)
        } else {
            enabledPlatforms.remove(id)
        }
        store.saveEnabledPlatforms(enabledPlatforms.toSet())
    }

    fun setCustomBgm(uri: String?) {
        customBgmUri = uri
        store.saveString(KEY_BGM_URI, uri)
    }

    fun changeVolume(delta: Int) {
        volume = (volume + delta).coerceIn(0, 100)
        store.saveString(KEY_VOLUME, volume.toString())
    }

    fun seekVolume(value: Int) {
        volume = value.coerceIn(0, 100)
        store.saveString(KEY_VOLUME, volume.toString())
    }

    fun toggleTouchGestures() {
        touchGestures = !touchGestures
        store.saveFlag(KEY_TOUCH_GESTURES, touchGestures)
    }

    /** 60 -> 120 -> 60. */
    fun cycleUiFps() {
        uiFps = UiFpsChoices[(UiFpsChoices.indexOf(uiFps) + 1) % UiFpsChoices.size]
        store.saveString(KEY_UI_FPS, uiFps.toString())
    }

    private fun loadWaveStyle(): Int =
        store.loadString(KEY_WAVE_STYLE)?.toIntOrNull()?.takeIf { it in 0..2 } ?: if (store.loadFlag(KEY_XMB_WAVE, false)) 1 else 2

    /** OFF -> ON (the original wave) -> CUSTOM (the Minima ribbon) -> OFF. A fresh install starts on CUSTOM. */
    fun cycleWaveStyle() {
        waveStyle = (waveStyle + 1) % 3
        store.saveString(KEY_WAVE_STYLE, waveStyle.toString())
    }

    /** Puts a saved custom wave in place and shows it (the wave switches to CUSTOM). */
    fun applyWave(config: WaveConfig) {
        wave = config
        store.saveString(KEY_WAVE, config.toText())
        if (waveStyle != WaveRibbonStyle) {
            waveStyle = WaveRibbonStyle
            store.saveString(KEY_WAVE_STYLE, waveStyle.toString())
        }
    }

    /** Rolls a new Minima ribbon look (and shows it: the wave switches to the ribbon). */
    fun randomizeWave() = applyWave(wave.randomized())

    fun toggleMenuSounds() {
        menuSounds = !menuSounds
        store.saveFlag(KEY_MENU_SOUNDS, menuSounds)
    }

    fun toggleBackgroundMusic() {
        backgroundMusic = !backgroundMusic
        store.saveFlag(KEY_BACKGROUND_MUSIC, backgroundMusic)
    }

    companion object {
        private const val KEY_XMB_WAVE = "xmbWave"
        private const val KEY_WAVE = "waveConfig"
        private const val WaveRibbonStyle = 2
        private const val KEY_WAVE_STYLE = "waveStyle"
        private const val KEY_TOUCH_GESTURES = "touchGestures"
        private const val KEY_UI_FPS = "uiFps"
        val UiFpsChoices = listOf(60, 120)
        private const val KEY_MENU_SOUNDS = "menuSounds"
        private const val KEY_BACKGROUND_MUSIC = "backgroundMusic"
        private const val KEY_ROM_FOLDERS = "romFolders"
        private const val KEY_BGM_URI = "customBgm"
        private const val KEY_VOLUME = "volume"
        private const val KEY_PLATFORM_ORDER = "platformOrder"
        private const val KEY_SORT_RECENT = "sortByRecent"

        /** Storage-access tree URIs encode "volume:path"; show that, like "1234-ABCD:ROMs/psv". */
        fun romFolderLabel(uri: String): String {
            val documentId = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(uri)) }.getOrNull()
                ?: return uri
            val label = documentId.replaceFirst("primary:", "Internal Storage:")
            return if (label.endsWith(":")) "$label (root)" else label
        }
    }
}
