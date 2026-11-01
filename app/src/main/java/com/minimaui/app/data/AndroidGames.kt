package com.minimaui.app.data

import com.minimaui.app.model.GameEntry

/** Installed Android apps the user added to the Android platform. */
class AndroidGames(private val store: LibraryStore) {
    private class Added(val packageName: String, val label: String, val icon: String?, val favoritesOnly: Boolean = false)

    fun add(packageName: String, label: String, icon: String?) = save(load().filterNot { it.packageName == packageName } + Added(packageName, label, icon))

    /**
     * Registers an app from the Apps page so it can be a favorite, without listing it under Android. A game that is
     * already there stays as it is, unless the user had removed it from the library ([wasRemoved]): then it starts over.
     */
    fun addFavoritesOnly(packageName: String, label: String, icon: String?, wasRemoved: Boolean = false) {
        val all = load()
        if (all.none { it.packageName == packageName } || wasRemoved) {
            save(all.filterNot { it.packageName == packageName } + Added(packageName, label, icon, favoritesOnly = true))
        }
    }

    private fun save(all: List<Added>) =
        store.saveList(KEY, all.map { "${it.packageName}\t${it.label}\t${it.icon.orEmpty()}" + if (it.favoritesOnly) "\t$FAVORITES_ONLY" else "" })

    fun games(): List<GameEntry> = load().map {
        GameEntry(
            id = idFor(it.packageName),
            platformId = "android",
            title = it.label,
            coverUri = it.icon?.ifEmpty { null },
            shortcutPackage = it.packageName,
            favoritesOnly = it.favoritesOnly,
        )
    }

    private fun load(): List<Added> = store.loadList(KEY).mapNotNull { line ->
        val parts = line.split('\t', limit = 4)
        if (parts.size >= 3) Added(parts[0], parts[1], parts[2], favoritesOnly = parts.getOrNull(3) == FAVORITES_ONLY) else null
    }

    companion object {
        private const val KEY = "androidGames"
        private const val FAVORITES_ONLY = "favorite"
        fun idFor(packageName: String) = "android:$packageName"
    }
}
