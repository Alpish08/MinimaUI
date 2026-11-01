package com.minimaui.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists the user's manual edits (favorites order and title overrides) across launches. */
class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    init {
        // Older picks were saved as content:// grants; turn them into paths so they outlast a restore or reinstall.
        val editor = prefs.edit()
        for ((key, value) in prefs.all) {
            if (value !is String) continue
            val path = StoredUris.persistent(value)
            if (path != value) editor.putString(key, path)
        }
        editor.apply()
    }

    /** Ordered favorite ids, or null if the user has never changed favorites. */
    fun loadFavorites(): List<String>? =
        prefs.getString(KEY_FAVORITES, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() }

    fun saveFavorites(ids: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, ids.joinToString(SEPARATOR)).apply()
    }

    fun loadTitles(): Map<String, String> =
        prefs.all
            .filterKeys { it.startsWith(TITLE_PREFIX) }
            .mapNotNull { (key, value) -> (value as? String)?.let { key.removePrefix(TITLE_PREFIX) to it } }
            .toMap()

    fun saveTitle(id: String, title: String) {
        prefs.edit().putString(TITLE_PREFIX + id, title).apply()
    }

    fun loadFlag(key: String, default: Boolean): Boolean = prefs.getBoolean(FLAG_PREFIX + key, default)

    fun saveFlag(key: String, value: Boolean) {
        prefs.edit().putBoolean(FLAG_PREFIX + key, value).apply()
    }

    /** Ids of platforms shown in the platform list, or null if the user has never changed them. */
    fun loadEnabledPlatforms(): Set<String>? = prefs.getStringSet(KEY_ENABLED_PLATFORMS, null)?.toSet()

    fun saveEnabledPlatforms(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_ENABLED_PLATFORMS, ids.toSet()).apply()
    }

    fun loadString(key: String): String? = prefs.getString(STRING_PREFIX + key, null)

    fun saveString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(STRING_PREFIX + key) else putString(STRING_PREFIX + key, value)
        }.apply()
    }

    /** All string values saved with [saveString] whose key starts with [prefix], keyed by the rest of the key. */
    fun loadStringsWithPrefix(prefix: String): Map<String, String> =
        prefs.all
            .filterKeys { it.startsWith(STRING_PREFIX + prefix) }
            .mapNotNull { (key, value) -> (value as? String)?.let { key.removePrefix(STRING_PREFIX + prefix) to it } }
            .toMap()

    /** Everything the user has changed, as one JSON document (the content of a backup file). */
    fun exportJson(): JSONObject {
        val data = JSONObject()
        for ((key, value) in prefs.all) {
            if (key in NOT_BACKED_UP) continue
            val entry = JSONObject()
            when (value) {
                is String -> entry.put("t", "s").put("v", value)
                is Boolean -> entry.put("t", "b").put("v", value)
                is Set<*> -> entry.put("t", "set").put("v", JSONArray(value.filterIsInstance<String>()))
                else -> continue
            }
            data.put(key, entry)
        }
        return JSONObject().put("app", BACKUP_APP).put("version", BACKUP_VERSION).put("settings", data)
    }

    /** Replaces all stored settings with those from a backup. Returns false if [root] isn't a MinimaUI backup. */
    fun importJson(root: JSONObject): Boolean {
        if (root.optString("app") != BACKUP_APP) return false
        val data = root.optJSONObject("settings") ?: return false
        val editor = prefs.edit().clear()
        for (key in data.keys()) {
            if (key in NOT_BACKED_UP) continue
            val entry = data.getJSONObject(key)
            when (entry.getString("t")) {
                "s" -> editor.putString(key, StoredUris.persistent(entry.getString("v")))
                "b" -> editor.putBoolean(key, entry.getBoolean("v"))
                "set" -> {
                    val array = entry.getJSONArray("v")
                    editor.putStringSet(key, (0 until array.length()).map { array.getString(it) }.toSet())
                }
            }
        }
        return editor.commit()
    }

    fun loadList(key: String): List<String> =
        prefs.getString(LIST_PREFIX + key, null)?.split(LIST_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

    fun saveList(key: String, values: List<String>) {
        prefs.edit().putString(LIST_PREFIX + key, values.joinToString(LIST_SEPARATOR)).apply()
    }

    private companion object {
        const val LIST_PREFIX = "list."
        const val STRING_PREFIX = "str."
        const val BACKUP_APP = "MinimaUI"
        const val BACKUP_VERSION = 1

        /** Bookkeeping that means nothing on another install: when the last game was launched. */
        val NOT_BACKED_UP = setOf("str.launchedAt")
        const val FLAG_PREFIX = "flag."
        const val KEY_ENABLED_PLATFORMS = "enabledPlatforms"
        const val KEY_FAVORITES = "favorites"
        const val TITLE_PREFIX = "title."
        const val SEPARATOR = ","
        const val LIST_SEPARATOR = "\n"
    }
}
