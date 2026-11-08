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
    /** The analog clock over the dimmed idle screen. */
    var ambientClock by mutableStateOf(store.loadFlag(KEY_AMBIENT_CLOCK, true))
        private set
    var backgroundMusic by mutableStateOf(store.loadFlag(KEY_BACKGROUND_MUSIC, true))
    /** With the music on: false repeats the picked track, true shuffles the files in MinimaUI/Music. */
    var shuffleMusic by mutableStateOf(store.loadFlag(KEY_SHUFFLE_MUSIC, false))
        private set

    /** The animated wave behind the pages: 0 off, 1 the original wave (ORIGINAL), 2 the custom wave (CUSTOM, see [WaveConfig]). */
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

    /** Persisted URI of the user's boot animation (a video played at start instead of the logo), or null for the default one. */
    var bootVideoUri by mutableStateOf(store.loadString(KEY_BOOT_VIDEO))
        private set

    // The user's own say on which platforms show up on All Games: switched on by hand (shown even without games) or off by hand
    // (hidden even with games). A platform with neither shows while it has games.
    private val forcedOn = mutableStateListOf<String>()
    private val forcedOff = mutableStateListOf<String>()

    /** The order of the platforms in All Games (the user can rearrange it in Edit Mode); starts alphabetical. */
    val platformOrder = mutableStateListOf<String>()

    /** All Games lists a platform's games by most recently played first (otherwise alphabetically). */
    var sortByRecent by mutableStateOf(store.loadFlag(KEY_SORT_RECENT, false))
        private set

    /** Persisted tree URIs of the folders the user added as ROM sources. */
    val romFolders = mutableStateListOf<String>()

    init {
        val known = platforms.map { it.id }.toSet()
        forcedOn.addAll(store.loadList(KEY_FORCED_ON).filter { it in known })
        forcedOff.addAll(store.loadList(KEY_FORCED_OFF).filter { it in known })
        // Before 2.4 every platform was either on or off by hand: the ones the user had switched off stay off, the rest follow their games.
        if (!store.loadFlag(KEY_PLATFORM_RULES, false)) {
            store.loadEnabledPlatforms()?.let { oldOn ->
                forcedOff.addAll(OLD_PLATFORMS.filter { it in known && it !in oldOn && it !in forcedOff })
                store.saveList(KEY_FORCED_OFF, forcedOff)
            }
            store.saveFlag(KEY_PLATFORM_RULES, true)
        }
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

    /** The user's own choice for a platform: true = shown even without games, false = hidden, null = shown while it has games. */
    fun platformOverride(id: String): Boolean? = if (id in forcedOn) true else if (id in forcedOff) false else null

    fun setPlatformOverride(id: String, shown: Boolean?) {
        forcedOn.remove(id)
        forcedOff.remove(id)
        when (shown) {
            true -> forcedOn.add(id)
            false -> forcedOff.add(id)
            null -> Unit
        }
        store.saveList(KEY_FORCED_ON, forcedOn)
        store.saveList(KEY_FORCED_OFF, forcedOff)
    }

    fun setCustomBgm(uri: String?) {
        customBgmUri = uri
        store.saveString(KEY_BGM_URI, uri)
    }

    /** Sets the boot animation video; null goes back to the default one and forgets the old video. */
    fun setBootVideo(uri: String?) {
        bootVideoUri = uri
        store.saveString(KEY_BOOT_VIDEO, uri)
    }

    fun changeVolume(delta: Int) {
        volume = (volume + delta).coerceIn(0, 100)
        store.saveString(KEY_VOLUME, volume.toString())
    }

    fun seekVolume(value: Int) {
        volume = value.coerceIn(0, 100)
        store.saveString(KEY_VOLUME, volume.toString())
    }

    fun toggleAmbientClock() {
        ambientClock = !ambientClock
        store.saveFlag(KEY_AMBIENT_CLOCK, ambientClock)
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
        store.loadString(KEY_WAVE_STYLE)?.toIntOrNull()?.takeIf { it in 0 until WaveStyleCount } ?: if (store.loadFlag(KEY_XMB_WAVE, false)) 1 else 2

    /** OFF -> ORIGINAL (the original wave) -> CUSTOM (the custom wave) -> OFF. A fresh install starts on CUSTOM (an old MINIMA reads as CUSTOM). */
    fun cycleWaveStyle() {
        waveStyle = (waveStyle + 1) % WaveStyleCount
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

    /**
     * How blurred and how bright (0..100 % each) the custom wave is behind each page; at 0% brightness it isn't drawn there.
     * Out of the box: as it is on All Games, blurred and dimmed on Apps, none behind Favorites.
     */
    fun wavePageLook(page: WavePage): WavePageLook = pageLooks[page.ordinal]

    fun setWavePageLook(page: WavePage, blur: Int, brightness: Int) {
        val look = WavePageLook(blur.coerceIn(0, 100), brightness.coerceIn(0, 100))
        pageLooks = pageLooks.toMutableList().also { it[page.ordinal] = look }
        store.saveString(KEY_WAVE_LOOK + page.name, "${look.blur},${look.brightness}")
    }

    private var pageLooks by mutableStateOf(WavePage.entries.map { page ->
        store.loadString(KEY_WAVE_LOOK + page.name)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 }
            ?.let { WavePageLook(it[0].coerceIn(0, 100), it[1].coerceIn(0, 100)) }
            ?: defaultLook(page)
    })

    private fun defaultLook(page: WavePage): WavePageLook = when (page) {
        // The author's own looks (2026-11-07): sharp and a little dimmed behind All Games, blurred and half as bright behind
        // Apps, sharp at half brightness behind Favorites (so its dock is frosted).
        WavePage.AllGames -> WavePageLook(0, 80)
        // The Apps look of the build before there were pages (blur and dim), if there is one.
        WavePage.Apps -> store.loadString(KEY_WAVE_APPS)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 }
            ?.let { WavePageLook(it[0].coerceIn(0, 100), (100 - it[1]).coerceIn(0, 100)) }
            ?: WavePageLook(30, 50)
        WavePage.Favorites -> WavePageLook(0, 50)
    }

    /** Rolls a new custom wave look (and shows it: the wave switches to CUSTOM). */
    fun randomizeWave() = applyWave(wave.randomized())

    fun toggleMenuSounds() {
        menuSounds = !menuSounds
        store.saveFlag(KEY_MENU_SOUNDS, menuSounds)
    }

    /** ON (repeat the picked track) -> SHUFFLE (the files in MinimaUI/Music) -> OFF -> ON. */
    fun cycleBackgroundMusic() {
        when {
            backgroundMusic && !shuffleMusic -> shuffleMusic = true
            backgroundMusic -> { backgroundMusic = false; shuffleMusic = false }
            else -> backgroundMusic = true
        }
        store.saveFlag(KEY_BACKGROUND_MUSIC, backgroundMusic)
        store.saveFlag(KEY_SHUFFLE_MUSIC, shuffleMusic)
    }

    companion object {
        private const val KEY_XMB_WAVE = "xmbWave"
        private const val KEY_WAVE = "waveConfig"
        private const val WaveRibbonStyle = 2
        private const val WaveStyleCount = 3
        private const val KEY_WAVE_APPS = "waveApps"
        private const val KEY_WAVE_LOOK = "waveLook."
        private const val KEY_WAVE_STYLE = "waveStyle"
        private const val KEY_TOUCH_GESTURES = "touchGestures"
        private const val KEY_UI_FPS = "uiFps"
        val UiFpsChoices = listOf(60, 120)
        private const val KEY_MENU_SOUNDS = "menuSounds"
        private const val KEY_AMBIENT_CLOCK = "ambientClock"
        private const val KEY_BACKGROUND_MUSIC = "backgroundMusic"
        private const val KEY_SHUFFLE_MUSIC = "shuffleMusic"
        private const val KEY_ROM_FOLDERS = "romFolders"
        private const val KEY_BGM_URI = "customBgm"
        private const val KEY_BOOT_VIDEO = "bootVideo"
        private const val KEY_VOLUME = "volume"
        private const val KEY_PLATFORM_ORDER = "platformOrder"
        private const val KEY_FORCED_ON = "platformsForcedOn"
        private const val KEY_FORCED_OFF = "platformsForcedOff"
        private const val KEY_PLATFORM_RULES = "platformRulesV2"
        /** The platforms that existed before the on-by-games rule. */
        private val OLD_PLATFORMS = listOf("psp", "steam", "switch", "android", "vita", "ps2", "n3ds", "wiiu", "xbox", "windows")
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
