package com.minimaui.app.data

import android.os.Environment
import com.minimaui.app.model.GameEntry
import java.io.File

/**
 * Games from GameHub (com.xiaoji.egggame) that the user added by hand: a title and the game's id (a Steam app id,
 * or GameHub's own "local_..." id for imported games). They sit under Steam and are started through GameHub's deep link.
 * [syncWithFile] keeps the list in MinimaUI/GameID/gamehub_games.txt too, so it survives a reset and can be edited by hand.
 */
class GameHubGames(private val store: LibraryStore) {
    private class Added(val id: String, val title: String)

    fun add(id: String, title: String) {
        val all = load().filterNot { it.id == id } + Added(id, title)
        store.saveList(KEY, all.map { "${it.id}\t${it.title}" })
        writeFile()
    }

    fun games(): List<GameEntry> = load().map {
        GameEntry(
            id = idFor(it.id),
            platformId = "steam",
            title = it.title,
            shortcutPackage = PACKAGE,
            shortcutId = PREFIX + it.id,
        )
    }

    /**
     * MinimaUI/GameID/gamehub_games.txt is the master copy of this list (lines "Title | id"), so it survives a reset
     * and can be edited by hand: when it exists its games replace the saved list, otherwise it is written from the
     * saved list. Does nothing without "All files access".
     */
    fun syncWithFile() {
        if (!Environment.isExternalStorageManager()) return
        runCatching {
            val file = listFile()
            if (!file.isFile) {
                writeFile()
                return
            }
            val games = LinkedHashMap<String, Added>()
            file.readLines().forEach { line ->
                val text = line.trim()
                if (text.isEmpty() || text.startsWith("#") || '|' !in text) return@forEach
                val title = text.substringBeforeLast('|').trim()
                val id = text.substringAfterLast('|').trim()
                if (title.isNotEmpty() && id.isNotEmpty()) games[id] = Added(id, title)
            }
            store.saveList(KEY, games.values.map { "${it.id}\t${it.title}" })
        }
    }

    private fun listFile() = File(Environment.getExternalStorageDirectory(), FILE_PATH)

    private fun writeFile() {
        if (!Environment.isExternalStorageManager()) return
        runCatching {
            val file = listFile()
            file.parentFile?.mkdirs()
            val header = listOf(
                "# MinimaUI: your GameHub games. One per line: Title | id",
                "# id is GameHub's own id for an imported game (local_...), or SteamAppId/GameHubGameId for a Steam download.",
                "# This file is the master copy: MinimaUI reads it on every scan, so edit or delete lines here freely.",
                "",
            )
            file.writeText((header + load().map { "${it.title} | ${it.id}" }).joinToString("\n") + "\n")
        }
    }

    private fun load(): List<Added> = store.loadList(KEY).mapNotNull { line ->
        val parts = line.split('\t', limit = 2)
        if (parts.size == 2) Added(parts[0], parts[1]) else null
    }

    companion object {
        const val PACKAGE = "com.xiaoji.egggame"
        const val PREFIX = "gamehub:"
        private const val KEY = "gamehubGames"
        private const val FILE_PATH = "MinimaUI/GameID/gamehub_games.txt"
        fun idFor(id: String) = "steam:gamehub:$id"
    }
}
