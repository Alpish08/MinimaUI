package com.minimaui.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import com.minimaui.app.model.AppEntry
import com.minimaui.app.model.AppFolder
import com.minimaui.app.model.AppLibrary
import com.minimaui.app.model.AppSlot
import com.minimaui.app.model.AppTile
import com.minimaui.app.model.AppTileApp
import com.minimaui.app.model.AppsPageSize
import com.minimaui.app.ui.components.ControlHint
import com.minimaui.app.ui.favorites.BentoLayouts
import com.minimaui.app.ui.favorites.Direction
import com.minimaui.app.ui.favorites.findNeighbor

/** What the Apps page asks of the rest of the home screen. */
interface AppsNavActions {
    fun launch(app: AppEntry)
    /** Opens the app picker; the apps chosen go into [at] and the free slots after it. */
    fun pickApps(at: AppSlot)
    fun toast(text: String, error: Boolean = false)
    /** The launch press (the focused icon dips). */
    fun press()
    fun haptic()
    fun pageSound()
    fun openSound()
}

/**
 * The Apps page's cursor: over the slots of the pages (rows of 3, 4 and 3, pages stacked vertically) or, with a folder
 * open, over the folder's ten slots. Apps and folders sit freely in slots, gaps allowed.
 *
 * In Edit Mode, A picks up what is focused and carries it: the cursor moves it around while everything else stays put,
 * and the thing in the slot under it is shown where the carried one came from, so the page shows the swap before it
 * happens. A puts it down (a swap, or a move into an empty slot), B puts it back. Apps go into folders from the Start menu.
 */
class AppsNav(
    private val library: AppLibrary,
    private val editMode: () -> Boolean,
    private val actions: AppsNavActions,
) {
    var page by mutableIntStateOf(0)
        private set
    var slot by mutableIntStateOf(0)
        private set
    var openFolderId by mutableStateOf<String?>(null)
        private set
    var folderSlot by mutableIntStateOf(0)
        private set
    /** Where the carried app or folder came from (null: nothing is carried). */
    var carryFrom by mutableStateOf<AppSlot?>(null)
        private set

    val carrying: Boolean get() = carryFrom != null

    init {
        clamp()
    }

    val openFolder: AppFolder? get() = openFolderId?.let(library::folder)

    /** The slot under the cursor. */
    val cursor: AppSlot
        get() = openFolderId?.let { AppSlot(it, folderSlot) } ?: AppSlot(null, page * AppsPageSize + slot)

    /** The app under the cursor (not while carrying). */
    val currentApp: AppEntry? get() = if (carrying) null else (library.tileAt(cursor) as? AppTileApp)?.app

    /** The folder bubble under the cursor (not while carrying, and not inside a folder). */
    val currentFolder: AppFolder? get() = if (carrying || openFolderId != null) null else library.tileAt(cursor) as? AppFolder

    val pageCount: Int
        get() {
            val used = (library.tiles.size + AppsPageSize - 1) / AppsPageSize
            // In Edit Mode there is always a blank page after the last one, to carry things to or fill with new apps.
            return maxOf(1, used + if (editMode() && library.tiles.isNotEmpty()) 1 else 0)
        }

    /** Changes whenever the cursor or the carried thing moves (for the navigation sound). */
    val signature: Int get() = listOf(page, slot, openFolderId, folderSlot, carryFrom).hashCode()

    val canStepBack: Boolean get() = carrying || openFolderId != null

    /** The ten tiles of page [p] as shown: with something carried, it shows under the cursor and the swap is previewed. */
    fun shownOnPage(p: Int): List<AppTile?> = List(AppsPageSize) { shownAt(AppSlot(null, p * AppsPageSize + it)) }

    /** The folder's ten tiles as shown (with the carry preview). */
    fun shownInFolder(): List<AppTile?> = openFolderId?.let { id -> List(AppsPageSize) { shownAt(AppSlot(id, it)) } } ?: emptyList()

    private fun shownAt(at: AppSlot): AppTile? {
        val from = carryFrom ?: return library.tileAt(at)
        val to = cursor
        if (from.folderId != to.folderId) return library.tileAt(at)
        return when (at) {
            to -> library.tileAt(from)
            from -> library.tileAt(to)
            else -> library.tileAt(at)
        }
    }

    /** The hints for the bottom bar, or null to leave them to the home screen (outside Edit Mode, on the page). */
    fun hints(): List<ControlHint>? {
        val inFolder = openFolderId != null
        val back = ControlHint("B", if (inFolder) "Close" else "Back")
        if (!editMode()) return if (inFolder) listOf(ControlHint("A", "Launch"), back) else null
        if (carrying) return listOf(ControlHint("A", "Place"), ControlHint("B", "Cancel"))
        return when (library.tileAt(cursor)) {
            is AppFolder -> listOf(ControlHint("X", "Delete"), ControlHint("Y", "Open"), ControlHint("A", "Move"), back)
            is AppTileApp -> listOf(ControlHint("X", if (inFolder) "Take Out" else "Remove"), ControlHint("A", "Move"), back)
            null -> listOf(ControlHint("A", "Add"), back)
        }
    }

    /** Handles a key on the Apps page; false leaves it to the home screen (Start, Select, X outside Edit Mode, B with nothing to undo). */
    fun onKey(key: Key, isRepeat: Boolean): Boolean {
        when (key) {
            Key.DirectionUp -> navigate(Direction.Up)
            Key.DirectionDown -> navigate(Direction.Down)
            Key.DirectionLeft -> navigate(Direction.Left)
            Key.DirectionRight -> navigate(Direction.Right)
            Key.ButtonL1 -> changePage(-1)
            Key.ButtonR1 -> changePage(1)
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> if (!isRepeat) confirm()
            Key.ButtonY -> if (!isRepeat) onY()
            Key.ButtonX -> {
                if (!editMode()) return openFolderId != null // in a folder X does nothing; on the page it switches to the games
                if (!isRepeat && !carrying) remove()
            }
            Key.ButtonB, Key.Escape, Key.Back -> {
                if (!canStepBack) return false
                if (!isRepeat) back()
            }
            else -> return false
        }
        return true
    }

    fun back() {
        when {
            carrying -> carryFrom = null
            openFolderId != null -> closeFolder()
        }
    }

    /** After apps or folders were removed elsewhere (or Edit Mode ended): keep the cursor on something that exists. */
    fun clamp() {
        if (!editMode()) carryFrom = null
        val folderId = openFolderId
        if (folderId != null && library.folder(folderId) == null) openFolderId = null
        if (openFolderId != null) {
            if (!focusable(folderSlot)) folderSlot = nearestFocusable(folderSlot) ?: 0
            return
        }
        page = page.coerceIn(0, pageCount - 1)
        if (!focusable(slot)) {
            nearestFocusable(slot)?.let { slot = it } ?: run {
                // Nothing left on this page: back to the last page that has something.
                val last = (pageCount - 1 downTo 0).firstOrNull { p -> (0 until AppsPageSize).any { library.tileAt(AppSlot(null, p * AppsPageSize + it)) != null } }
                if (last != null && last != page) {
                    page = last
                    slot = nearestFocusable(slot) ?: 0
                }
            }
        }
    }

    fun reset() {
        page = 0
        slot = 0
        openFolderId = null
        carryFrom = null
        clamp()
    }

    // ---- Touch.

    /** A tap on [s] (a slot of the page, or of the open folder). */
    fun tap(s: Int) {
        if (editMode()) {
            focusSlot(s)
            if (!carrying && library.tileAt(cursor) == null) actions.pickApps(cursor)
            return
        }
        if (library.tileAt(slotHere(s)) == null) return
        focusSlot(s)
        open()
    }

    /** A tap outside the open folder's bubble closes it. */
    fun tapOutside() {
        if (openFolderId != null && !carrying) closeFolder()
    }

    fun swipe(delta: Int) {
        if (openFolderId == null) changePage(delta)
    }

    /** Edit Mode, touch: a long press on [s] picks up what is there. */
    fun touchHold(s: Int) {
        if (!editMode() || library.tileAt(slotHere(s)) == null) return
        focusSlot(s)
        carryFrom = cursor
        actions.haptic()
    }

    /** Edit Mode, touch: the carried thing is over [s]. */
    fun touchDrag(s: Int) {
        if (carrying) focusSlot(s)
    }

    fun touchEdge(delta: Int) {
        if (carrying && openFolderId == null) changePage(delta)
    }

    fun touchRelease() {
        if (carrying) drop()
    }

    // ---- Internals.

    private fun slotHere(s: Int) = openFolderId?.let { AppSlot(it, s) } ?: AppSlot(null, page * AppsPageSize + s)

    private fun focusSlot(s: Int) {
        if (openFolderId != null) folderSlot = s else slot = s
    }

    /** Edit Mode and carrying reach every slot; otherwise only the ones with something in them. */
    private fun focusable(s: Int, p: Int = page): Boolean =
        editMode() || carrying || library.tileAt(openFolderId?.let { AppSlot(it, s) } ?: AppSlot(null, p * AppsPageSize + s)) != null

    private fun nearestFocusable(from: Int, p: Int = page): Int? {
        val layout = BentoLayouts.Apps
        val a = layout[from.coerceIn(0, layout.lastIndex)]
        return layout.indices.filter { focusable(it, p) }.minByOrNull {
            val b = layout[it]
            val dx = b.centerX - a.centerX
            val dy = b.centerY - a.centerY
            dx * dx + dy * dy
        }
    }

    private fun navigate(direction: Direction) {
        val layout = BentoLayouts.Apps
        if (openFolderId != null) {
            findNeighbor(layout, { focusable(it) }, folderSlot, direction)?.let { folderSlot = it }
            return
        }
        val neighbor = findNeighbor(layout, { focusable(it) }, slot, direction)
        if (neighbor != null) {
            slot = neighbor
            return
        }
        // Off the top or bottom row: the page before or after (rows of 3, 4 and 3, so the slot lines up across).
        val lastRow = AppsPageSize - FirstRowSize
        when {
            direction == Direction.Up && slot < FirstRowSize && page > 0 -> goToPage(page - 1, slot + lastRow)
            direction == Direction.Down && slot >= lastRow && page < pageCount - 1 -> goToPage(page + 1, slot - lastRow)
        }
    }

    private fun goToPage(target: Int, preferred: Int) {
        val s = if (focusable(preferred, target)) preferred else nearestFocusable(preferred, target) ?: return
        page = target
        slot = s
    }

    private fun changePage(delta: Int) {
        if (openFolderId != null) return
        val target = (page + delta).coerceIn(0, pageCount - 1)
        if (target == page) return
        val s = if (focusable(slot, target)) slot else nearestFocusable(slot, target) ?: return
        actions.haptic()
        actions.pageSound()
        page = target
        slot = s
    }

    private fun confirm() {
        if (carrying) {
            drop()
            return
        }
        if (editMode()) {
            if (library.tileAt(cursor) != null) {
                carryFrom = cursor
                actions.haptic()
            } else {
                actions.pickApps(cursor)
            }
            return
        }
        open()
    }

    /** Launches the focused app, or opens the focused folder. */
    private fun open() {
        when (val tile = library.tileAt(cursor)) {
            is AppTileApp -> {
                actions.press()
                actions.launch(tile.app)
            }
            is AppFolder -> openFolder(tile.id)
            null -> Unit
        }
    }

    /** Y in Edit Mode opens the focused folder (A there picks it up). */
    private fun onY() {
        if (editMode() && !carrying) (library.tileAt(cursor) as? AppFolder)?.let { openFolder(it.id) }
    }

    private fun drop() {
        val from = carryFrom ?: return
        carryFrom = null
        library.swap(from, cursor)
    }

    private fun remove() {
        when (val tile = library.tileAt(cursor)) {
            is AppFolder -> {
                library.deleteFolder(tile.id)
                actions.toast("Deleted \"${tile.name}\"; its apps are back on the page")
            }
            is AppTileApp ->
                if (openFolderId != null) {
                    library.takeOutOfFolder(tile.app.id)
                    actions.toast("Moved to the Apps page")
                } else {
                    library.hide(tile.app.id)
                }
            null -> return
        }
        clamp()
    }

    private fun openFolder(id: String) {
        actions.openSound()
        openFolderId = id
        folderSlot = 0
        if (!focusable(0)) folderSlot = nearestFocusable(0) ?: 0
    }

    private fun closeFolder() {
        openFolderId = null
        clamp()
    }

    companion object {
        private const val FirstRowSize = 3
    }
}
