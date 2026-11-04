package com.minimaui.app.ui.home

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.roundToInt
import com.minimaui.app.data.AndroidGames
import com.minimaui.app.model.AppEntry
import com.minimaui.app.model.AppLibrary
import com.minimaui.app.data.InstalledApp
import com.minimaui.app.ui.components.WaveOriginal
import com.minimaui.app.ui.components.WaveRibbon
import com.minimaui.app.ui.menu.PickerColumns
import com.minimaui.app.model.GameEntry
import com.minimaui.app.model.Library
import com.minimaui.app.model.Platform
import com.minimaui.app.model.Settings
import com.minimaui.app.ui.components.ControlHint
import com.minimaui.app.ui.favorites.BentoLayouts
import com.minimaui.app.ui.favorites.BentoSlot
import com.minimaui.app.ui.favorites.Direction
import com.minimaui.app.ui.favorites.findNeighbor
import com.minimaui.app.ui.menu.MenuCategory
import com.minimaui.app.ui.menu.MenuEntry
import com.minimaui.app.ui.menu.MenuFocus
import com.minimaui.app.ui.menu.MenuLayout
import com.minimaui.app.ui.menu.MoreMenuColumns
import com.minimaui.app.ui.menu.MoreMenuEntries
import com.minimaui.app.ui.menu.KeyAction
import com.minimaui.app.ui.menu.SearchKeyboardLayout

enum class HomeScreen { Favorites, AllGames, Apps }

/** Which page the start menu was opened from; it decides what "Edit Current ..." offers. */
enum class MenuContext { Favorites, AllGames, Apps }

/** Which bar the Select + shoulder combos show. */
enum class LevelKind { Volume, Brightness }

enum class EditItem {
    EditTitle, SelectCover, ToggleFavorite, AddToFolder, CustomEmulator, RemoveFromLibrary, EditMode, EditAppTitle, HideApp,
    PlatformIcon, PlatformTitle, PlatformEmulator,
}

/** What the Favorites bento is showing: the favorites, the recently played games, or one of the user's folders. */
sealed interface FavView {
    data object Favorites : FavView
    data object Recent : FavView
    data class Folder(val id: String) : FavView
}

/** Things the menu asks the host activity to do (pickers, files, ...). */
class HomeActions(
    val pickRomFolder: () -> Unit,
    val pickBackgroundMusic: () -> Unit,
    /** Opens the video picker for the boot animation. */
    val pickBootVideo: () -> Unit,
    val createBackup: () -> Unit,
    val saveWave: () -> Unit,
    val restoreBackup: () -> Unit,
    /** Opens the image picker; [forXmb] when it was asked for on the All Games page (a 16:9 XMB cover). */
    val pickCover: (gameId: String, forXmb: Boolean) -> Unit,
    /** Opens the image picker for a platform's tile on All Games. */
    val pickPlatformIcon: (platformId: String) -> Unit,
    val launchGame: (GameEntry) -> Unit,
    val launchApp: (AppEntry) -> Unit,
    val installedApps: () -> List<InstalledApp>,
    val addApp: (InstalledApp) -> Unit,
    val addAndroidGame: (InstalledApp) -> Unit,
    /** Makes an app of the Apps page a game of the Android platform, so it can be a favorite. */
    val registerAppAsGame: (AppEntry) -> Unit,
    val addGameHubGame: (title: String, id: String) -> Unit,
    val rescan: () -> Unit,
    val openSystemSettings: () -> Unit,
)

/** One search result: a game or an app, shown by its title. */
class SearchHit(val title: String, val game: GameEntry? = null, val app: AppEntry? = null)

/** Where the installed-app picker will put the app that gets chosen. */
enum class PickerTarget { Apps, AndroidGames, CustomEmulator, PlatformEmulator }

private const val VolumeStep = 5

// The wave setting's row on the first page of Customization.
private const val WaveRow = 0

/** The flat Favorites position of the launcher tile (the bottom right small tile of the first page). */
private const val LauncherFlat = 7
private const val RecentLimit = 8

/** Select counts as a tap (and opens the details card) when it is released within this long. */
private const val SelectTapMillis = 450L

/** All navigation state for the home experience, driven entirely by gamepad keys. */
class HomeState(
    val library: Library,
    val appLibrary: AppLibrary,
    val settings: Settings,
    private val actions: HomeActions,
) {
    /** Changes whenever the cursor moves anywhere, so a push against an edge (nothing moved) can stay silent. */
    val cursorSignature: Int
        get() = listOf(
            screen, selectedPlatformIndex, gameIndex, favoritesNav.page, favoritesNav.slot, appsNav.page, appsNav.slot,
            menuOpen, menuFocus, menuCategory, menuPage, menuAction, pickerIndex, searchIndex, searchOnResults, searchKey, held, settings.volume,
            moreOpen, moreIndex, folderPickerOpen, folderIndex,
        ).hashCode()

    /** A weak tick for page and platform changes; set by the UI. */
    var haptic: () -> Unit = {}
    var pageSound: () -> Unit = {}
    /** The menu sound, for opening the launcher menu by touch. */
    var menuSound: () -> Unit = {}
    /** Pressing a key of the search keyboard. */
    var keySound: () -> Unit = {}
    /** A toast appears. */
    var toastSound: () -> Unit = {}
    /** Switching the keyboard between the letters and the number pad. */
    var switchSound: () -> Unit = {}
    /** Edit Mode is entered. */
    var editModeSound: () -> Unit = {}
    /** The search is opened. */
    var searchSound: () -> Unit = {}
    /** The cancel / confirm sound, for choosing an item of a menu by touch (the key handler plays it for the buttons). */
    var confirmSound: () -> Unit = {}

    // Volume / brightness bars (shown when the system changes a level) and the short message pill.
    var levelKind by mutableStateOf(LevelKind.Volume)
        private set
    var levelVisible by mutableStateOf(false)
        private set
    /** Bumped on every level combo, so the hide timer restarts. */
    var levelStamp by mutableIntStateOf(0)
        private set
    var selectHeld by mutableStateOf(false)
        private set
    private var selectCombo = false
    private var selectIgnoreRelease = false
    private var selectDownAt = 0L

    var toastText by mutableStateOf("")
        private set
    var toastStamp by mutableIntStateOf(0)
        private set

    private fun showToast(text: String) {
        toastText = text
        toastStamp++
        toastSound()
    }

    /** The manual rescan has finished; [added] is how many more games the library shows than before it. */
    fun onRescanResult(added: Int) {
        showToast(
            when {
                added <= 0 -> "No new games added"
                added == 1 -> "1 new game added"
                else -> "$added new games added"
            }
        )
    }

    var screen by mutableStateOf(HomeScreen.Favorites)
        private set
    private var lastGamesScreen = HomeScreen.Favorites

    /** Edit Mode: tiles can be picked up (A) and moved with the D-pad. [held] is true while one is picked up. */
    var editMode by mutableStateOf(false)
        private set
    var held by mutableStateOf(false)
        private set

    var platformIndex by mutableIntStateOf(0)
        private set
    private val gameIndexes = mutableStateMapOf<String, Int>()

    var menuOpen by mutableStateOf(false)
        private set
    var menuCategory by mutableStateOf(MenuCategory.EditGame)
        private set
    var menuFocus by mutableStateOf(MenuFocus.Categories)
        private set
    var menuAction by mutableIntStateOf(0)
        private set
    /** The page of the open menu category (Customization has three). */
    var menuPage by mutableIntStateOf(0)
        private set
    private var menuContext by mutableStateOf(MenuContext.Favorites)
    private var menuGame by mutableStateOf<GameEntry?>(null)
    private var menuApp by mutableStateOf<AppEntry?>(null)

    private var titleEditGameId by mutableStateOf<String?>(null)
    private var titleEditAppId by mutableStateOf<String?>(null)
    private var titleEditPlatformId by mutableStateOf<String?>(null)
    var titleEditValue by mutableStateOf(TextFieldValue())
        private set
    // Titles and folder names are typed with MinimaUI's own keyboard; only the GameHub entries (title and id) use the system
    // keyboard, since pasting from the clipboard is what they are for.
    var entryText by mutableStateOf("")
        private set
    /** The text is selected: the first letter typed replaces it, and Delete clears it. */
    var entrySelected by mutableStateOf(false)
        private set
    var entryKey by mutableIntStateOf(0)
        private set
    var entryCaps by mutableStateOf(false)
        private set
    var entryDigits by mutableStateOf(false)
        private set
    val ownKeyboardEntry: Boolean
        get() = titleEditGameId != null || titleEditAppId != null || titleEditPlatformId != null || newFolderEntry

    private fun startEntry(initial: String) {
        entryText = initial
        entrySelected = initial.isNotEmpty()
        entryKey = 0
        entryCaps = false
        entryDigits = false
    }

    /** Adding a GameHub game takes two text entries: 1 = its title, 2 = its id. */
    private var gameHubStep by mutableIntStateOf(0)
    private var gameHubTitle = ""
    val isEditingTitle: Boolean get() = titleEditGameId != null || titleEditAppId != null || titleEditPlatformId != null || gameHubStep != 0 || newFolderEntry
    /** True while a GameHub game is being added (two entries: title, then id). */
    val addingGameHubGame: Boolean get() = gameHubStep != 0 || newFolderEntry
    val titleEditHeading: String
        get() = when {
            newFolderEntry -> "New Folder"
            gameHubStep == 1 -> "Steam Game Title"
            gameHubStep == 2 -> "Steam Game ID"
            titleEditAppId != null -> "Edit App Title"
            titleEditPlatformId != null -> "Edit Platform Title"
            else -> "Edit Game Title"
        }

    /** Favorites of platforms that are switched on (the others stay remembered, just not shown). */
    val favorites: List<GameEntry> by derivedStateOf {
        library.favorites.filter { settings.isPlatformEnabled(it.platformId) }
    }
    val apps: List<AppEntry> by derivedStateOf { appLibrary.apps }

    // ---- The Favorites page can show more than the favorites: the launcher tile opens a small menu (Folder, Recently Played).
    var favView by mutableStateOf<FavView>(FavView.Favorites)
        private set
    val hasLauncher: Boolean get() = favView == FavView.Favorites
    /** True on the Favorites page while it shows Recently Played or a folder (then Apps and All Games are off limits). */
    private val inFavView: Boolean get() = screen == HomeScreen.Favorites && favView != FavView.Favorites
    var moreOpen by mutableStateOf(false)
        private set
    var moreIndex by mutableIntStateOf(0)
        private set
    var folderPickerOpen by mutableStateOf(false)
        private set
    var folderIndex by mutableIntStateOf(0)
        private set
    /** When picking a folder for a game: that game's id (null = just opening a folder). */
    private var folderPickerGameId by mutableStateOf<String?>(null)
    var newFolderEntry by mutableStateOf(false)
        private set

    private val enabledPlatformIds: Set<String> get() = visiblePlatforms.map { it.id }.toSet()

    /** The games the Favorites bento currently lists. */
    val viewGames: List<GameEntry> get() = gamesIn(favView)

    /** The games of [view]; the page draws the view it is told to, so one fading out during a switch keeps its own games. */
    fun gamesIn(view: FavView): List<GameEntry> = when (view) {
        FavView.Favorites -> favorites
        FavView.Recent -> library.recentGames(enabledPlatformIds, RecentLimit)
        is FavView.Folder -> library.folderGames(view.id, enabledPlatformIds)
    }

    /** One page of [view]: eight slots, empty ones null (the launcher's place on the favorites' first page too). */
    fun pageOf(view: FavView, page: Int): List<GameEntry?> {
        val games = gamesIn(view)
        return List(FavoritesPageSize) { slot ->
            val flat = page * FavoritesPageSize + slot
            if (view == FavView.Favorites) {
                if (flat == LauncherFlat) null else games.getOrNull(if (flat < LauncherFlat) flat else flat - 1)
            } else {
                games.getOrNull(flat)
            }
        }
    }

    fun slotsIn(view: FavView, page: Int): List<BentoSlot> =
        if (view == FavView.Favorites && page == 0) BentoLayouts.Default else BentoLayouts.Grid

    fun pageCountIn(view: FavView): Int {
        val items = if (view == FavView.Favorites) maxOf(gamesIn(view).size + 1, FavoritesPageSize) else gamesIn(view).size
        return maxOf(1, (items + FavoritesPageSize - 1) / FavoritesPageSize)
    }

    fun labelIn(view: FavView): String? = when (view) {
        FavView.Favorites -> null
        FavView.Recent -> "Recently Played"
        is FavView.Folder -> library.folders.firstOrNull { it.id == view.id }?.name
    }

    /** The game at flat position [flat] of the bento (the launcher tile holds a place of its own on the first page). */
    fun favoriteGameAt(flat: Int): GameEntry? =
        if (hasLauncher) {
            if (flat == LauncherFlat) null else viewGames.getOrNull(if (flat < LauncherFlat) flat else flat - 1)
        } else {
            viewGames.getOrNull(flat)
        }

    private fun flatOfGameIndex(i: Int) = if (hasLauncher && i >= LauncherFlat) i + 1 else i

    val viewLabel: String?
        get() = when (val v = favView) {
            FavView.Favorites -> null
            FavView.Recent -> "Recently Played"
            is FavView.Folder -> library.folders.firstOrNull { it.id == v.id }?.name
        }

    val folderNames: List<String> get() = library.folders.map { it.name }
    val folderPickerTitle: String get() = if (folderPickerGameId != null) "Add To Folder" else "Folders"
    val moreHints = listOf(ControlHint("A", "Enter"), ControlHint("B", "Back"))
    val folderHints: List<ControlHint>
        get() = when {
            folderPickerGameId != null -> listOf(ControlHint("A", "Add / Remove"), ControlHint("B", "Back"))
            folderIndex in library.folders.indices -> listOf(ControlHint("X", "Delete"), ControlHint("A", "Open"), ControlHint("B", "Back"))
            else -> listOf(ControlHint("A", "New Folder"), ControlHint("B", "Back"))
        }

    fun openMore() {
        if (!hasLauncher || editMode || menuOpen || pickerOpen || searchOpen || detailsGame != null || isEditingTitle) return
        moreIndex = 0
        moreOpen = true
    }

    private fun onMoreKey(key: Key, isRepeat: Boolean): Boolean {
        when (key) {
            Key.DirectionLeft -> if (moreIndex % MoreMenuColumns > 0) moreIndex--
            Key.DirectionRight -> if (moreIndex % MoreMenuColumns < MoreMenuColumns - 1 && moreIndex < MoreMenuEntries - 1) moreIndex++
            Key.DirectionUp -> if (moreIndex >= MoreMenuColumns) moreIndex -= MoreMenuColumns
            Key.DirectionDown -> if (moreIndex + MoreMenuColumns < MoreMenuEntries) moreIndex += MoreMenuColumns
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> if (!isRepeat) chooseMore(moreIndex)
            Key.ButtonB, Key.Escape -> if (!isRepeat) moreOpen = false
            else -> return false
        }
        return true
    }

    /** A tap on a launcher menu entry (the random game has its own launch sound). */
    fun tapMore(index: Int) {
        if (index != 2) confirmSound()
        chooseMore(index)
    }

    fun tapFolder(index: Int) {
        confirmSound()
        chooseFolder(index)
    }

    fun chooseMore(index: Int) {
        moreIndex = index
        when (index) {
            0 -> {
                moreOpen = false
                openFolderPicker(null)
            }
            1 -> {
                moreOpen = false
                showView(FavView.Recent)
            }
            2 -> {
                // Straight into a random game from All Games, without saying which.
                val all = visiblePlatforms.flatMap(library::gamesFor)
                moreOpen = false
                all.randomOrNull()?.let(actions.launchGame)
            }
            3 -> {
                moreOpen = false
                toggleMenu()
            }
            4 -> {
                moreOpen = false
                switchTo(HomeScreen.Apps)
            }
            5 -> {
                moreOpen = false
                openSearch()
            }
        }
    }

    private fun showView(view: FavView) {
        favView = view
        favoritesNav.reset()
        held = false
    }

    private fun leaveView() {
        favView = FavView.Favorites
        changeEditMode(false)
        favoritesNav.focusAt(LauncherFlat)
    }

    private fun openFolderPicker(gameId: String?) {
        folderPickerGameId = gameId
        folderIndex = 0
        folderPickerOpen = true
    }

    private fun onFolderKey(key: Key, isRepeat: Boolean): Boolean {
        // The folders, then a "+" row for a new one.
        val count = library.folders.size + 1
        when (key) {
            Key.DirectionUp -> folderIndex = (folderIndex - 1).coerceAtLeast(0)
            Key.DirectionDown -> folderIndex = (folderIndex + 1).coerceAtMost(count - 1)
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> if (!isRepeat) chooseFolder(folderIndex)
            Key.ButtonX -> if (!isRepeat && folderPickerGameId == null && folderIndex in library.folders.indices) {
                val folder = library.folders[folderIndex]
                library.deleteFolder(folder.id)
                folderIndex = folderIndex.coerceAtMost(library.folders.size)
                showToast("Deleted \"${folder.name}\"")
            }
            Key.ButtonB, Key.Escape -> if (!isRepeat) folderPickerOpen = false
            else -> return false
        }
        return true
    }

    fun chooseFolder(index: Int) {
        folderIndex = index
        val folder = library.folders.getOrNull(index)
        if (folder == null) {
            newFolderEntry = true
            startEntry("")
            return
        }
        val gameId = folderPickerGameId
        if (gameId == null) {
            folderPickerOpen = false
            showView(FavView.Folder(folder.id))
        } else {
            val added = library.toggleInFolder(folder.id, gameId)
            folderPickerOpen = false
            showToast(if (added) "Added to \"${folder.name}\"" else "Removed from \"${folder.name}\"")
        }
    }

    var pickerTarget by mutableStateOf<PickerTarget?>(null)
        private set
    var pickerItems by mutableStateOf<List<InstalledApp>>(emptyList())
        private set
    var pickerIndex by mutableIntStateOf(0)
        private set
    val pickerOpen: Boolean get() = pickerTarget != null
    val pickerTitle: String
        get() = when (pickerTarget) {
            PickerTarget.AndroidGames -> "Add Android Games"
            PickerTarget.CustomEmulator -> "Choose Custom Emulator"
            PickerTarget.PlatformEmulator -> "Choose Default Emulator"
            else -> "Add Android Apps"
        }
    val pickerHints: List<ControlHint>
        get() {
            val choosing = pickerTarget == PickerTarget.CustomEmulator || pickerTarget == PickerTarget.PlatformEmulator
            val hints = listOf(ControlHint("A", if (choosing) "Select" else "Add"), ControlHint("B", "Back"))
            return if (pickerCanRemove) listOf(ControlHint("X", "Remove")) + hints else hints
        }

    /** True in an emulator picker when the game or platform already has an emulator set (X takes it away again). */
    private val pickerCanRemove: Boolean
        get() = when (pickerTarget) {
            PickerTarget.CustomEmulator -> emulatorTargetId?.let { library.customEmulatorOf(it) } != null
            PickerTarget.PlatformEmulator ->
                library.platforms.firstOrNull { it.id == emulatorTargetId }?.let { library.platformEmulator(it) } != null
            else -> false
        }

    /** The game (or platform) the emulator picker is choosing for. */
    private var emulatorTargetId: String? = null

    private fun openPicker(target: PickerTarget) {
        pickerTarget = target
        pickerItems = actions.installedApps().filterNot(::isPicked)
        pickerIndex = 0
    }

    private fun isPicked(app: InstalledApp): Boolean = when (pickerTarget) {
        PickerTarget.Apps -> appLibrary.contains(app.packageName)
        PickerTarget.AndroidGames -> androidPlatform?.let { library.gamesFor(it) }.orEmpty().any { it.shortcutPackage == app.packageName }
        PickerTarget.CustomEmulator, PickerTarget.PlatformEmulator, null -> false
    }

    private val androidPlatform: Platform? get() = library.platforms.firstOrNull { it.id == "android" }

    private fun pickerConfirm() {
        pickerItems.getOrNull(pickerIndex)?.let {
            if (pickerTarget == PickerTarget.CustomEmulator || pickerTarget == PickerTarget.PlatformEmulator) {
                emulatorTargetId?.let { id ->
                    if (pickerTarget == PickerTarget.CustomEmulator) library.setCustomEmulator(id, it.packageName)
                    else library.setPlatformEmulator(id, it.packageName)
                }
                pickerTarget = null
                showToast("Emulator: ${it.label}")
                return
            }
            if (pickerTarget == PickerTarget.Apps) actions.addApp(it) else actions.addAndroidGame(it)
            // Added apps leave the list.
            pickerItems = pickerItems - it
            pickerIndex = pickerIndex.coerceAtMost(maxOf(pickerItems.lastIndex, 0))
        }
    }

    private var lastPickerStep = 0L

    private fun onPickerKey(key: Key, isRepeat: Boolean): Boolean {
        // Same hold-scroll speed cap as the platform row in All Games.
        if (isRepeat) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastPickerStep < XmbRepeatMillis) return key in XmbKeys
            lastPickerStep = now
        }
        when (key) {
            Key.DirectionLeft -> if (pickerIndex % PickerColumns > 0) pickerIndex--
            Key.DirectionRight ->
                if (pickerIndex % PickerColumns < PickerColumns - 1 && pickerIndex < pickerItems.lastIndex) pickerIndex++
            Key.DirectionUp -> if (pickerIndex >= PickerColumns) pickerIndex -= PickerColumns
            Key.DirectionDown -> pickerIndex = (pickerIndex + PickerColumns).coerceAtMost(maxOf(pickerItems.lastIndex, 0))
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> if (!isRepeat) pickerConfirm()
            Key.ButtonX -> if (!isRepeat && pickerCanRemove) {
                // Back to the original emulator.
                val id = emulatorTargetId
                if (id != null) {
                    if (pickerTarget == PickerTarget.CustomEmulator) library.setCustomEmulator(id, null) else library.setPlatformEmulator(id, null)
                }
                pickerTarget = null
                showToast("Original emulator")
            }
            Key.ButtonB, Key.Escape -> if (!isRepeat) pickerTarget = null
            else -> return false
        }
        return true
    }

    val favoritesNav = FavoritesNav()
    val appsNav = AppsNav()

    /** Bumped whenever A confirms a tile, so the focused one can play its pressed feel. */
    var pressTick by mutableIntStateOf(0)
        private set

    /** Global search (L2 + R2): the query, its live results, and whether the cursor is on the results or the field. */
    var searchOpen by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var searchOnResults by mutableStateOf(false)
        private set
    var searchIndex by mutableIntStateOf(0)
        private set

    /** The key the cursor is on in the search's on-screen keyboard (an index into [SearchKeyboardLayout.keys]). */
    var searchKey by mutableIntStateOf(0)
        private set

    /** Capitals on (L or the Shift key): the search keyboard types capital letters. */
    var searchCaps by mutableStateOf(false)
        private set

    /** The search keyboard's top row types digits (R1). */
    var searchDigits by mutableStateOf(false)
        private set

    /** Every launchable app on the device, read when the search opens: the search finds apps that were never added to the Apps page too. */
    private var searchInstalled by mutableStateOf<List<InstalledApp>>(emptyList())

    val searchHits: List<SearchHit> by derivedStateOf {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) return@derivedStateOf emptyList()
        val terms = query.split(' ').filter { it.isNotEmpty() }
        val games = visiblePlatforms.flatMap { library.gamesFor(it) }
        // An added app (maybe renamed) and an Android game's own app are already results; the rest of the installed apps follow.
        val known = apps.map { it.id }.toSet() + games.mapNotNull { it.shortcutPackage }
        (games.map { SearchHit(it.title, game = it) } +
            apps.map { SearchHit(it.title, app = it) } +
            searchInstalled.filter { it.packageName !in known }.map { SearchHit(it.label, app = AppEntry(it.packageName, it.label)) })
            .filter { hit -> hit.title.lowercase().let { title -> terms.all { it in title } } }
            .sortedWith(compareBy({ !it.title.lowercase().startsWith(query) }, { it.title.lowercase() }))
            .take(SearchMaxHits)
    }

    /** True while the menu, picker, search, details or a text field is on top of the home screens. */
    val overlayOpen: Boolean get() = menuOpen || pickerOpen || searchOpen || detailsGame != null || isEditingTitle || moreOpen || folderPickerOpen

    val searchHints: List<ControlHint>
        get() = if (searchOnResults) listOf(ControlHint("A", "Enter"), ControlHint("B", "Back")) else listOf(ControlHint("B", "Back"))

    /** True while the cursor of an open on-screen keyboard (search or title) is on its Shift key. */
    val keyboardOnShift: Boolean
        get() = when {
            ownKeyboardEntry -> SearchKeyboardLayout.keysFor(entryDigits).getOrNull(entryKey)?.action == KeyAction.Shift
            searchOpen && !searchOnResults -> SearchKeyboardLayout.keysFor(searchDigits).getOrNull(searchKey)?.action == KeyAction.Shift
            else -> false
        }

    /** True while a text field has the system keyboard (the title editor; the search has its own on-screen keyboard). */
    val textInputActive: Boolean get() = isEditingTitle && !ownKeyboardEntry

    fun openSearch() {
        if (searchOpen || menuOpen || pickerOpen || detailsGame != null || isEditingTitle || editMode || moreOpen || folderPickerOpen) return
        searchQuery = ""
        searchOnResults = false
        searchIndex = 0
        searchKey = 0
        searchDigits = false
        searchCaps = false
        searchInstalled = actions.installedApps()
        searchOpen = true
        searchSound()
    }

    fun updateSearch(text: String) {
        searchQuery = text
        searchIndex = 0
    }

    /** Moves the cursor from the keyboard to the results, if the query has any. */
    private fun showSearchResults(): Boolean {
        if (searchHits.isEmpty()) return false
        searchOnResults = true
        searchIndex = 0
        return true
    }

    private fun pressSearchKey(index: Int) {
        val key = SearchKeyboardLayout.keysFor(searchDigits).getOrNull(index) ?: return
        when (key.action) {
            KeyAction.Letter ->
                if (searchQuery.length < SearchMaxQuery) updateSearch(searchQuery + (SearchKeyboardLayout.charFor(index, searchDigits) ?: return).let { if (searchCaps) it.uppercaseChar() else it.lowercaseChar() })
            KeyAction.Space -> if (searchQuery.isNotEmpty() && !searchQuery.endsWith(' ') && searchQuery.length < SearchMaxQuery) updateSearch("$searchQuery ")
            KeyAction.Delete -> if (searchQuery.isNotEmpty()) updateSearch(searchQuery.dropLast(1))
            KeyAction.Enter -> showSearchResults()
            KeyAction.Shift -> {
                keySound()
                searchCaps = !searchCaps
            }
        }
    }

    /** A on the field moves to the results; A on a result launches it. */
    fun submitSearch() {
        if (!searchOnResults) {
            if (searchHits.isNotEmpty()) {
                searchOnResults = true
                searchIndex = 0
            }
            return
        }
        val hit = searchHits.getOrNull(searchIndex) ?: return
        searchOpen = false
        hit.game?.let(actions.launchGame)
        hit.app?.let(actions.launchApp)
    }

    private fun onSearchKey(key: Key, isRepeat: Boolean): Boolean {
        if (!searchOnResults) return onSearchKeyboardKey(key, isRepeat)
        when (key) {
            Key.ButtonB, Key.Escape -> if (!isRepeat) searchOnResults = false
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> if (!isRepeat) submitSearch()
            Key.DirectionDown -> searchIndex = (searchIndex + 1).coerceAtMost(maxOf(searchHits.lastIndex, 0))
            Key.DirectionUp -> if (searchIndex == 0) searchOnResults = false else searchIndex--
            else -> Unit
        }
        return true
    }

    /** The on-screen keyboard: the D-pad moves between keys, A presses the focused one, X deletes, Y types a space, Start shows the results. */
    private fun onSearchKeyboardKey(key: Key, isRepeat: Boolean): Boolean {
        when (key) {
            Key.ButtonB, Key.Escape -> if (!isRepeat) searchOpen = false
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> pressSearchKey(searchKey)
            Key.ButtonX -> pressSearchKey(SearchKeyboardLayout.keysFor(searchDigits).indexOfFirst { it.action == KeyAction.Delete })
            Key.ButtonY -> pressSearchKey(SearchKeyboardLayout.keysFor(searchDigits).indexOfFirst { it.action == KeyAction.Space })
            Key.ButtonL1 -> if (!isRepeat && !searchDigits) {
                keySound()
                searchCaps = !searchCaps
            }
            Key.ButtonR1 -> if (!isRepeat) {
                switchSound()
                searchDigits = !searchDigits
                searchKey = 0
            }
            Key.DirectionLeft -> searchKey = SearchKeyboardLayout.move(searchKey, -1, 0, searchDigits)
            Key.DirectionRight -> searchKey = SearchKeyboardLayout.move(searchKey, 1, 0, searchDigits)
            Key.DirectionUp -> searchKey = SearchKeyboardLayout.move(searchKey, 0, -1, searchDigits)
            Key.DirectionDown -> searchKey = SearchKeyboardLayout.move(searchKey, 0, 1, searchDigits)
            else -> return false
        }
        return true
    }

    /** Start held down (a tap opens/closes the menu on release; a 0.5 s hold opens Android's settings instead). */
    var startHeld by mutableStateOf(false)
        private set
    private var startFired = false

    /** Start pressed or released; returns true if this event toggled the menu. */
    fun onStart(down: Boolean, isRepeat: Boolean): Boolean {
        if (down) {
            if (!isRepeat && !startHeld) {
                startHeld = true
                startFired = false
            }
            return false
        }
        if (!startHeld) return false
        startHeld = false
        if (startFired) return false
        if (ownKeyboardEntry) {
            saveTitleEdit()
            return true
        }
        if (searchOpen) return !searchOnResults && showSearchResults()
        if (pickerOpen || detailsGame != null || isEditingTitle || moreOpen || folderPickerOpen) return false
        toggleMenu()
        return true
    }

    /** Start has been held for 0.5 s: open the Android settings (the menu is not toggled on release). */
    fun fireStartHold() {
        if (!startHeld || startFired) return
        startFired = true
        if (!searchOpen && !pickerOpen && detailsGame == null && !isEditingTitle && !moreOpen && !folderPickerOpen) actions.openSystemSettings()
    }

    fun releaseStart() {
        startHeld = false
    }

    /** The SELECT card. */
    var detailsGame by mutableStateOf<GameEntry?>(null)
        private set

    fun playTimeText(game: GameEntry): String {
        val minutes = library.playTimeMillis(game.id) / 60_000L
        return when {
            minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
            else -> "${minutes}m"
        }
    }

    fun platformName(game: GameEntry): String =
        library.platforms.firstOrNull { it.id == game.platformId }?.menuLabel ?: game.platformId

    private val activeNav: GridNav?
        get() = when (screen) {
            HomeScreen.Favorites -> favoritesNav
            HomeScreen.Apps -> appsNav
            HomeScreen.AllGames -> null
        }

    /** Platforms the user has switched on, in the order they arranged them (alphabetical until they do). */
    val visiblePlatforms: List<Platform> by derivedStateOf {
        library.platforms
            .filter { settings.isPlatformEnabled(it.id) }
            .sortedBy { platform -> settings.platformOrder.indexOf(platform.id).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    /** [platformIndex] kept in range, since platforms can be switched off while one is selected. */
    val selectedPlatformIndex: Int
        get() = platformIndex.coerceIn(0, maxOf(visiblePlatforms.lastIndex, 0))

    private val selectedPlatform: Platform? get() = visiblePlatforms.getOrNull(selectedPlatformIndex)

    val gamesOnPlatform: List<GameEntry> by derivedStateOf {
        selectedPlatform?.let(::gamesFor).orEmpty()
    }

    /** A platform's games: alphabetical, or the most recently played first (never-played ones follow, alphabetically). */
    fun gamesFor(platform: Platform): List<GameEntry> {
        val games = library.gamesFor(platform)
        return if (settings.sortByRecent) {
            games.sortedWith(compareByDescending<GameEntry> { library.lastPlayedMillis(it.id) }.thenBy { it.title.lowercase() })
        } else {
            games.sortedBy { it.title.lowercase() }
        }
    }

    /** Rows in a platform's list: its games, plus a "+" row at the end of Android's and Steam's while Edit Mode is on. */
    private fun rowCount(platform: Platform) = library.gamesFor(platform).size + if (platform.id in PlusPlatforms && editMode) 1 else 0

    fun gameIndexFor(platform: Platform): Int =
        (gameIndexes[platform.id] ?: 0).coerceIn(0, maxOf(rowCount(platform) - 1, 0))

    val gameIndex: Int
        get() = (selectedPlatform?.let { gameIndexes[it.id] } ?: 0).coerceIn(0, maxOf((selectedPlatform?.let(::rowCount) ?: 0) - 1, 0))

    /** The platforms of the Customization menu's last page, in the order the designs list them. */
    private val menuPlatforms: List<Platform> = library.platforms.sortedBy { platform ->
        PlatformMenuOrder.indexOf(platform.id).let { if (it < 0) Int.MAX_VALUE else it }
    }

    val menuEditTitle: String
        get() = if (platformEditing) "Edit Current Platform" else if (menuContext == MenuContext.Apps) "Edit Current App" else "Edit Current Game"

    /** The folder being viewed in Favorites, while the menu was opened there (null on every other screen and view). */
    private val openFolder: FavView.Folder?
        get() = if (menuContext == MenuContext.Favorites) favView as? FavView.Folder else null

    // Edit Mode itself lives in the Customization menu now.
    private val editItems: List<EditItem>
        get() = if (platformEditing) listOf(EditItem.PlatformIcon, EditItem.PlatformTitle, EditItem.PlatformEmulator) else when (menuContext) {
            MenuContext.Apps -> if (menuApp != null) listOf(EditItem.EditAppTitle, EditItem.ToggleFavorite, EditItem.HideApp) else emptyList()
            MenuContext.Favorites, MenuContext.AllGames ->
                if (menuGame != null) {
                    // The custom emulator is for ROM files; shortcuts, GameHub and Android games start through their own app.
                    listOfNotNull(
                        EditItem.EditTitle, EditItem.SelectCover, EditItem.ToggleFavorite, EditItem.AddToFolder,
                        EditItem.CustomEmulator.takeIf { menuGame?.fileUri != null }, EditItem.RemoveFromLibrary
                    )
                } else {
                    emptyList()
                }
        }

    val menuEntries: List<MenuEntry>
        get() = menuEntriesFor(menuCategory, menuPage)

    /** The games taken out of the library, for the second page of Edit Current Game. */
    private val hiddenGames: List<GameEntry>
        get() = library.removedGames()

    /** How many pages the open menu category has: Edit Current Platform has a second one (the hidden games), only in Edit Mode on All Games. */
    val menuPageCount: Int
        get() = when (menuCategory) {
            MenuCategory.EditGame -> if (platformEditing) 2 else 1
            else -> menuCategory.pages
        }

    /** The entries of page [page] of [category]; the menu draws neighbouring pages too while they slide. */
    fun menuEntriesFor(category: MenuCategory, page: Int): List<MenuEntry> = when (category) {
        MenuCategory.EditGame ->
            if (page == 1) {
                hiddenGames.map { MenuEntry(it.title) }.ifEmpty { listOf(MenuEntry("No hidden games")) }
            } else {
                editItems.map { MenuEntry(editItemLabel(it), danger = it == EditItem.RemoveFromLibrary) }
            }
        MenuCategory.Customize -> when (page) {
            0 -> listOf(
                MenuEntry("XMB Wave [${waveLabel(settings.waveStyle)}]"),
                MenuEntry("Touch Gestures [${onOff(settings.touchGestures)}]"),
                MenuEntry(editItemLabel(EditItem.EditMode)),
                MenuEntry("UI Smoothness [${settings.uiFps} fps]"),
                MenuEntry("Boot Animation [${if (settings.bootVideoUri != null) "CUSTOM" else "DEFAULT"}]"),
                MenuEntry("Ambient Clock [${onOff(settings.ambientClock)}]"),
            )
            1 -> listOf(
                MenuEntry("Menu Sounds [${onOff(settings.menuSounds)}]"),
                MenuEntry("Background Music [${onOff(settings.backgroundMusic)}]"),
                MenuEntry("Pick Menu BGM…"),
                MenuEntry("Volume", slider = settings.volume),
            )
            else -> menuPlatforms.map { MenuEntry(library.platformTitle(it) ?: it.menuLabel, dot = settings.isPlatformEnabled(it.id)) }
        }
        MenuCategory.RomFolders -> settings.romFolders.map { MenuEntry(Settings.romFolderLabel(it)) } + MenuEntry("+", plus = true)
        MenuCategory.Backup -> listOf(MenuEntry("Backup Current Settings"), MenuEntry("Restore Settings"))
    }

    val menuHints: List<ControlHint>
        get() {
            val base = listOf(ControlHint("A", "Enter"), ControlHint("B", "Back"))
            if (menuFocus != MenuFocus.Actions) return base
            if (menuCategory == MenuCategory.EditGame && menuPage == 1) {
                return if (hiddenGames.isEmpty()) listOf(ControlHint("B", "Back")) else listOf(ControlHint("A", "Unhide"), ControlHint("B", "Back"))
            }
            return when (menuCategory) {
                MenuCategory.EditGame ->
                    if (canRemoveEditItem(editItems.getOrNull(menuAction))) listOf(ControlHint("X", "Remove")) + base else base
                MenuCategory.RomFolders ->
                    // The + at the end of the list has no folder to remove.
                    (if (menuAction < settings.romFolders.size) listOf(ControlHint("X", "Remove Selected Folder")) else emptyList()) +
                        ControlHint("Y", "Rescan") + base
                MenuCategory.Customize ->
                    if (menuPage == 0 && menuAction == WaveRow) listOf(ControlHint("X", "Randomize"), ControlHint("Y", "Save Current")) + base
                    else base
                else -> base
            }
        }

    /** Edit Mode on the platform row of All Games. */
    private val platformEditing: Boolean get() = editMode && screen == HomeScreen.AllGames

    /** True if X on [item] would take something away: the game's own emulator, or the platform's icon, title or emulator. */
    private fun canRemoveEditItem(item: EditItem?): Boolean {
        val platform = visiblePlatforms.getOrNull(selectedPlatformIndex)
        return when (item) {
            EditItem.CustomEmulator -> menuGame?.customEmulator != null
            EditItem.PlatformIcon -> platform != null && library.platformIcon(platform) != null
            EditItem.PlatformTitle -> platform != null && library.platformTitle(platform) != null
            EditItem.PlatformEmulator -> platform != null && library.platformEmulator(platform) != null
            else -> false
        }
    }

    private fun waveLabel(style: Int) = when (style) { WaveOriginal -> "ON"; WaveRibbon -> "CUSTOM"; else -> "OFF" }

    private fun onOff(value: Boolean) = if (value) "ON" else "OFF"

    private fun editItemLabel(item: EditItem): String = when (item) {
        EditItem.EditTitle -> "Edit Game Title"
        EditItem.SelectCover -> "Select Cover Image"
        EditItem.ToggleFavorite -> {
            val app = menuApp
            val favorite = if (app != null) library.isFavoriteId(AndroidGames.idFor(app.id)) else menuGame?.isFavorite == true
            if (favorite) "Remove From Favorites" else "Add To Favorites"
        }
        EditItem.AddToFolder -> if (openFolder != null) "Remove From Folder" else "Add To Folder"
        EditItem.CustomEmulator -> "Custom Emulator"
        EditItem.RemoveFromLibrary -> "Remove From Library"
        EditItem.EditMode -> if (editMode) "Exit Edit Mode" else "Edit Mode"
        EditItem.PlatformIcon -> "Select Platform Icon"
        EditItem.PlatformTitle -> "Edit Platform Title"
        EditItem.PlatformEmulator -> "Default Emulator"
        EditItem.EditAppTitle -> "Edit App Title"
        EditItem.HideApp -> "Hide App"
    }

    /** The hints for the bottom bar of the page currently shown. */
    val hints: List<ControlHint>
        get() = when {
            editMode && screen == HomeScreen.AllGames ->
                listOf(ControlHint("Y", "Sort"), ControlHint("X", "Remove"), ControlHint("A", if (onPlusRow) "Add" else "Move"), ControlHint("B", "Back"))
            editMode -> listOf(ControlHint("X", "Remove"), ControlHint("A", "Move"), ControlHint("B", "Back"))
            screen == HomeScreen.Apps -> listOf(ControlHint("X", "Games"))
            inFavView -> listOf(ControlHint("A", "Launch"), ControlHint("B", "Back"))
            screen == HomeScreen.AllGames ->
                listOf(ControlHint("Y", "Favorites"), ControlHint("X", "Apps"))
            else -> listOf(ControlHint("Y", "All Games"), ControlHint("X", "Apps"))
        }

    fun onKey(key: Key, isRepeat: Boolean): Boolean {
        if (pickerOpen) return onPickerKey(key, isRepeat)
        if (searchOpen) return onSearchKey(key, isRepeat)
        if (detailsGame != null) {
            if (!isRepeat && (key == Key.ButtonB || key == Key.Escape)) detailsGame = null
            return true
        }
        if (isEditingTitle) return onTitleEditKey(key, isRepeat)
        if (folderPickerOpen) return onFolderKey(key, isRepeat)
        if (moreOpen) return onMoreKey(key, isRepeat)
        // Start is handled by onStart (a tap opens the menu, holding it opens the system settings).
        if (key == Key.ButtonStart) return true
        if (menuOpen) return onMenuKey(key, isRepeat)
        when (key) {
            Key.ButtonB, Key.Escape, Key.Back -> {
                // Always consumed, so B with nothing to go back to doesn't fall through to Android's Back (recents).
                if (canStepBack() && !isRepeat) back()
                return true
            }
            // Select is handled on release (see onSelect), so holding it for a hotkey combo doesn't open the card.
            Key.ButtonSelect -> return true
            Key.ButtonY -> {
                if (!isRepeat) {
                    if (editMode && screen == HomeScreen.AllGames) toggleSort()
                    else if (editMode || inFavView) Unit
                    else if (screen != HomeScreen.Apps) switchTo(if (screen == HomeScreen.Favorites) HomeScreen.AllGames else HomeScreen.Favorites)
                }
                return true
            }
            Key.ButtonX -> {
                if (isRepeat) return true
                if (editMode) removeCurrent()
                else if (inFavView) Unit
                else switchTo(if (screen == HomeScreen.Apps) lastGamesScreen else HomeScreen.Apps)
                return true
            }
        }
        val nav = activeNav
        return if (nav != null) onGridKey(nav, key, isRepeat) else onAllGamesKey(key, isRepeat)
    }

    /** X in Edit Mode: takes the focused app off the Apps page, or the focused game out of Favorites. */
    private fun removeCurrent() {
        held = false
        when (screen) {
            HomeScreen.Apps -> apps.getOrNull(appsNav.index)?.let {
                appLibrary.hide(it.id)
                appsNav.clamp()
            }
            HomeScreen.Favorites -> favoriteGameAt(favoritesNav.index)?.let {
                when (val v = favView) {
                    is FavView.Folder -> library.toggleInFolder(v.id, it.id)
                    else -> library.toggleFavorite(it.id)
                }
                favoritesNav.clamp()
            }
            HomeScreen.AllGames -> gamesOnPlatform.getOrNull(gameIndex)?.let { library.removeGame(it.id) }
        }
    }

    private fun toggleSort() {
        val platform = selectedPlatform
        val current = gamesOnPlatform.getOrNull(gameIndex)
        settings.toggleSortByRecent()
        // Keep the cursor on the same game after the list reorders.
        if (platform != null && current != null) {
            gameIndexes[platform.id] = gamesFor(platform).indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        }
        showToast(if (settings.sortByRecent) "Sort: Recently Played" else "Sort: Alphabetical")
    }

    /**
     * Select pressed or released. A quick press and release opens the details card (so holding Select for a hotkey
     * combo doesn't); returns true when it did. Pressing Select while the card is up closes it.
     */
    fun onSelect(down: Boolean, isRepeat: Boolean): Boolean {
        if (down) {
            if (isRepeat) return false
            selectHeld = true
            selectCombo = false
            selectDownAt = android.os.SystemClock.uptimeMillis()
            selectIgnoreRelease = detailsGame != null
            if (selectIgnoreRelease) detailsGame = null
            return false
        }
        if (!selectHeld) return false
        selectHeld = false
        val quick = android.os.SystemClock.uptimeMillis() - selectDownAt <= SelectTapMillis
        if (selectCombo || selectIgnoreRelease || !quick || overlayOpen || editMode) return false
        detailsGame = currentGame()
        return detailsGame != null
    }

    /** A shoulder button while Select is held (a hotkey combo, Key Mapper's): it counts as one, so releasing Select does not open the details card. */
    fun onSelectCombo() {
        selectCombo = true
    }

    /** The system's volume or brightness changed (whoever changed it: hardware keys, Key Mapper's hotkeys): shows its bar. */
    fun onSystemLevel(kind: LevelKind) {
        levelKind = kind
        levelVisible = true
        levelStamp++
    }

    /** Select is no longer considered held (the app lost focus, or another button was pressed, so its release was missed). */
    fun releaseSelect() {
        selectHeld = false
    }

    fun hideLevel() {
        levelVisible = false
    }

    fun canStepBack() = searchOpen || menuOpen || isEditingTitle || held || editMode || screen == HomeScreen.Apps ||
        moreOpen || folderPickerOpen || favView != FavView.Favorites

    fun back() {
        when {
            searchOpen -> if (searchOnResults) searchOnResults = false else searchOpen = false
            pickerOpen -> pickerTarget = null
            isEditingTitle -> cancelTitleEdit()
            folderPickerOpen -> folderPickerOpen = false
            moreOpen -> moreOpen = false
            menuOpen -> if (menuFocus == MenuFocus.Actions) menuFocus = MenuFocus.Categories else menuOpen = false
            held -> held = false
            editMode -> changeEditMode(false)
            favView != FavView.Favorites -> leaveView()
            screen == HomeScreen.Apps -> switchTo(lastGamesScreen)
        }
    }

    private fun switchTo(target: HomeScreen) {
        if (target == screen) return
        if (screen != HomeScreen.Apps) lastGamesScreen = screen
        changeEditMode(false)
        if (target != HomeScreen.Favorites && favView != FavView.Favorites) {
            favView = FavView.Favorites
            favoritesNav.reset()
        }
        screen = target
        haptic()
    }

    /**
     * The right stick, one step per push: up/down turns the Apps pages, left/right the Favorites pages (the same
     * as L1/R1 there). Ignored while a menu or dialog is open.
     */
    fun onRightStick(direction: Direction): Boolean {
        // In the menu the stick turns the pages of a category that has them (Customization).
        if (menuOpen && !pickerOpen && !searchOpen && !isEditingTitle && detailsGame == null && menuPageCount > 1) {
            return when (direction) {
                Direction.Left -> changeMenuPage(-1)
                Direction.Right -> changeMenuPage(1)
                else -> false
            }
        }
        if (pickerOpen || searchOpen || menuOpen || isEditingTitle || detailsGame != null) return false
        val back = when {
            screen == HomeScreen.Apps && direction == Direction.Up -> true
            screen == HomeScreen.Apps && direction == Direction.Down -> false
            screen == HomeScreen.Favorites && direction == Direction.Left -> true
            screen == HomeScreen.Favorites && direction == Direction.Right -> false
            else -> return false
        }
        return onKey(if (back) Key.ButtonL1 else Key.ButtonR1, isRepeat = false)
    }

    private fun changeEditMode(on: Boolean) {
        editMode = on
        held = false
        if (on) editModeSound()
        if (!on) appsNav.clamp()
    }

    fun changeActivePage(delta: Int) {
        activeNav?.changePage(delta)
    }

    fun updateTitleEdit(value: TextFieldValue) {
        titleEditValue = value
    }

    fun saveTitleEdit() {
        val title = (if (ownKeyboardEntry) entryText else titleEditValue.text).trim()
        if (newFolderEntry) {
            if (title.isNotEmpty()) {
                val folder = library.createFolder(title)
                val gameId = folderPickerGameId
                if (gameId != null) {
                    library.toggleInFolder(folder.id, gameId)
                    folderPickerOpen = false
                    showToast("Added to \"${folder.name}\"")
                } else {
                    folderIndex = library.folders.lastIndex
                }
            }
            cancelTitleEdit()
            return
        }
        if (gameHubStep == 1) {
            if (title.isNotEmpty()) {
                gameHubTitle = title
                gameHubStep = 2
                titleEditValue = TextFieldValue("")
            }
            return
        }
        if (gameHubStep == 2) {
            if (title.isNotEmpty()) actions.addGameHubGame(gameHubTitle, title)
            cancelTitleEdit()
            return
        }
        if (title.isNotEmpty()) {
            titleEditGameId?.let { library.setTitle(it, title) }
            titleEditAppId?.let { appLibrary.setTitle(it, title) }
            titleEditPlatformId?.let { library.setPlatformTitle(it, title) }
        }
        cancelTitleEdit()
    }

    fun cancelTitleEdit() {
        newFolderEntry = false
        gameHubStep = 0
        titleEditGameId = null
        titleEditAppId = null
        titleEditPlatformId = null
    }

    private fun currentGame(): GameEntry? = when (screen) {
        HomeScreen.Favorites -> favoriteGameAt(favoritesNav.index)
        HomeScreen.AllGames -> gamesOnPlatform.getOrNull(gameIndex)
        HomeScreen.Apps -> null
    }

    private fun toggleMenu() {
        menuOpen = !menuOpen
        if (!menuOpen) return
        held = false
        menuContext = when (screen) {
            HomeScreen.Apps -> MenuContext.Apps
            HomeScreen.AllGames -> MenuContext.AllGames
            HomeScreen.Favorites -> MenuContext.Favorites
        }
        menuGame = currentGame()
        menuApp = if (screen == HomeScreen.Apps) apps.getOrNull(appsNav.index) else null
        menuCategory = MenuCategory.EditGame
        menuPage = 0
        menuFocus = MenuFocus.Categories
        menuAction = 0
    }

    private fun directionOf(key: Key): Direction? = when (key) {
        Key.DirectionUp -> Direction.Up
        Key.DirectionDown -> Direction.Down
        Key.DirectionLeft -> Direction.Left
        Key.DirectionRight -> Direction.Right
        else -> null
    }

    private fun onGridKey(nav: GridNav, key: Key, isRepeat: Boolean): Boolean {
        val direction = directionOf(key)
        when {
            direction != null -> nav.navigate(direction, held)
            key == Key.ButtonL1 -> if (held) nav.movePageHeld(-1) else nav.changePage(-1)
            key == Key.ButtonR1 -> if (held) nav.movePageHeld(1) else nav.changePage(1)
            key == Key.ButtonA || key == Key.Enter || key == Key.DirectionCenter -> if (!isRepeat) onConfirm(nav)
            else -> return false
        }
        return true
    }

    private fun onConfirm(nav: GridNav) {
        if (editMode) {
            if (nav.currentExists) held = !held
            else if (nav === appsNav) openPicker(PickerTarget.Apps)
        } else if (nav === favoritesNav) {
            if (hasLauncher && nav.index == LauncherFlat) {
                openMore()
                return
            }
            pressTick++
            favoriteGameAt(nav.index)?.let(actions.launchGame)
        } else if (nav === appsNav) {
            pressTick++
            apps.getOrNull(nav.index)?.let(actions.launchApp)
        }
    }

    /** Touch on the Apps page works only on the plain page: not in Edit Mode and not under a menu, picker, search or dialog. */
    private fun touchable(on: HomeScreen): Boolean =
        screen == on && !editMode && !menuOpen && !pickerOpen && !searchOpen && detailsGame == null && !isEditingTitle &&
            !moreOpen && !folderPickerOpen

    /** Edit Mode by touch works on the page being edited, as long as nothing is open over it. */
    private fun editTouchable(on: HomeScreen): Boolean =
        screen == on && editMode && !menuOpen && !pickerOpen && !searchOpen && detailsGame == null && !isEditingTitle &&
            !moreOpen && !folderPickerOpen

    private val editNav: GridNav? get() = when (screen) {
        HomeScreen.Favorites -> favoritesNav
        HomeScreen.Apps -> appsNav
        HomeScreen.AllGames -> null
    }

    private var lastEdgeFlip = 0L

    /** Edit Mode, touch: a long press on the tile in [slot] picks it up. */
    fun touchEditHold(on: HomeScreen, slot: Int) {
        if (!editTouchable(on)) return
        val nav = editNav ?: return
        val flat = nav.flatOf(slot)
        if (!nav.existsAt(flat)) return
        nav.focusAt(flat)
        held = true
        haptic()
    }

    /** Edit Mode, touch: the picked-up tile is dragged over [slot] and takes that place. */
    fun touchEditDrag(on: HomeScreen, slot: Int) {
        if (!editTouchable(on) || !held) return
        editNav?.let { it.dragHeldTo(it.flatOf(slot)) }
    }

    /** Edit Mode, touch: the picked-up tile is at the panel's edge toward the previous ([delta] -1) or next page; turns the page now and then. */
    fun touchEditEdge(on: HomeScreen, delta: Int) {
        if (!editTouchable(on) || !held) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastEdgeFlip < EdgeFlipMillis) return
        lastEdgeFlip = now
        editNav?.movePageHeld(delta)
    }

    /** Edit Mode, touch: the finger lets go, which puts the tile (or platform) down. */
    fun touchEditRelease() {
        if (editMode && held) held = false
    }

    /** Edit Mode on All Games: a long press on platform [index] picks it up. */
    fun touchEditHoldPlatform(index: Int) {
        if (!editTouchable(HomeScreen.AllGames) || index !in visiblePlatforms.indices) return
        platformIndex = index
        held = true
        haptic()
    }

    /** Edit Mode on All Games: the picked-up platform is dragged over platform [index] and takes its place in the row. */
    fun touchEditDragPlatform(index: Int) {
        if (!editTouchable(HomeScreen.AllGames) || !held) return
        val platform = selectedPlatform ?: return
        if (index in visiblePlatforms.indices && index != selectedPlatformIndex) movePlatformBy(platform, index - selectedPlatformIndex)
    }

    /** A tap on the icon in [slot] of the current Apps page: focus it and launch it. */
    fun tapApp(slot: Int) {
        if (editTouchable(HomeScreen.Apps)) {
            // Edit Mode: a tap focuses the app (like moving the cursor there); on an empty slot it opens the app picker (like A).
            val flat = appsNav.flatOf(slot)
            appsNav.focusAt(flat)
            if (!appsNav.existsAt(flat)) openPicker(PickerTarget.Apps)
            return
        }
        if (!touchable(HomeScreen.Apps)) return
        val flat = appsNav.page * BentoLayouts.Apps.size + slot
        val app = apps.getOrNull(flat) ?: return
        appsNav.focusAt(flat)
        pressTick++
        actions.launchApp(app)
    }

    /** A vertical swipe on the Apps page: [delta] 1 for the next page, -1 for the previous. */
    fun swipeAppsPage(delta: Int) {
        if (touchable(HomeScreen.Apps)) appsNav.changePage(delta)
    }

    /** A tap on the tile in [slot] of the current Favorites page: focus it and launch it. */
    fun tapFavorite(slot: Int) {
        if (editTouchable(HomeScreen.Favorites)) {
            val flat = favoritesNav.flatOf(slot)
            if (favoritesNav.existsAt(flat)) favoritesNav.focusAt(flat)
            return
        }
        if (!touchable(HomeScreen.Favorites)) return
        val flat = favoritesNav.page * FavoritesPageSize + slot
        if (hasLauncher && flat == LauncherFlat) {
            favoritesNav.focusAt(flat)
            openMore()
            menuSound()
            return
        }
        val game = favoriteGameAt(flat) ?: return
        favoritesNav.focusAt(flat)
        pressTick++
        actions.launchGame(game)
    }

    /** A horizontal swipe on Favorites: [delta] 1 for the next page, -1 for the previous. */
    fun swipeFavoritesPage(delta: Int) {
        if (touchable(HomeScreen.Favorites)) favoritesNav.changePage(delta)
    }

    private var lastXmbStep = 0L

    private fun onAllGamesKey(key: Key, isRepeat: Boolean): Boolean {
        // Holding a direction repeats very fast; one step per XmbRepeatMillis keeps the glide smooth.
        if (isRepeat) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastXmbStep < XmbRepeatMillis) return key in XmbKeys
            lastXmbStep = now
        }
        val platform = selectedPlatform
        when (key) {
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> if (editMode) {
                // Edit Mode: on the "+" row A adds a game; elsewhere it picks the platform up (so left/right move it along the row) and puts it down.
                if (!isRepeat && onPlusRow) activateXmbRow()
                else if (!isRepeat && platform != null) held = !held
            } else {
                if (!isRepeat) activateXmbRow()
            }
            Key.DirectionLeft, Key.DirectionRight -> if (editMode && held && platform != null) {
                movePlatformBy(platform, if (key == Key.DirectionLeft) -1 else 1)
            } else {
                val before = selectedPlatformIndex
                platformIndex = if (key == Key.DirectionLeft) (before - 1).coerceAtLeast(0)
                else (before + 1).coerceAtMost(maxOf(visiblePlatforms.lastIndex, 0))
                if (platformIndex != before) haptic()
            }
            Key.DirectionUp -> if (platform != null) gameIndexes[platform.id] = (gameIndex - 1).coerceAtLeast(0)
            Key.DirectionDown ->
                if (platform != null) {
                    gameIndexes[platform.id] = (gameIndex + 1).coerceAtMost(maxOf(rowCount(platform) - 1, 0))
                }
            else -> return false
        }
        return true
    }

    /** True while the focused row of All Games is its "+" row (only there in Edit Mode, on Android and Steam). */
    private val onPlusRow: Boolean
        get() = editMode && screen == HomeScreen.AllGames && selectedPlatform?.id in PlusPlatforms && gameIndex >= gamesOnPlatform.size

    /** A on the focused row of All Games: launches the game, or (on the "+" row) opens the add flow. */
    private fun activateXmbRow() {
        val platform = selectedPlatform
        val game = gamesOnPlatform.getOrNull(gameIndex)
        pressTick++
        if (game != null) actions.launchGame(game)
        else if (platform?.id == "android") openPicker(PickerTarget.AndroidGames)
        else if (platform?.id == "steam") {
            gameHubStep = 1
            titleEditValue = TextFieldValue("")
        }
    }

    // ---- Touch on the rest of the UI: simple taps and swipes that do what the buttons do. Search and the details card
    // are opened with their buttons only; touch just works inside them.

    /** A tap on platform [index] in the All Games row: select it. */
    fun touchPlatform(index: Int) {
        if (!touchable(HomeScreen.AllGames) && !editTouchable(HomeScreen.AllGames)) return
        if (index in visiblePlatforms.indices && index != selectedPlatformIndex) {
            platformIndex = index
            haptic()
        }
    }

    /** A tap on game [row] of the selected platform: the focused one launches, another one is selected first. */
    fun touchGame(row: Int) {
        if (editTouchable(HomeScreen.AllGames)) {
            // Edit Mode: only the "+" row answers a tap (the other rows have nothing to do there).
            val platform = selectedPlatform ?: return
            if (platform.id in PlusPlatforms && row == gamesOnPlatform.size) {
                if (row == gameIndex) activateXmbRow() else gameIndexes[platform.id] = row
            }
            return
        }
        if (!touchable(HomeScreen.AllGames)) return
        val platform = selectedPlatform ?: return
        if (row == gameIndex) activateXmbRow() else if (row in 0 until rowCount(platform)) gameIndexes[platform.id] = row
    }

    /** A swipe on All Games: [dx] steps along the platforms, [dy] steps down the games (each -1, 0 or 1). */
    fun swipeXmb(dx: Int, dy: Int) {
        if (!touchable(HomeScreen.AllGames)) return
        val platform = selectedPlatform ?: return
        if (dx != 0) {
            val before = selectedPlatformIndex
            platformIndex = (before + dx).coerceIn(0, maxOf(visiblePlatforms.lastIndex, 0))
            if (platformIndex != before) haptic()
        } else if (dy != 0) {
            gameIndexes[platform.id] = (gameIndex + dy).coerceIn(0, maxOf(rowCount(platform) - 1, 0))
        }
    }

    fun touchMenuCategory(index: Int) {
        if (!menuOpen) return
        selectCategory(index)
        menuFocus = MenuFocus.Categories
    }

    fun touchMenuEntry(index: Int) {
        val entry = menuEntries.getOrNull(index) ?: return
        if (!menuOpen) return
        menuFocus = MenuFocus.Actions
        menuAction = index
        if (entry.slider == null) {
            confirmSound()
            performMenuAction()
        }
    }

    fun touchMenuOutside() {
        if (menuOpen) menuOpen = false
    }

    /** The ROM folders and the hidden games are long lists that scroll. */
    private val menuListScrolls: Boolean
        get() = menuCategory == MenuCategory.RomFolders || (menuCategory == MenuCategory.EditGame && menuPage == 1)

    /** Finger-scrolling the ROM folders list of the menu: [delta] entries on. */
    fun touchMenuStep(delta: Int) {
        if (!menuOpen || !menuListScrolls) return
        val count = menuEntries.size
        if (count == 0) return
        menuFocus = MenuFocus.Actions
        val before = menuAction
        menuAction = (menuAction + delta).coerceIn(0, count - 1)
        if (menuAction != before) haptic()
    }

    /** The ROM folders list's scroll slider, dragged to [fraction] (0..1) of the list. */
    fun touchMenuSeek(fraction: Float) {
        if (!menuOpen || !menuListScrolls) return
        val count = menuEntries.size
        if (count == 0) return
        menuFocus = MenuFocus.Actions
        menuAction = (fraction * (count - 1)).roundToInt().coerceIn(0, count - 1)
    }

    /** A swipe (or a tap on a page dot) in the menu: [delta] pages forward or back. */
    fun touchMenuPage(delta: Int) {
        if (menuOpen) changeMenuPage(delta)
    }

    /** The volume slider of the Customization menu, set by a finger. */
    fun touchSetVolume(value: Int) {
        if (!menuOpen || menuCategory != MenuCategory.Customize || menuPage != AudioPage) return
        menuFocus = MenuFocus.Actions
        menuAction = AudioVolumeIndex
        settings.seekVolume(value)
    }

    /** Turns the open menu category to page [menuPage] + [delta] (with the page sound); false when there is no such page. */
    private fun changeMenuPage(delta: Int): Boolean {
        val target = (menuPage + delta).coerceIn(0, menuPageCount - 1)
        if (target == menuPage) return false
        menuPage = target
        menuAction = 0
        haptic()
        pageSound()
        return true
    }

    fun touchPicker(index: Int) {
        if (!pickerOpen || index !in pickerItems.indices) return
        pickerIndex = index
        pickerConfirm()
    }

    fun touchPickerOutside() {
        pickerTarget = null
    }

    fun touchPickerRows(rows: Int) {
        if (pickerOpen) pickerIndex = (pickerIndex + rows * PickerColumns).coerceIn(0, maxOf(pickerItems.lastIndex, 0))
    }

    /** The app picker's scroll slider, dragged to [fraction] (0..1) of its rows. */
    fun touchPickerSeek(fraction: Float) {
        if (!pickerOpen || pickerItems.isEmpty()) return
        val rows = (pickerItems.size + PickerColumns - 1) / PickerColumns
        val row = (fraction * (rows - 1)).roundToInt().coerceIn(0, rows - 1)
        pickerIndex = (row * PickerColumns + pickerIndex % PickerColumns).coerceAtMost(pickerItems.lastIndex)
    }

    fun touchFolderStep(delta: Int) {
        if (folderPickerOpen && !newFolderEntry) folderIndex = (folderIndex + delta).coerceIn(0, library.folders.size)
    }

    fun touchFolderSeek(fraction: Float) {
        if (folderPickerOpen && !newFolderEntry) folderIndex = (fraction * library.folders.size).roundToInt().coerceIn(0, library.folders.size)
    }

    fun touchSearchStep(delta: Int) {
        if (!searchOpen || searchHits.isEmpty()) return
        searchOnResults = true
        searchIndex = (searchIndex + delta).coerceIn(0, searchHits.lastIndex)
    }

    fun touchSearchSeek(fraction: Float) {
        if (!searchOpen || searchHits.isEmpty()) return
        searchOnResults = true
        searchIndex = (fraction * (searchHits.size - 1)).roundToInt().coerceIn(0, searchHits.lastIndex)
    }

    fun touchSearchResult(index: Int) {
        if (!searchOpen || index !in searchHits.indices) return
        searchIndex = index
        searchOnResults = true
        submitSearch()
    }

    /** A tap on a key of the search keyboard: the cursor goes there and the key is pressed. */
    fun touchSearchKey(index: Int) {
        if (!searchOpen || searchOnResults) return
        searchKey = index
        if (SearchKeyboardLayout.keysFor(searchDigits).getOrNull(index)?.action != KeyAction.Shift) keySound()
        pressSearchKey(index)
    }

    fun touchSearchOutside() {
        searchOpen = false
    }

    fun touchCloseDetails() {
        detailsGame = null
    }

    /** Edit Mode on All Games: moves the picked-up platform one place along the row. */
    private fun movePlatformBy(platform: Platform, delta: Int) {
        val target = visiblePlatforms.getOrNull(selectedPlatformIndex + delta) ?: return
        settings.movePlatform(platform.id, target.id)
        platformIndex = visiblePlatforms.indexOfFirst { it.id == platform.id }.coerceAtLeast(0)
        haptic()
    }

    private fun onMenuKey(key: Key, isRepeat: Boolean): Boolean {
        val inCategories = menuFocus == MenuFocus.Categories
        val onVolume = !inCategories && menuCategory == MenuCategory.Customize && menuPage == AudioPage && menuAction == AudioVolumeIndex
        when (key) {
            Key.DirectionUp ->
                if (inCategories) selectCategory(menuCategory.ordinal - 1) else moveInMenu(Direction.Up)
            Key.DirectionDown ->
                if (inCategories) selectCategory(menuCategory.ordinal + 1) else moveInMenu(Direction.Down)
            Key.DirectionRight ->
                when {
                    inCategories -> if (!isRepeat) enterActions()
                    onVolume -> settings.changeVolume(VolumeStep)
                    else -> moveInMenu(Direction.Right)
                }
            Key.DirectionLeft ->
                when {
                    inCategories -> Unit
                    onVolume -> settings.changeVolume(-VolumeStep)
                    else -> if (!moveInMenu(Direction.Left)) menuFocus = MenuFocus.Categories
                }
            Key.ButtonA, Key.Enter, Key.DirectionCenter ->
                if (!isRepeat) {
                    if (inCategories) enterActions() else performMenuAction()
                }
            Key.ButtonX ->
                if (!isRepeat && !inCategories) performSecondaryAction()
            Key.ButtonY ->
                if (!isRepeat && !inCategories) {
                    if (menuCategory == MenuCategory.RomFolders) actions.rescan()
                    else if (menuCategory == MenuCategory.Customize && menuPage == 0 && menuAction == WaveRow) actions.saveWave()
                }
            Key.ButtonL1 -> if (!isRepeat) changeMenuPage(-1)
            Key.ButtonR1 -> if (!isRepeat) changeMenuPage(1)
            Key.ButtonB, Key.Escape -> if (!isRepeat) back()
            else -> return false
        }
        return true
    }

    /** Moves the highlighted entry one step; false if there is nowhere to go that way. */
    private fun moveInMenu(direction: Direction): Boolean {
        val target: Int? = when (menuCategory.layout(menuPage)) {
            MenuLayout.List, MenuLayout.Settings, MenuLayout.Folders, MenuLayout.HiddenList -> when (direction) {
                Direction.Up -> menuAction - 1
                Direction.Down -> menuAction + 1
                else -> null
            }
            MenuLayout.Grid -> when (direction) {
                Direction.Up -> menuAction - 2
                Direction.Down -> menuAction + 2
                Direction.Left -> if (menuAction % 2 == 1) menuAction - 1 else null
                Direction.Right -> if (menuAction % 2 == 0) menuAction + 1 else null
            }
            // Rows: sounds, music, pick, volume.
            MenuLayout.Audio -> when (direction) {
                Direction.Up -> menuAction - 1
                Direction.Down -> menuAction + 1
                else -> null
            }
        }
        if (target == null || target !in menuEntries.indices) return false
        menuAction = target
        return true
    }

    private fun selectCategory(ordinal: Int) {
        val target = MenuCategory.entries[ordinal.coerceIn(0, MenuCategory.entries.lastIndex)]
        if (target != menuCategory) {
            menuCategory = target
            menuPage = 0
            menuAction = 0
        }
    }

    private fun enterActions() {
        if (menuEntries.isNotEmpty()) {
            menuFocus = MenuFocus.Actions
            menuAction = 0
        }
    }

    private fun performMenuAction() {
        when (menuCategory) {
            MenuCategory.EditGame ->
                if (menuPage == 1) unhideGame(menuAction) else editItems.getOrNull(menuAction)?.let(::performEditItem)
            MenuCategory.Customize -> when (menuPage) {
                0 -> when (menuAction) {
                    0 -> settings.cycleWaveStyle()
                    1 -> settings.toggleTouchGestures()
                    2 -> toggleEditModeFromMenu()
                    3 -> settings.cycleUiFps()
                    // Custom opens the video picker (it stays on the default one if none is picked); going back forgets the video.
                    4 -> if (settings.bootVideoUri == null) actions.pickBootVideo() else settings.setBootVideo(null)
                    5 -> settings.toggleAmbientClock()
                }
                AudioPage -> when (menuAction) {
                    0 -> settings.toggleMenuSounds()
                    1 -> settings.toggleBackgroundMusic()
                    2 -> actions.pickBackgroundMusic()
                }
                else -> menuPlatforms.getOrNull(menuAction)?.let { settings.setPlatformEnabled(it.id, !settings.isPlatformEnabled(it.id)) }
                    .also { clampSelections() }
            }
            MenuCategory.RomFolders ->
                if (menuAction == settings.romFolders.size) actions.pickRomFolder()
            MenuCategory.Backup -> when (menuAction) {
                0 -> actions.createBackup()
                1 -> actions.restoreBackup()
            }
        }
    }

    /** A on the hidden games page: the game is back in the library (the menu stays open on the shorter list). */
    private fun unhideGame(index: Int) {
        val game = hiddenGames.getOrNull(index) ?: return
        library.restoreGame(game.id)
        menuAction = menuAction.coerceAtMost((hiddenGames.size - 1).coerceAtLeast(0))
    }

    /** Edit Mode, from the Customization menu: not offered in the Recently Played view (nothing there can be arranged). */
    private fun toggleEditModeFromMenu() {
        if (screen == HomeScreen.Favorites && favView == FavView.Recent) {
            showToast("Edit Mode isn't available here")
            return
        }
        changeEditMode(!editMode)
        menuOpen = false
        clampSelections()
    }

    /** X on an entry of Edit Current Game / Platform: takes the game's emulator, or the platform's icon, title or emulator, back to the default. */
    private fun removeEditItem(item: EditItem?) {
        val platform = visiblePlatforms.getOrNull(selectedPlatformIndex)
        when (item) {
            EditItem.CustomEmulator -> menuGame?.let { library.setCustomEmulator(it.id, null) }
            EditItem.PlatformIcon -> platform?.let { library.setPlatformIcon(it.id, null) }
            EditItem.PlatformTitle -> platform?.let { library.setPlatformTitle(it.id, null) }
            EditItem.PlatformEmulator -> platform?.let { library.setPlatformEmulator(it.id, null) }
            else -> Unit
        }
    }

    /** The X button: removes the selected ROM folder from the list. */
    private fun performSecondaryAction() {
        when (menuCategory) {
            MenuCategory.Customize -> if (menuPage == 0 && menuAction == WaveRow) settings.randomizeWave()
            MenuCategory.EditGame -> if (menuPage == 0) removeEditItem(editItems.getOrNull(menuAction))
            MenuCategory.RomFolders -> {
                settings.removeRomFolder(menuAction)
                menuAction = menuAction.coerceAtMost(menuEntries.lastIndex)
            }
            else -> Unit
        }
    }

    private fun performEditItem(item: EditItem) {
        val game = menuGame
        val app = menuApp
        when (item) {
            EditItem.EditTitle -> game?.let {
                menuOpen = false
                titleEditGameId = it.id
                startEntry(it.title)
            }
            EditItem.EditAppTitle -> app?.let {
                menuOpen = false
                titleEditAppId = it.id
                startEntry(it.title)
            }
            EditItem.SelectCover -> game?.let {
                menuOpen = false
                actions.pickCover(it.id, menuContext == MenuContext.AllGames)
            }
            EditItem.ToggleFavorite ->
                if (game != null) library.toggleFavorite(game.id)
                else app?.let { favoriteApp(it) }
            EditItem.AddToFolder -> game?.let {
                val folder = openFolder
                if (folder != null) {
                    // Inside a folder the entry takes the game out of it again (it stays in the library).
                    library.toggleInFolder(folder.id, it.id)
                } else {
                    menuOpen = false
                    openFolderPicker(it.id)
                }
            }
            EditItem.CustomEmulator -> game?.let {
                emulatorTargetId = it.id
                openPicker(PickerTarget.CustomEmulator)
            }
            EditItem.PlatformIcon -> visiblePlatforms.getOrNull(selectedPlatformIndex)?.let { actions.pickPlatformIcon(it.id) }
            EditItem.PlatformTitle -> visiblePlatforms.getOrNull(selectedPlatformIndex)?.let {
                titleEditPlatformId = it.id
                startEntry(library.platformTitle(it) ?: it.menuLabel)
            }
            EditItem.PlatformEmulator -> visiblePlatforms.getOrNull(selectedPlatformIndex)?.let {
                emulatorTargetId = it.id
                openPicker(PickerTarget.PlatformEmulator)
            }
            EditItem.RemoveFromLibrary -> game?.let { library.removeGame(it.id) }
            EditItem.HideApp -> app?.let { appLibrary.hide(it.id) }
            EditItem.EditMode -> changeEditMode(!editMode)
        }
        menuOpen = false
        clampSelections()
    }

    /**
     * Adds an app of the Apps page to the Favorites (or takes it out again). Favorites hold games, so the app is registered
     * as an Android game first (kept out of the Android platform's list in All Games), under its current name and icon.
     */
    private fun favoriteApp(app: AppEntry) {
        val id = AndroidGames.idFor(app.id)
        if (!library.isFavoriteId(id)) actions.registerAppAsGame(app)
        library.toggleFavorite(id)
    }

    private fun clampSelections() {
        favoritesNav.clamp()
        appsNav.clamp()
    }

    private fun onEntryKey(key: Key, isRepeat: Boolean): Boolean {
        when (key) {
            Key.ButtonB, Key.Escape -> if (!isRepeat) cancelTitleEdit()
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> pressEntryKey(entryKey)
            Key.ButtonX -> pressEntryKey(SearchKeyboardLayout.keysFor(entryDigits).indexOfFirst { it.action == KeyAction.Delete })
            Key.ButtonY -> pressEntryKey(SearchKeyboardLayout.keysFor(entryDigits).indexOfFirst { it.action == KeyAction.Space })
            Key.ButtonL1 -> if (!isRepeat && !entryDigits) {
                keySound()
                entryCaps = !entryCaps
            }
            Key.ButtonR1 -> if (!isRepeat) {
                switchSound()
                entryDigits = !entryDigits
                entryKey = 0
            }
            Key.DirectionLeft -> entryKey = SearchKeyboardLayout.move(entryKey, -1, 0, entryDigits)
            Key.DirectionRight -> entryKey = SearchKeyboardLayout.move(entryKey, 1, 0, entryDigits)
            Key.DirectionUp -> entryKey = SearchKeyboardLayout.move(entryKey, 0, -1, entryDigits)
            Key.DirectionDown -> entryKey = SearchKeyboardLayout.move(entryKey, 0, 1, entryDigits)
            else -> return false
        }
        return true
    }

    private fun pressEntryKey(index: Int) {
        val key = SearchKeyboardLayout.keysFor(entryDigits).getOrNull(index) ?: return
        when (key.action) {
            KeyAction.Letter -> SearchKeyboardLayout.charFor(index, entryDigits)?.let(::typeEntry)
            KeyAction.Space ->
                if (entryText.isNotEmpty() && !entryText.endsWith(' ') && entryText.length < EntryMaxLength) {
                    entrySelected = false
                    entryText += " "
                }
            KeyAction.Delete ->
                if (entrySelected) {
                    entryText = ""
                    entrySelected = false
                } else {
                    entryText = entryText.dropLast(1)
                }
            KeyAction.Enter -> saveTitleEdit()
            KeyAction.Shift -> {
                keySound()
                entryCaps = !entryCaps
            }
        }
    }

    /** Types [c]: letters start a word with a capital (all capitals with Caps on), digits are as they are. */
    private fun typeEntry(c: Char) {
        if (entrySelected) {
            entryText = ""
            entrySelected = false
        }
        if (entryText.length >= EntryMaxLength) return
        val upper = c.isLetter() && (entryCaps || entryText.isEmpty() || entryText.endsWith(' '))
        entryText += if (upper) c.uppercaseChar() else c.lowercaseChar()
    }

    /** A tap on a key of the title keyboard. */
    fun touchEntryKey(index: Int) {
        if (!ownKeyboardEntry) return
        entryKey = index
        if (SearchKeyboardLayout.keysFor(entryDigits).getOrNull(index)?.action != KeyAction.Shift) keySound()
        pressEntryKey(index)
    }

    private fun onTitleEditKey(key: Key, isRepeat: Boolean): Boolean = if (ownKeyboardEntry) onEntryKey(key, isRepeat) else onSystemTitleEditKey(key, isRepeat)

    private fun onSystemTitleEditKey(key: Key, isRepeat: Boolean): Boolean = when (key) {
        Key.ButtonB, Key.Escape -> {
            if (!isRepeat) cancelTitleEdit()
            true
        }
        Key.ButtonA, Key.Enter -> {
            if (!isRepeat) saveTitleEdit()
            true
        }
        else -> false
    }

    /**
     * Cursor over a paged grid of slots. The items are one flat list; page and slot are the cursor's
     * position in it. While an item is held, moving the cursor reorders the list instead.
     */
    abstract inner class GridNav(protected val pageSize: Int) {
        var page by mutableIntStateOf(0)
            private set
        var slot by mutableIntStateOf(0)
            private set

        abstract val itemCount: Int
        protected open val emptySlotsFocusable: Boolean get() = false
        protected abstract fun moveItem(from: Int, to: Int)

        /** Where moving [direction] from the edge of the page leads (another page), if anywhere. */
        protected open fun edgeTarget(direction: Direction): Int? = null

        /** The slot layout of [page]; pages may differ, but all hold [pageSize] slots. */
        abstract fun slotsOn(page: Int): List<BentoSlot>

        open val pageCount: Int get() = maxOf(1, (itemCount + pageSize - 1) / pageSize)
        val index: Int get() = page * pageSize + slot

        protected open fun isFocusable(flat: Int): Boolean = emptySlotsFocusable || flat < itemCount

        open val currentExists: Boolean get() = index < itemCount

        /** The last position a held item can be moved to. */
        protected open val lastIndex: Int get() = itemCount - 1

        private val maxIndex: Int
            get() = (pageCount * pageSize - 1 downTo 0).firstOrNull(::isFocusable) ?: 0

        private fun slotFocusable(p: Int, s: Int) = isFocusable(p * pageSize + s)

        /** The flat index of [slot] on the current page. */
        fun flatOf(slot: Int) = page * pageSize + slot

        /** Whether there is an item (not just an empty slot) at [flat]. */
        open fun existsAt(flat: Int): Boolean = flat < itemCount

        /** Touch: while an item is picked up, moves it to the place of [flat] (the others make room). */
        fun dragHeldTo(flat: Int) {
            if (flat != index && existsAt(flat)) moveHeldTo(flat)
        }

        fun reset() = focus(0)

        fun clamp() = focus(index)

        fun focusAt(flat: Int) = focus(flat)

        private fun focus(flat: Int) {
            var i = flat.coerceIn(0, maxIndex)
            while (i > 0 && !isFocusable(i)) i--
            if (!isFocusable(i)) i = (0..maxIndex).firstOrNull(::isFocusable) ?: 0
            page = i / pageSize
            slot = i % pageSize
        }

        fun changePage(delta: Int) {
            val target = (page + delta).coerceIn(0, pageCount - 1)
            if (target == page) return
            haptic()
            pageSound()
            page = target
            slot = (slot downTo 0).firstOrNull { slotFocusable(target, it) }
                ?: (0 until pageSize).firstOrNull { slotFocusable(target, it) } ?: 0
        }

        fun navigate(direction: Direction, holding: Boolean) {
            val neighbor = findNeighbor(slotsOn(page), { slotFocusable(page, it) }, slot, direction)
            val flat = if (neighbor != null) page * pageSize + neighbor else edgeTarget(direction) ?: return
            if (holding) moveHeldTo(flat) else focus(flat)
        }

        fun movePageHeld(delta: Int) {
            val target = page + delta
            if (target in 0 until pageCount) {
                pageSound()
                moveHeldTo(target * pageSize + slot)
            }
        }

        private fun moveHeldTo(flat: Int) {
            val to = flat.coerceIn(0, maxOf(lastIndex, 0))
            if (to != index && currentExists) moveItem(index, to)
            focus(to)
        }
    }

    /** Favorites: the first page is the bento (large, medium and small covers); later pages are eight equal tiles. */
    inner class FavoritesNav : GridNav(FavoritesPageSize) {
        // With the launcher tile (the favorites view) the first page holds seven games and the tile; the tile is a
        // focusable position of its own, except in Edit Mode (there it only keeps its place).
        override val itemCount: Int
            get() = if (hasLauncher) maxOf(viewGames.size + 1, FavoritesPageSize) else viewGames.size
        override val currentExists: Boolean get() = favoriteGameAt(index) != null
        override fun existsAt(flat: Int): Boolean = favoriteGameAt(flat) != null
        override val lastIndex: Int
            get() = if (viewGames.isEmpty()) 0 else flatOfGameIndex(viewGames.size - 1)
        override fun isFocusable(flat: Int): Boolean =
            favoriteGameAt(flat) != null || (hasLauncher && !editMode && flat == LauncherFlat)
        // Folders and Recently Played are plain grids of eight on every page; the favorites start with the bento.
        override fun slotsOn(page: Int): List<BentoSlot> =
            if (hasLauncher && page == 0) BentoLayouts.Default else BentoLayouts.Grid
        override fun moveItem(from: Int, to: Int) {
            val a = favoriteGameAt(from) ?: return
            val b = favoriteGameAt(to) ?: return
            when (val v = favView) {
                FavView.Favorites -> library.moveFavorite(a.id, b.id)
                is FavView.Folder -> library.moveInFolder(v.id, a.id, b.id)
                FavView.Recent -> Unit
            }
        }
    }

    /** The Apps page: pages stack vertically, so moving up/down off the top/bottom row changes page. */
    inner class AppsNav : GridNav(BentoLayouts.Apps.size) {
        override fun slotsOn(page: Int): List<BentoSlot> = BentoLayouts.Apps
        override val itemCount: Int get() = apps.size
        override val emptySlotsFocusable: Boolean get() = editMode

        // In Edit Mode there is always one more page than the apps need, when the last one is full: a page of "+" slots
        // waiting to be filled (so a full page never stops you adding more apps).
        override val pageCount: Int
            get() = if (editMode) maxOf(1, (itemCount + 1 + pageSize - 1) / pageSize) else super.pageCount

        override fun moveItem(from: Int, to: Int) {
            val list = apps
            appLibrary.move(list[from].id, list[to].id)
        }

        override fun edgeTarget(direction: Direction): Int? = when {
            direction == Direction.Up && slot < FirstRowSize && page > 0 ->
                (page - 1) * BentoLayouts.Apps.size + slot + (BentoLayouts.Apps.size - FirstRowSize)
            direction == Direction.Down && slot >= BentoLayouts.Apps.size - FirstRowSize && page < pageCount - 1 ->
                (page + 1) * BentoLayouts.Apps.size + slot - (BentoLayouts.Apps.size - FirstRowSize)
            else -> null
        }
    }

    private companion object {
        const val FirstRowSize = 3
        const val FavoritesPageSize = 8
        const val SearchMaxHits = 20
        const val SearchMaxQuery = 28
        const val EntryMaxLength = 32
        val PlusPlatforms = setOf("android", "steam")
        const val XmbRepeatMillis = 55L
        const val EdgeFlipMillis = 750L
        val XmbKeys = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)
        const val AudioVolumeIndex = 3
        const val AudioPage = 1
        val PlatformMenuOrder =
            listOf("android", "steam", "xbox", "n3ds", "psp", "wiiu", "ps2", "switch", "vita", "windows")
    }
}
