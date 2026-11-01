package com.minimaui.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.os.SystemClock
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.minimaui.app.audio.BgmPlayer
import com.minimaui.app.audio.Sfx
import com.minimaui.app.audio.UiSounds
import com.minimaui.app.data.BackupManager
import com.minimaui.app.data.MinimaFolders
import com.minimaui.app.data.StoredUris
import com.minimaui.app.data.LibraryStore
import com.minimaui.app.data.AndroidGames
import com.minimaui.app.data.GameHubGames
import com.minimaui.app.data.InstalledApps
import com.minimaui.app.data.PinnedShortcuts
import com.minimaui.app.data.PlatformCatalog
import com.minimaui.app.data.RomCache
import com.minimaui.app.data.RomScanner
import com.minimaui.app.model.AppLibrary
import com.minimaui.app.model.Library
import com.minimaui.app.model.GameEntry
import com.minimaui.app.model.Settings
import com.minimaui.app.ui.FixedDesignCanvas
import com.minimaui.app.ui.home.HomeActions
import com.minimaui.app.ui.home.HomeRoot
import com.minimaui.app.ui.theme.MinimaUITheme

class MainActivity : ComponentActivity() {
    private val store by lazy { LibraryStore(applicationContext) }
    private val library by lazy { Library(PlatformCatalog.platforms, store) }
    private val appLibrary by lazy { AppLibrary(store) }
    private val settings by lazy { Settings(PlatformCatalog.platforms, store) }
    private val backups by lazy { BackupManager(applicationContext, store) }
    private val bgm by lazy { BgmPlayer(applicationContext) }
    private val uiSounds by lazy { UiSounds(applicationContext) }
    private val scanner by lazy { RomScanner(contentResolver) }
    private val romCache by lazy { RomCache(applicationContext) }
    // refreshTick rebuilds the library from what is already known (cheap); scanTick also walks the ROM folders again.
    private val refreshTick = mutableIntStateOf(0)
    private val scanTick = mutableIntStateOf(0)
    private val launching = mutableStateOf(false)
    private var pendingCoverGameId: String? = null
    /** The games scanned when a manual rescan started (to tell how many it found), or null. */
    private var rescanBefore: Set<String>? = null
    private var pendingCoverForXmb = false
    private var foldersReady = false
    private var cameBackFromLaunch = false

    private val romFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) addRomFolder(uri)
    }

    private val bgmPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            settings.setCustomBgm(StoredUris.persistent(uri.toString()))
        }
    }

    private val coverPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val gameId = pendingCoverGameId
        val forXmb = pendingCoverForXmb
        pendingCoverGameId = null
        if (uri != null && gameId != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Where the cover was added decides where it shows: the XMB has its own 16:9 covers.
            if (forXmb) library.setXmbCover(gameId, StoredUris.persistent(uri.toString())) else library.setCover(gameId, StoredUris.persistent(uri.toString()))
        }
    }

    private var pendingIconPlatformId: String? = null

    private val platformIconPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val platformId = pendingIconPlatformId
        pendingIconPlatformId = null
        if (uri != null && platformId != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            library.setPlatformIcon(platformId, StoredUris.persistent(uri.toString()))
        }
    }

    private val backupPicker = registerForActivityResult(OpenBackupDocument()) { uri ->
        if (uri != null) restoreBackup(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        preferHighestRefreshRate()
        refreshIcons()
        // If the system closed MinimaUI while a game or app was running, coming back is a return, not a fresh start:
        // no boot logo then.
        cameBackFromLaunch = store.loadString(KEY_LAUNCHED_AT)?.toLongOrNull()
            ?.let { System.currentTimeMillis() - it < 12 * 60 * 60 * 1000L } == true
        store.saveString(KEY_LAUNCHED_AT, null)
        val launcher = GameLauncher(this)
        val actions = HomeActions(
            pickRomFolder = { suppressHomeSound = true; romFolderPicker.launch(null) },
            pickBackgroundMusic = { suppressHomeSound = true; bgmPicker.launch(arrayOf("audio/*")) },
            createBackup = ::createBackup,
            saveWave = ::saveWave,
            restoreBackup = { suppressHomeSound = true; backupPicker.launch(arrayOf("*/*")) },
            launchGame = { game ->
                // An app of the Apps page (favorited from there, or also kept in the Android list) is launched, and left again, with the app sounds; a game with the game ones.
                val isApp = game.favoritesOnly || (game.platformId == "android" && appLibrary.apps.any { it.id == game.shortcutPackage })
                beginLaunch(if (isApp) Sfx.AppLaunch else Sfx.GameLaunch) {
                    launcher.launch(game).also { started ->
                        if (started) {
                            playingId = game.id
                            library.markPlayed(game.id)
                            playingSince = SystemClock.elapsedRealtime()
                        }
                    }
                }
            },
            launchApp = { app -> beginLaunch(Sfx.AppLaunch) { launcher.launchPackage(app.id) } },
            installedApps = { InstalledApps.list(this) },
            addApp = { appLibrary.add(it.packageName, it.label, InstalledApps.saveIcon(this, it.packageName)) },
            addAndroidGame = {
                AndroidGames(store).add(it.packageName, it.label, InstalledApps.saveIcon(this, it.packageName))
                library.restoreGame(AndroidGames.idFor(it.packageName))
                refreshTick.intValue++
            },
            registerAppAsGame = { app ->
                AndroidGames(store).addFavoritesOnly(app.id, app.title, app.coverUri, wasRemoved = library.isRemoved(AndroidGames.idFor(app.id)))
                library.restoreGame(AndroidGames.idFor(app.id))
                refreshTick.intValue++
            },
            addGameHubGame = { title, id ->
                GameHubGames(store).add(id, title)
                library.restoreGame(GameHubGames.idFor(id))
                refreshTick.intValue++
            },
            openSystemSettings = {
                suppressHomeSound = false
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            rescan = {
                rescanBefore = library.scannedGameIds()
                scanTick.intValue++
            },
            pickPlatformIcon = { platformId ->
                pendingIconPlatformId = platformId
                suppressHomeSound = true
                platformIconPicker.launch(arrayOf("image/*"))
            },
            pickCover = { gameId, forXmb ->
                pendingCoverGameId = gameId
                pendingCoverForXmb = forXmb
                suppressHomeSound = true
                coverPicker.launch(arrayOf("image/*"))
            },
        )
        setContent {
            // UI Smoothness: 60 or 120 Hz display mode.
            LaunchedEffect(settings.uiFps) { applyRefreshRate() }
            LaunchedEffect(settings.backgroundMusic, settings.customBgmUri) {
                bgm.update(settings.backgroundMusic, settings.customBgmUri)
            }
            // The ROM folders are walked only when there is no saved scan for them (first launch), when one is added or
            // removed, and on a manual rescan. Everything else (returning to the app, adding an app game) just rebuilds
            // the list from the saved scan.
            LaunchedEffect(Unit) {
                var scannedFolders: List<String>? = null
                var scannedTick = -1
                var roms: List<GameEntry> = emptyList()
                snapshotFlow { Triple(settings.romFolders.toList(), scanTick.intValue, refreshTick.intValue) }
                    .collectLatest { (folders, scan, _) ->
                        library.setGames(
                            withContext(Dispatchers.IO) {
                                val gameHub = GameHubGames(store)
                                gameHub.syncWithFile()
                                if (scannedFolders != folders || scannedTick != scan) {
                                    val manual = scannedFolders != null
                                    roms = (if (manual) null else romCache.load(folders)) ?: scanner.scan(folders).also { romCache.save(folders, it) }
                                    scannedFolders = folders
                                    scannedTick = scan
                                }
                                roms + PinnedShortcuts(store).games() + AndroidGames(store).games() + gameHub.games()
                            }
                        )
                        autoHideEmptyPlatforms()
                        rescanBefore?.let { known ->
                            library.reportRescan((library.scannedGameIds() - known).size)
                            rescanBefore = null
                        }
                    }
            }
            LaunchedEffect(settings.volume) {
                bgm.setVolume(settings.volume / 100f)
            }
            MinimaUITheme {
                FixedDesignCanvas {
                    HomeRoot(
                        library = library,
                        appLibrary = appLibrary,
                        settings = settings,
                        actions = actions,
                        uiSounds = uiSounds,
                        launching = launching.value,
                        playBoot = !bootPlayed && !cameBackFromLaunch,
                        dimmed = dimState.value,
                        idle = idleState.value,
                        onBootPlayed = { bootPlayed = true }
                    )
                }
            }
        }
    }

    /** Re-renders the saved app icons once after the icon style changed (full-bleed instead of padded). */
    private fun refreshIcons() {
        if (store.loadList("iconsVersion") == listOf("2")) return
        (AndroidGames(store).games().mapNotNull { it.shortcutPackage } + appLibrary.apps.map { it.id })
            .distinct().forEach { InstalledApps.saveIcon(this, it) }
        store.saveList("iconsVersion", listOf("2"))
    }

    override fun onStart() {
        super.onStart()
        bgm.setForeground(true)
    }

    override fun onResume() {
        super.onResume()
        com.minimaui.app.overlay.LevelOverlayService.homeActive = true
        // Coming back (screen on, or from another app): ask again for the fast mode, going through "no preference" first so
        // the window manager really applies it after a dimmed, 60 Hz stay.
        applyRefreshRate()
        verifyRefreshRate()
        if (!foldersReady) foldersReady = MinimaFolders.ensure()
        uiSounds.loadCustom()
        // Time away from MinimaUI since a game was launched counts as play time (very short stints are failed launches).
        playingId?.let { id ->
            val elapsed = SystemClock.elapsedRealtime() - playingSince
            if (elapsed >= MIN_PLAY_MILLIS) library.addPlayTime(id, elapsed)
        }
        playingId = null
        // Coming back from another app (a game, recents, Home): the home sound; not after our own pickers.
        // A launch from here always counts; otherwise only a real stay elsewhere does, not a peek at recents or a screen-off.
        val away = SystemClock.elapsedRealtime() - leftAt
        if (leftApp && !suppressHomeSound && settings.menuSounds && (launching.value || away >= HOME_SOUND_MIN_AWAY_MS)) uiSounds.play(if (lastLaunchSound == Sfx.GameLaunch) Sfx.GameExit else Sfx.AppExit)
        leftApp = false
        suppressHomeSound = false
        launching.value = false
        scheduleDim()
        bgm.fadeIn()
        refreshTick.intValue++
    }

    /** Fades the screen out, then runs [start]; if nothing started, the screen fades back in. */
    private fun beginLaunch(sound: Sfx, start: () -> Boolean) {
        lastLaunchSound = sound
        store.saveString(KEY_LAUNCHED_AT, System.currentTimeMillis().toString())
        launching.value = true
        if (settings.menuSounds) uiSounds.play(sound)
        bgm.fadeOut()
        window.decorView.postDelayed({
            if (!start()) {
                store.saveString(KEY_LAUNCHED_AT, null)
                launching.value = false
                bgm.fadeIn()
            }
        }, 200L)
    }

    /**
     * Platforms with no games used to switch themselves off and come back on when games appeared again (Android and Steam
     * stayed, since their games are added by hand). That is off now ([AUTO_HIDE_EMPTY_PLATFORMS]): platforms are only
     * switched on and off by hand, in the Customization menu.
     */
    private fun autoHideEmptyPlatforms() {
        if (!AUTO_HIDE_EMPTY_PLATFORMS) return
        val autoHidden = store.loadList(KEY_AUTO_HIDDEN).toMutableSet()
        for (platform in library.platforms) {
            if (platform.id == "android" || platform.id == "steam") {
                // Never hidden automatically (their games are added by hand), but undo an earlier automatic hide.
                if (autoHidden.remove(platform.id)) settings.setPlatformEnabled(platform.id, true)
                continue
            }
            val hasGames = library.gamesFor(platform).isNotEmpty()
            if (!hasGames && settings.isPlatformEnabled(platform.id)) {
                settings.setPlatformEnabled(platform.id, false)
                autoHidden += platform.id
            } else if (hasGames && autoHidden.remove(platform.id)) {
                settings.setPlatformEnabled(platform.id, true)
            }
        }
        store.saveList(KEY_AUTO_HIDDEN, autoHidden.toList())
    }

    private companion object {
        /** The boot logo plays once per process, not every time the home screen comes back. */
        private var bootPlayed = false
        const val AUTO_HIDE_EMPTY_PLATFORMS = false
        const val KEY_AUTO_HIDDEN = "autoHiddenPlatforms"
        const val KEY_LAUNCHED_AT = "launchedAt"
        const val HOME_SOUND_MIN_AWAY_MS = 4_000L
        const val IDLE_DIM_MS = 30_000L
        const val IDLE_FREEZE_MS = 30_000L
        const val DIM_FRACTION = 0.4f
        const val DIM_FADE_MS = 1000L
        const val REFRESH_CHECKS = 6
        const val REFRESH_CHECK_MS = 700L
        const val MIN_PLAY_MILLIS = 20_000L
    }

    private var playingId: String? = null
    // What was launched last decides which exit sound plays on return (a game's, or an app's for anything else).
    private var lastLaunchSound = Sfx.AppLaunch
    private var playingSince = 0L

    override fun onStop() {
        // Only counts as leaving for another app if the screen was on (not switching the screen off).
        if (getSystemService(android.os.PowerManager::class.java).isInteractive) {
            leftApp = true
            leftAt = SystemClock.elapsedRealtime()
        }
        bgm.setForeground(false)
        super.onStop()
    }

    override fun onPause() {
        com.minimaui.app.overlay.LevelOverlayService.homeActive = false
        dimHandler.removeCallbacks(refreshCheck)
        dimHandler.removeCallbacks(dimRunnable)
        dimHandler.removeCallbacks(idleRunnable)
        dimAnimator?.cancel()
        if (dimmed) {
            dimmed = false
            dimState.value = false
            bgm.setDimmed(false)
        }
        // Always leave with the normal brightness and the fastest display mode (the screen may be switching off, and
        // coming back on must not start in the dimmed 60 Hz mode).
        window.attributes = window.attributes.apply {
            screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            modeFor(Float.MAX_VALUE)?.let { preferredDisplayModeId = it }
        }
        super.onPause()
    }

    // Idle dimming: after IDLE_DIM_MS without input the screen dims; the first input after that only wakes it.
    private val dimHandler = Handler(Looper.getMainLooper())
    private val dimRunnable = Runnable { if (!launching.value) setDimmed(true) }
    private var dimmed = false
    private val dimState = mutableStateOf(false)
    private val swallowedKeys = HashSet<Int>()
    private var swallowTouch = false
    private var leftApp = false
    private var leftAt = 0L
    private var suppressHomeSound = false

    // After IDLE_FREEZE_MS without input the focus glow and the title scroll stop (the UI then has nothing left to redraw).
    private val idleRunnable = Runnable { idleState.value = true }
    private val idleState = mutableStateOf(false)

    private fun scheduleDim() {
        dimHandler.removeCallbacks(dimRunnable)
        dimHandler.postDelayed(dimRunnable, IDLE_DIM_MS)
        idleState.value = false
        dimHandler.removeCallbacks(idleRunnable)
        dimHandler.postDelayed(idleRunnable, IDLE_FREEZE_MS)
    }

    private var dimAnimator: android.animation.ValueAnimator? = null

    private fun setDimmed(on: Boolean) {
        if (dimmed == on) return
        // Never dim (and drop to 60 Hz) for a screen that is already switching off: the mode would be left behind for the wake-up.
        if (on && !getSystemService(android.os.PowerManager::class.java).isInteractive) return
        dimmed = on
        dimState.value = on
        dimAnimator?.cancel()
        val attributes = window.attributes
        val system = android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
        val dimLevel = (system * DIM_FRACTION).coerceAtLeast(0.02f)
        // Dimmed means idle: ask for the display's 60 Hz mode (the app also draws at half of that).
        modeFor(if (on) 60f else Float.MAX_VALUE)?.let { attributes.preferredDisplayModeId = it }
        window.attributes = attributes
        bgm.setDimmed(on)
        // The brightness eases over the same second as the music.
        val from = window.attributes.screenBrightness.takeIf { it >= 0f } ?: system
        val to = if (on) dimLevel else system
        dimAnimator = android.animation.ValueAnimator.ofFloat(from, to).apply {
            duration = DIM_FADE_MS
            addUpdateListener {
                window.attributes = window.attributes.apply { screenBrightness = it.animatedValue as Float }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Back to the system's own brightness once the wake-up fade is done.
                    if (!on && !cancelled) {
                        window.attributes = window.attributes.apply {
                            screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        }
                    }
                }
            })
            start()
        }
    }

    /** True if this input only woke the dimmed screen (and so must not reach the UI). */
    private fun wakeIfDimmed(): Boolean {
        val wasDimmed = dimmed
        if (wasDimmed) setDimmed(false)
        scheduleDim()
        return wasDimmed
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            if (swallowedKeys.remove(event.keyCode)) return true
        } else {
            if (event.keyCode in swallowedKeys) return true
            if (wakeIfDimmed()) {
                swallowedKeys.add(event.keyCode)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // A touch that wakes the dimmed screen is only that; and with Touch Gestures off (Customization menu) the screen is ignored.
            swallowTouch = wakeIfDimmed() || !settings.touchGestures
        }
        if (swallowTouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) swallowTouch = false
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val pushed = event.source and InputDevice.SOURCE_JOYSTICK != 0 && listOf(
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
        ).any { kotlin.math.abs(event.getAxisValue(it)) > 0.5f }
        if (pushed && wakeIfDimmed()) return true
        if (pushed) scheduleDim()
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onDestroy() {
        bgm.release()
        uiSounds.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
        // Waking from the screen timeout can leave the window on the dimmed 60 Hz mode: ask again once it has the focus.
        if (hasFocus && !dimmed) {
            applyRefreshRate()
            verifyRefreshRate()
        }
    }

    private fun addRomFolder(uri: Uri) {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        if (documentId == null || documentId.endsWith(":")) {
            toast(R.string.error_rom_folder)
            return
        }
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        settings.addRomFolder(uri.toString())
    }

    /** True when backups can be written; otherwise asks for "All files access" and says so. */
    private fun hasBackupAccess(): Boolean {
        if (backups.hasStorageAccess()) return true
        toast(R.string.backup_needs_access)
        suppressHomeSound = true
        startActivity(
            Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
        return false
    }

    private fun saveWave() {
        if (!hasBackupAccess()) return
        backups.saveWave(settings.wave)
            .onSuccess { Toast.makeText(this, getString(R.string.wave_saved, it.name), Toast.LENGTH_LONG).show() }
            .onFailure { toast(R.string.backup_failed) }
    }

    private fun createBackup() {
        if (!hasBackupAccess()) return
        backups.createBackup()
            .onSuccess { Toast.makeText(this, getString(R.string.backup_saved, it.name), Toast.LENGTH_LONG).show() }
            .onFailure { toast(R.string.backup_failed) }
    }

    private fun restoreBackup(uri: Uri) {
        backups.restore(uri)
            .onSuccess { wave ->
                if (wave != null) {
                    // A custom wave file: only the wave changes.
                    settings.applyWave(wave)
                    toast(R.string.wave_restored)
                } else {
                    toast(R.string.restore_done)
                    recreate()
                }
            }
            .onFailure { toast(R.string.restore_failed) }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_LONG).show()

    /**
     * Asks for the display's fastest mode at the current resolution (120 Hz on a Retroid Pocket 6), so the system's
     * adaptive refresh doesn't drop MinimaUI to 60 Hz while only a gamepad is in use. Compose animations simply follow
     * whatever rate the display runs at.
     */
    private fun preferHighestRefreshRate() {
        // UI Smoothness: 120 asks for the fastest mode, 60 for the 60 Hz one.
        val hz = if (settings.uiFps >= 120) Float.MAX_VALUE else 60f
        modeFor(hz)?.let { id -> window.attributes = window.attributes.apply { preferredDisplayModeId = id } }
    }

    private var refreshChecks = 0
    private val refreshCheck = Runnable {
        val fast = settings.uiFps >= 120
        val mode = display?.mode
        // The display should be on its fast mode unless the screen is dimmed on purpose; if the system left it on 60 Hz, go again.
        if (fast && !dimmed && mode != null && mode.refreshRate < 100f &&
            getSystemService(android.os.PowerManager::class.java).isInteractive
        ) {
            applyRefreshRate()
        }
        if (++refreshChecks < REFRESH_CHECKS) dimHandler.postDelayed(refreshCheckAgain, REFRESH_CHECK_MS)
    }
    private val refreshCheckAgain: Runnable get() = refreshCheck

    /** After waking or coming back, looks a few times whether the display really is on the fast mode, and repairs it if not. */
    private fun verifyRefreshRate() {
        dimHandler.removeCallbacks(refreshCheck)
        refreshChecks = 0
        dimHandler.postDelayed(refreshCheck, REFRESH_CHECK_MS)
    }

    /** Goes through "no preference" first so the window manager really applies the mode, also after a dimmed 60 Hz stay. */
    private fun applyRefreshRate() {
        window.attributes = window.attributes.apply { preferredDisplayModeId = 0 }
        window.decorView.post { preferHighestRefreshRate() }
    }

    /** The id of the display mode at the current resolution whose refresh rate is closest to [hz] (Float.MAX_VALUE = the fastest). */
    private fun modeFor(hz: Float): Int? {
        val display = display ?: return null
        val current = display.mode
        return display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .minByOrNull { kotlin.math.abs(it.refreshRate - minOf(hz, 1000f)) }?.modeId
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** The system file picker, opened in MinimaUI/Backup so the user can pick a recovery file. */
private class OpenBackupDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: android.content.Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, BackupManager.pickerStartUri())
}
