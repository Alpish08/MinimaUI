package com.minimaui.app.model

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.minimaui.app.data.LibraryStore

/** A named group of games the user made (the Folder in the Favorites menu); [gameIds] keep the user's order. */
data class Folder(val id: String, val name: String, val gameIds: List<String>)

data class Platform(
    val id: String,
    val menuLabel: String,
    /** The proportions (height / width) of the platform's tile image; its name is drawn as text on a tile of this shape. */
    val tileAspect: Float,
)

/**
 * The observable game library. The games themselves come from ROM scans ([setGames]); the user's
 * manual edits (favorites, titles, covers) are layered on top and persisted through [store], keyed
 * by game id so they survive rescans.
 */
class Library(
    val platforms: List<Platform>,
    private val store: LibraryStore,
) {
    private var gamesByPlatform by mutableStateOf<Map<String, List<GameEntry>>>(emptyMap())
    private var gamesById by mutableStateOf<Map<String, GameEntry>>(emptyMap())

    private val favoriteIds = mutableStateListOf<String>()
    private val titleOverrides = mutableStateMapOf<String, String>()
    private val coverOverrides = mutableStateMapOf<String, String>()
    private val emulatorOverrides = mutableStateMapOf<String, String>()
    private val platformIcons = mutableStateMapOf<String, String>()
    private val platformTitles = mutableStateMapOf<String, String>()
    private val platformEmulators = mutableStateMapOf<String, String>()
    private val xmbCoverOverrides = mutableStateMapOf<String, String>()
    private val removedIds = mutableStateListOf<String>()
    private val playTimes = mutableStateMapOf<String, Long>()
    private val lastPlayed = mutableStateMapOf<String, Long>()
    val folders = mutableStateListOf<Folder>()

    init {
        // Edits are loaded unfiltered: the games they refer to may not have been scanned yet.
        favoriteIds.addAll(store.loadFavorites().orEmpty())
        titleOverrides.putAll(store.loadTitles())
        coverOverrides.putAll(store.loadStringsWithPrefix(COVER_PREFIX))
        emulatorOverrides.putAll(store.loadStringsWithPrefix(EMULATOR_PREFIX))
        platformIcons.putAll(store.loadStringsWithPrefix(PLATFORM_ICON_PREFIX))
        platformTitles.putAll(store.loadStringsWithPrefix(PLATFORM_TITLE_PREFIX))
        platformEmulators.putAll(store.loadStringsWithPrefix(PLATFORM_EMULATOR_PREFIX))
        xmbCoverOverrides.putAll(store.loadStringsWithPrefix(XMB_COVER_PREFIX))
        removedIds.addAll(store.loadList(KEY_REMOVED))
        store.loadStringsWithPrefix(PLAY_PREFIX).forEach { (id, millis) -> millis.toLongOrNull()?.let { playTimes[id] = it } }
        for (id in store.loadList(KEY_FOLDERS)) {
            folders.add(Folder(id, store.loadString(FOLDER_NAME_PREFIX + id) ?: continue, store.loadList(FOLDER_GAMES_PREFIX + id)))
        }
        store.loadStringsWithPrefix(LAST_PLAYED_PREFIX).forEach { (id, time) -> time.toLongOrNull()?.let { lastPlayed[id] = it } }
    }

    /** Replaces the catalogue with the result of a ROM scan. */
    fun setGames(games: List<GameEntry>) {
        gamesById = games.associateBy { it.id }
        gamesByPlatform = games.groupBy { it.platformId }
    }

    /** Favorites in the user's order. */
    val favorites: List<GameEntry>
        get() = favoriteIds.filter { it !in removedIds }.mapNotNull { id -> gamesById[id]?.let(::resolve) }

    /** The games launched most recently (newest first) that are still in the library and on one of [platformIds]. */
    fun recentGames(platformIds: Set<String>, limit: Int): List<GameEntry> =
        gamesById.values
            .filter { it.id !in removedIds && it.platformId in platformIds && lastPlayedMillis(it.id) > 0L }
            .sortedByDescending { lastPlayedMillis(it.id) }
            .take(limit)
            .map(::resolve)

    /** The games in [folder], in the folder's order (ones that left the library are skipped). */
    fun folderGames(folderId: String, platformIds: Set<String>): List<GameEntry> =
        folders.firstOrNull { it.id == folderId }?.gameIds.orEmpty()
            .mapNotNull { id -> gamesById[id]?.takeIf { it.id !in removedIds && it.platformId in platformIds }?.let(::resolve) }

    fun createFolder(name: String): Folder {
        val folder = Folder("f" + System.currentTimeMillis().toString(36), name, emptyList())
        folders.add(folder)
        saveFolders()
        saveFolder(folder)
        return folder
    }

    fun deleteFolder(id: String) {
        if (folders.removeAll { it.id == id }) {
            store.saveString(FOLDER_NAME_PREFIX + id, null)
            store.saveList(FOLDER_GAMES_PREFIX + id, emptyList())
            saveFolders()
        }
    }

    /** Puts [gameId] in the folder (or takes it out again if it is already there); returns true if it is in it now. */
    fun toggleInFolder(folderId: String, gameId: String): Boolean {
        val i = folders.indexOfFirst { it.id == folderId }
        if (i < 0) return false
        val folder = folders[i]
        val now = gameId !in folder.gameIds
        val updated = folder.copy(gameIds = if (now) folder.gameIds + gameId else folder.gameIds - gameId)
        folders[i] = updated
        saveFolder(updated)
        return now
    }

    /** Moves a game within a folder into the place of [targetId]. */
    fun moveInFolder(folderId: String, id: String, targetId: String) {
        val i = folders.indexOfFirst { it.id == folderId }
        if (i < 0) return
        val ids = folders[i].gameIds.toMutableList()
        val from = ids.indexOf(id)
        val to = ids.indexOf(targetId)
        if (from < 0 || to < 0 || from == to) return
        ids.removeAt(from)
        ids.add(to, id)
        folders[i] = folders[i].copy(gameIds = ids)
        saveFolder(folders[i])
    }

    private fun saveFolders() = store.saveList(KEY_FOLDERS, folders.map { it.id })

    private fun saveFolder(folder: Folder) {
        store.saveString(FOLDER_NAME_PREFIX + folder.id, folder.name)
        store.saveList(FOLDER_GAMES_PREFIX + folder.id, folder.gameIds)
    }

    fun playTimeMillis(id: String): Long = playTimes[id] ?: 0L

    /** When the game was last launched (epoch millis), or 0 if never. */
    fun lastPlayedMillis(id: String): Long = lastPlayed[id] ?: 0L

    fun markPlayed(id: String) {
        val now = System.currentTimeMillis()
        lastPlayed[id] = now
        store.saveString(LAST_PLAYED_PREFIX + id, now.toString())
    }

    fun addPlayTime(id: String, millis: Long) {
        val total = playTimeMillis(id) + millis
        playTimes[id] = total
        store.saveString(PLAY_PREFIX + id, total.toString())
    }

    fun gamesFor(platform: Platform): List<GameEntry> =
        gamesByPlatform[platform.id].orEmpty().filter { it.id !in removedIds && !it.favoritesOnly }.map(::resolve)

    /** The games the user took out of the library, by title (for the menu that brings them back). */
    fun removedGames(): List<GameEntry> =
        gamesByPlatform.values.flatten()
            .filter { it.id in removedIds && !it.favoritesOnly }
            .map(::resolve)
            .sortedBy { it.title.lowercase() }

    /** The ids of every scanned game, taken out of the library or not. */
    fun scannedGameIds(): Set<String> =
        gamesByPlatform.values.flatMapTo(HashSet()) { games -> games.filter { !it.favoritesOnly }.map { it.id } }

    /** Set when a manual rescan finishes: how many games it found that were not scanned before (the UI toasts it). */
    var rescanAdded by mutableIntStateOf(0)
        private set
    var rescanStamp by mutableIntStateOf(0)
        private set

    fun reportRescan(added: Int) {
        rescanAdded = added
        rescanStamp++
    }

    /** True if the user removed [id] from the library. */
    fun isRemoved(id: String): Boolean = id in removedIds

    /** True if [id] is in the favorites list (even before its game has been scanned in). */
    fun isFavoriteId(id: String): Boolean = id in favoriteIds

    fun toggleFavorite(id: String) {
        if (!favoriteIds.remove(id)) favoriteIds.add(id)
        store.saveFavorites(favoriteIds)
    }

    /** Moves a favorite into the place of [targetId], shifting the ones in between. */
    fun moveFavorite(id: String, targetId: String) {
        val from = favoriteIds.indexOf(id)
        val to = favoriteIds.indexOf(targetId)
        if (from < 0 || to < 0 || from == to) return
        favoriteIds.removeAt(from)
        favoriteIds.add(to, id)
        store.saveFavorites(favoriteIds)
    }

    fun setTitle(id: String, title: String) {
        titleOverrides[id] = title
        store.saveTitle(id, title)
    }

    fun setCover(id: String, uri: String) {
        coverOverrides[id] = uri
        store.saveString(COVER_PREFIX + id, uri)
    }

    /** Chooses the emulator app [packageName] for one game (null goes back to the platform's usual emulator). */
    fun setCustomEmulator(id: String, packageName: String?) {
        if (packageName == null) emulatorOverrides.remove(id) else emulatorOverrides[id] = packageName
        store.saveString(EMULATOR_PREFIX + id, packageName)
    }

    fun customEmulatorOf(id: String): String? = emulatorOverrides[id]

    /** The image the user picked for a platform's tile on All Games, or null for the built-in logo. */
    fun platformIcon(platform: Platform): String? = platformIcons[platform.id]

    /** The title the user gave a platform: its tile then shows that text instead of the built-in logo (null for the logo). */
    fun platformTitle(platform: Platform): String? = platformTitles[platform.id]

    fun setPlatformTitle(platformId: String, title: String?) {
        if (title == null) platformTitles.remove(platformId) else platformTitles[platformId] = title
        store.saveString(PLATFORM_TITLE_PREFIX + platformId, title)
    }

    /** The emulator app the user chose for a whole platform, or null for the usual one. */
    fun platformEmulator(platform: Platform): String? = platformEmulators[platform.id]

    fun setPlatformEmulator(platformId: String, packageName: String?) {
        if (packageName == null) platformEmulators.remove(platformId) else platformEmulators[platformId] = packageName
        store.saveString(PLATFORM_EMULATOR_PREFIX + platformId, packageName)
    }

    fun setPlatformIcon(platformId: String, uri: String?) {
        if (uri == null) platformIcons.remove(platformId) else platformIcons[platformId] = uri
        store.saveString(PLATFORM_ICON_PREFIX + platformId, uri)
    }

    /** The 16:9 cover shown on the All Games (XMB) page; separate from the Favorites cover. */
    fun setXmbCover(id: String, uri: String) {
        xmbCoverOverrides[id] = uri
        store.saveString(XMB_COVER_PREFIX + id, uri)
    }

    /** Takes a game out of the library (and so out of Favorites); its saved edits are kept. */
    fun removeGame(id: String) {
        if (id in removedIds) return
        removedIds.add(id)
        store.saveList(KEY_REMOVED, removedIds)
    }

    /** Brings back a game taken out of the library (for instance when it is added again). */
    fun restoreGame(id: String) {
        if (removedIds.remove(id)) store.saveList(KEY_REMOVED, removedIds)
    }

    private fun resolve(game: GameEntry): GameEntry = game.copy(
        title = titleOverrides[game.id] ?: game.title,
        isFavorite = game.id in favoriteIds,
        coverUri = coverOverrides[game.id] ?: game.coverUri,
        xmbCoverUri = xmbCoverOverrides[game.id],
        customEmulator = emulatorOverrides[game.id],
        platformEmulator = platformEmulators[game.platformId],
    )

    private companion object {
        const val COVER_PREFIX = "cover."
        const val XMB_COVER_PREFIX = "xmbcover."
        const val EMULATOR_PREFIX = "emulator."
        const val PLATFORM_ICON_PREFIX = "platformicon."
        const val PLATFORM_TITLE_PREFIX = "platformtitle."
        const val PLATFORM_EMULATOR_PREFIX = "platformemulator."
        const val KEY_REMOVED = "removedGames"
        const val PLAY_PREFIX = "playtime."
        const val LAST_PLAYED_PREFIX = "lastplayed."
        const val KEY_FOLDERS = "folders"
        const val FOLDER_NAME_PREFIX = "folder.name."
        const val FOLDER_GAMES_PREFIX = "folder.games."
    }
}
