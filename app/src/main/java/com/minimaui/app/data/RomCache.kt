package com.minimaui.app.data

import android.content.Context
import com.minimaui.app.model.GameEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * The result of the last ROM folder scan, kept on disk so launching MinimaUI doesn't walk the folders again (that is
 * slow and drains the battery). It is only rebuilt on a manual rescan or when a ROM folder is added or removed; the
 * folder list it was made for is stored with it, so a cache that doesn't match the current folders is ignored.
 * Kept out of the settings backups on purpose: it is rebuilt data, not a setting.
 */
class RomCache(context: Context) {
    private val prefs = context.getSharedPreferences("romcache", Context.MODE_PRIVATE)

    /** The cached games for exactly these [folders], or null if there is no cache for them. */
    fun load(folders: List<String>): List<GameEntry>? = runCatching {
        val root = JSONObject(prefs.getString(KEY, null) ?: return null)
        val saved = root.getJSONArray("folders").let { a -> List(a.length()) { a.getString(it) } }
        if (saved != folders) return null
        val games = root.getJSONArray("games")
        List(games.length()) {
            val g = games.getJSONObject(it)
            GameEntry(
                id = g.getString("id"),
                platformId = g.getString("platformId"),
                title = g.getString("title"),
                fileUri = g.optString("fileUri").ifEmpty { null },
            )
        }
    }.getOrNull()

    fun save(folders: List<String>, games: List<GameEntry>) {
        val array = JSONArray()
        for (game in games) {
            array.put(
                JSONObject()
                    .put("id", game.id)
                    .put("platformId", game.platformId)
                    .put("title", game.title)
                    .put("fileUri", game.fileUri ?: "")
            )
        }
        val json = JSONObject().put("folders", JSONArray(folders)).put("games", array)
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private companion object {
        const val KEY = "scan"
    }
}
