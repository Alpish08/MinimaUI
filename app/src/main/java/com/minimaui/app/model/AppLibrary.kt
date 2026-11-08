package com.minimaui.app.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.minimaui.app.data.LibraryStore

/** One thing on the Apps page: an app, or a folder of apps (a bubble). */
sealed interface AppTile {
    /** Stable across moves, so the page can glide a tile to its new slot. */
    val key: String
}

data class AppTileApp(val app: AppEntry) : AppTile {
    override val key: String get() = app.id
}

/** A folder: a [name] and [AppFolderSize] slots of apps (null = empty), placed freely like the page's own. */
data class AppFolder(val id: String, val name: String, val apps: List<AppEntry?>) : AppTile {
    override val key: String get() = FolderKeyPrefix + id
    val count: Int get() = apps.count { it != null }
}

/** Slots on one Apps page (rows of 3, 4 and 3), and in one folder. */
const val AppsPageSize = 10
const val AppFolderSize = 10

private const val FolderKeyPrefix = "folder:"

/** Where a slot is: on the Apps page ([folderId] null; [index] = page * [AppsPageSize] + slot) or in a folder. */
data class AppSlot(val folderId: String?, val index: Int)

/**
 * The Apps page contents: installed apps the user added (id = package name), with manual titles and hidden apps, placed
 * freely in slots (gaps allowed) on the pages and in folders, persisted through [store].
 */
class AppLibrary(private val store: LibraryStore) {
    private val appsById = mutableStateMapOf<String, AppEntry>()
    private val hidden = mutableStateListOf<String>()
    private val titleOverrides = mutableStateMapOf<String, String>()

    // The page slots: an app id, a folder key (FolderKeyPrefix + id), or null for an empty slot.
    private val slots = mutableStateListOf<String?>()
    private val folderNames = mutableStateMapOf<String, String>()
    private val folderSlots = mutableStateMapOf<String, List<String?>>()

    /** Where the next app added from the picker goes (an empty slot the user chose), then the empty slots after it. */
    var addAt: AppSlot? = null

    init {
        store.loadList(KEY_ADDED).forEach { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size == 3) appsById[parts[0]] = AppEntry(parts[0], parts[1], parts[2].ifEmpty { null })
        }
        hidden.addAll(store.loadList(KEY_HIDDEN).filter { it in appsById })
        titleOverrides.putAll(store.loadStringsWithPrefix(TITLE_PREFIX).filterKeys { it in appsById })
        for (id in store.loadList(KEY_FOLDERS)) {
            val name = store.loadString(FOLDER_NAME_PREFIX + id) ?: continue
            folderNames[id] = name
            val saved = store.loadList(FOLDER_SLOTS_PREFIX + id).map { s -> s.takeIf { it != EMPTY && isShownApp(it) } }
            folderSlots[id] = List(AppFolderSize) { saved.getOrNull(it) }
        }
        if (store.hasList(KEY_SLOTS)) {
            slots.addAll(store.loadList(KEY_SLOTS).map { s ->
                s.takeIf { it != EMPTY && (isShownApp(it) || folderOf(it) in folderNames) }
            })
        } else {
            // Before free placement the page was an ordered list: it becomes the first slots, in the same order.
            slots.addAll(store.loadList(KEY_ORDER).filter(::isShownApp))
        }
        // Each app and folder in one place only; any shown app placed nowhere (an older save) goes to the first free slot.
        val placed = HashSet<String>()
        for (i in slots.indices) slots[i]?.let { if (!placed.add(it)) slots[i] = null }
        for ((id, list) in folderSlots.toMap()) folderSlots[id] = list.map { s -> s?.takeIf { placed.add(it) } }
        folderNames.keys.filter { (FolderKeyPrefix + it) !in placed }.forEach { place(FolderKeyPrefix + it) }
        appsById.keys.filter { isShownApp(it) && it !in placed }.forEach { place(it) }
        trim()
    }

    /** The page slots, page after page (null = empty), up to the last thing placed. */
    val tiles: List<AppTile?>
        get() = slots.map(::tileFor)

    /** Every app on the page and in folders, in page order (a folder's apps where the folder is). */
    val apps: List<AppEntry>
        get() = slots.flatMap { id ->
            when (val t = tileFor(id)) {
                is AppTileApp -> listOf(t.app)
                is AppFolder -> t.apps.filterNotNull()
                null -> emptyList()
            }
        }

    val folders: List<AppFolder>
        get() = folderNames.keys.mapNotNull(::folder).sortedBy { it.name.lowercase() }

    fun folder(id: String): AppFolder? {
        val name = folderNames[id] ?: return null
        return AppFolder(id, name, folderSlots[id].orEmpty().map { it?.let(::entry) })
    }

    fun tileAt(slot: AppSlot): AppTile? =
        if (slot.folderId == null) tileFor(slots.getOrNull(slot.index))
        else folderSlots[slot.folderId]?.getOrNull(slot.index)?.let(::entry)?.let(::AppTileApp)

    fun contains(id: String) = id in appsById && id !in hidden

    /** The folder [appId] is in, if any. */
    fun folderOfApp(appId: String): String? = folderSlots.entries.firstOrNull { appId in it.value }?.key

    /** Adds an installed app (or brings back one that was hidden), into [addAt] if that is still free, else the first free slot. */
    fun add(packageName: String, label: String, icon: String?) {
        appsById[packageName] = AppEntry(packageName, label, icon)
        if (hidden.remove(packageName)) store.saveList(KEY_HIDDEN, hidden)
        if (locate(packageName) == null) {
            val target = addAt?.takeIf { isFree(it) } ?: addAt?.let { nextFree(it) }
            if (target != null) {
                put(target, packageName)
                addAt = nextFree(target)
            } else {
                place(packageName)
            }
        }
        store.saveList(KEY_ADDED, appsById.values.map { "${it.id}\t${it.title}\t${it.coverUri.orEmpty()}" })
        save()
    }

    /** Swaps what is in [a] and [b] (either may be empty), on the page or in folders. A folder never goes into a folder. */
    fun swap(a: AppSlot, b: AppSlot) {
        if (a == b) return
        val ida = idAt(a)
        val idb = idAt(b)
        if ((b.folderId != null && ida.isFolderKey()) || (a.folderId != null && idb.isFolderKey())) return
        put(a, idb)
        put(b, ida)
        save()
    }

    /** Makes a folder named [name] in slot [at] of the page, holding the apps [appIds] (taken from wherever they were). */
    fun createFolder(name: String, at: Int, appIds: List<String>): String {
        val id = "a" + System.currentTimeMillis().toString(36)
        appIds.forEach { appId -> locate(appId)?.let { put(it, null) } }
        folderNames[id] = name
        folderSlots[id] = List(AppFolderSize) { appIds.getOrNull(it) }
        val key = FolderKeyPrefix + id
        if (at >= 0 && idAt(AppSlot(null, at)) == null) put(AppSlot(null, at), key) else place(key)
        store.saveString(FOLDER_NAME_PREFIX + id, name)
        save()
        return id
    }

    /** Makes a folder named [name] holding [appId], in the app's place on the page (or the first free slot). */
    fun createFolderWith(name: String, appId: String): String =
        createFolder(name, locate(appId)?.takeIf { it.folderId == null }?.index ?: -1, listOf(appId))

    /** Puts [appId] into the first free slot of [folderId]; false if the folder is full. */
    fun addToFolder(folderId: String, appId: String): Boolean {
        val list = folderSlots[folderId] ?: return false
        val free = list.indexOfFirst { it == null }.takeIf { it >= 0 } ?: return false
        if (folderOfApp(appId) == folderId) return true
        locate(appId)?.let { put(it, null) }
        put(AppSlot(folderId, free), appId)
        save()
        return true
    }

    /** Takes [appId] out of its folder, to the first free slot of the page. */
    fun takeOutOfFolder(appId: String) {
        val from = locate(appId)?.takeIf { it.folderId != null } ?: return
        put(from, null)
        place(appId)
        save()
    }

    fun renameFolder(id: String, name: String) {
        if (id !in folderNames) return
        folderNames[id] = name
        store.saveString(FOLDER_NAME_PREFIX + id, name)
    }

    /** Deletes the folder; its apps go to the free slots of the page (none is lost). */
    fun deleteFolder(id: String) {
        val list = folderSlots.remove(id) ?: return
        folderNames.remove(id)
        locate(FolderKeyPrefix + id)?.let { put(it, null) }
        list.filterNotNull().forEach(::place)
        store.saveString(FOLDER_NAME_PREFIX + id, null)
        store.saveList(FOLDER_SLOTS_PREFIX + id, emptyList())
        save()
    }

    fun setTitle(id: String, title: String) {
        titleOverrides[id] = title
        store.saveString(TITLE_PREFIX + id, title)
    }

    /** Takes the app off the Apps page (and out of its folder); adding it again brings it back. */
    fun hide(id: String) {
        if (id in hidden) return
        hidden.add(id)
        locate(id)?.let { put(it, null) }
        store.saveList(KEY_HIDDEN, hidden)
        save()
    }

    /** The folder id behind a page slot's key, if it is a folder. */
    fun folderIdOf(tile: AppTile?): String? = (tile as? AppFolder)?.id

    private fun tileFor(id: String?): AppTile? = when {
        id == null -> null
        id.isFolderKey() -> folder(folderOf(id))
        else -> entry(id)?.let(::AppTileApp)
    }

    private fun entry(id: String): AppEntry? = appsById[id]?.takeIf { id !in hidden }?.let(::resolve)

    private fun isShownApp(id: String) = id in appsById && id !in hidden

    private fun String?.isFolderKey() = this != null && startsWith(FolderKeyPrefix)

    private fun folderOf(key: String) = key.removePrefix(FolderKeyPrefix)

    private fun idAt(slot: AppSlot): String? =
        if (slot.folderId == null) slots.getOrNull(slot.index) else folderSlots[slot.folderId]?.getOrNull(slot.index)

    private fun isFree(slot: AppSlot): Boolean =
        idAt(slot) == null && (slot.folderId == null || (slot.folderId in folderSlots && slot.index in 0 until AppFolderSize))

    /** The first free slot after [from] (on the page: any later free slot or a new one; in a folder: null when full). */
    private fun nextFree(from: AppSlot): AppSlot? =
        if (from.folderId == null) {
            AppSlot(null, (from.index + 1 until slots.size).firstOrNull { slots[it] == null } ?: maxOf(slots.size, from.index + 1))
        } else {
            folderSlots[from.folderId]?.let { list -> (from.index + 1 until AppFolderSize).firstOrNull { list[it] == null } }
                ?.let { AppSlot(from.folderId, it) }
        }

    private fun locate(id: String): AppSlot? {
        slots.indexOf(id).takeIf { it >= 0 }?.let { return AppSlot(null, it) }
        for ((folderId, list) in folderSlots) list.indexOf(id).takeIf { it >= 0 }?.let { return AppSlot(folderId, it) }
        return null
    }

    private fun put(slot: AppSlot, id: String?) {
        if (slot.folderId == null) {
            while (slots.size <= slot.index) slots.add(null)
            slots[slot.index] = id
        } else {
            val list = folderSlots[slot.folderId] ?: return
            folderSlots[slot.folderId] = list.toMutableList().also { it[slot.index] = id }
        }
    }

    /** Into the page's first free slot. */
    private fun place(id: String) {
        val free = slots.indexOf(null)
        if (free >= 0) slots[free] = id else slots.add(id)
    }

    private fun trim() {
        while (slots.isNotEmpty() && slots.last() == null) slots.removeAt(slots.lastIndex)
    }

    private fun save() {
        trim()
        store.saveList(KEY_SLOTS, slots.map { it ?: EMPTY })
        store.saveList(KEY_FOLDERS, folderNames.keys.toList())
        for ((id, list) in folderSlots) store.saveList(FOLDER_SLOTS_PREFIX + id, list.map { it ?: EMPTY })
        // The plain order too, so a version from before free placement still finds every app.
        store.saveList(KEY_ORDER, apps.map { it.id })
    }

    private fun resolve(app: AppEntry) = app.copy(title = titleOverrides[app.id] ?: app.title)

    private companion object {
        const val KEY_ORDER = "appOrder"
        const val KEY_SLOTS = "appSlots"
        const val KEY_FOLDERS = "appFolders"
        const val FOLDER_NAME_PREFIX = "appFolder.name."
        const val FOLDER_SLOTS_PREFIX = "appFolder.slots."
        const val KEY_HIDDEN = "appsHidden"
        const val KEY_ADDED = "addedApps"
        const val TITLE_PREFIX = "apptitle."
        const val EMPTY = "-"
    }
}
