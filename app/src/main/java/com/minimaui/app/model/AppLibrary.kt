package com.minimaui.app.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.minimaui.app.data.LibraryStore

/**
 * The Apps page contents: installed apps the user added (id = package name), in a user-ordered list with
 * manual titles and hidden apps, persisted through [store].
 */
class AppLibrary(private val store: LibraryStore) {
    private val appsById = mutableStateMapOf<String, AppEntry>()
    private val order = mutableStateListOf<String>()
    private val hidden = mutableStateListOf<String>()
    private val titleOverrides = mutableStateMapOf<String, String>()

    init {
        store.loadList(KEY_ADDED).forEach { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size == 3) appsById[parts[0]] = AppEntry(parts[0], parts[1], parts[2].ifEmpty { null })
        }
        val saved = store.loadList(KEY_ORDER).filter { it in appsById }
        order.addAll(saved + appsById.keys.filter { it !in saved })
        hidden.addAll(store.loadList(KEY_HIDDEN).filter { it in appsById })
        titleOverrides.putAll(store.loadStringsWithPrefix(TITLE_PREFIX).filterKeys { it in appsById })
    }

    val apps: List<AppEntry>
        get() = order.filter { it !in hidden }.mapNotNull { id -> appsById[id]?.let(::resolve) }

    fun contains(id: String) = id in appsById && id !in hidden

    /** Adds an installed app (or brings back one that was hidden). */
    fun add(packageName: String, label: String, icon: String?) {
        appsById[packageName] = AppEntry(packageName, label, icon)
        if (packageName !in order) order.add(packageName)
        if (hidden.remove(packageName)) store.saveList(KEY_HIDDEN, hidden)
        store.saveList(KEY_ORDER, order)
        store.saveList(KEY_ADDED, appsById.values.map { "${it.id}\t${it.title}\t${it.coverUri.orEmpty()}" })
    }

    /** Moves [id] into the place of [targetId], shifting the apps in between. */
    fun move(id: String, targetId: String) {
        val from = order.indexOf(id)
        val to = order.indexOf(targetId)
        if (from < 0 || to < 0 || from == to) return
        order.removeAt(from)
        order.add(to, id)
        store.saveList(KEY_ORDER, order)
    }

    fun setTitle(id: String, title: String) {
        titleOverrides[id] = title
        store.saveString(TITLE_PREFIX + id, title)
    }

    fun hide(id: String) {
        if (id in hidden) return
        hidden.add(id)
        store.saveList(KEY_HIDDEN, hidden)
    }

    private fun resolve(app: AppEntry) = app.copy(title = titleOverrides[app.id] ?: app.title)

    private companion object {
        const val KEY_ORDER = "appOrder"
        const val KEY_HIDDEN = "appsHidden"
        const val KEY_ADDED = "addedApps"
        const val TITLE_PREFIX = "apptitle."
    }
}
