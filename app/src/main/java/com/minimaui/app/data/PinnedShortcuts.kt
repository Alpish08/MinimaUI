package com.minimaui.app.data

import com.minimaui.app.model.GameEntry

/**
 * Shortcuts other apps (Winlator Cmod, GameHub, Artemis...) pinned to MinimaUI, which acts as the home app.
 * Artemis shortcuts become Windows games, everything else a Steam game. Kept in [LibraryStore] across restarts.
 * An [id] starting with [LEGACY] is an old-style shortcut: the rest is the intent to start, as a URI.
 */
class PinnedShortcuts(private val store: LibraryStore) {
    private class Pinned(val packageName: String, val id: String, val label: String)

    fun add(packageName: String, id: String, label: String) {
        val all = load().filterNot { it.packageName == packageName && it.id == id } + Pinned(packageName, id, label)
        store.saveList(KEY, all.map { "${it.packageName}\t${it.id}\t${it.label}" })
    }

    fun games(): List<GameEntry> = load().map {
        val platform = if (it.packageName.contains("artemis", true) || it.packageName.contains("limelight", true)) "windows" else "steam"
        GameEntry(
            id = "$platform:shortcut:${it.packageName}:${it.id}",
            platformId = platform,
            title = it.label,
            shortcutPackage = it.packageName,
            shortcutId = it.id,
        )
    }

    private fun load(): List<Pinned> = store.loadList(KEY).mapNotNull { line ->
        val parts = line.split('\t', limit = 3)
        if (parts.size == 3) Pinned(parts[0], parts[1], parts[2]) else null
    }

    companion object {
        const val LEGACY = "legacy:"

        private const val KEY = "pinnedShortcuts"
    }
}
