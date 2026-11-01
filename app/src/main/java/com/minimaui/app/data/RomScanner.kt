package com.minimaui.app.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.minimaui.app.model.GameEntry

/**
 * Finds games in the ROM folders the user added. For each added folder it reads the files directly in
 * it and in its immediate subfolders only (so "ROMs/xbox" is read but "ROMs/psv/ux0" is not). A file
 * belongs to the platform its folder is named after ("xbox" -> Xbox), or failing that to the one its
 * extension implies (.art -> Windows, .psvita -> PS Vita), and only counts if its extension is one that
 * platform uses. Windows/Steam folders are skipped. A game's title is its file name without the extension and without any "(...)" or "[...]" tail.
 */
class RomScanner(private val resolver: ContentResolver) {
    private class Entry(val id: String, val name: String, val isDirectory: Boolean)

    fun scan(treeUris: List<String>): List<GameEntry> {
        val games = LinkedHashMap<String, GameEntry>()
        for (tree in treeUris) {
            val treeUri = Uri.parse(tree)
            val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: continue
            val rootName = rootId.substringAfter(':').substringAfterLast('/')
            if (PlatformCatalog.isIgnoredFolder(rootName)) continue
            val rootPlatform = PlatformCatalog.platformForFolder(rootName)
            for (child in children(treeUri, rootId)) {
                if (child.isDirectory) {
                    if (PlatformCatalog.isIgnoredFolder(child.name)) continue
                    val folderPlatform = PlatformCatalog.platformForFolder(child.name)
                    for (file in children(treeUri, child.id)) {
                        if (!file.isDirectory) add(games, treeUri, file, folderPlatform)
                    }
                } else {
                    add(games, treeUri, child, rootPlatform)
                }
            }
        }
        return games.values.toList()
    }

    private fun add(games: MutableMap<String, GameEntry>, tree: Uri, file: Entry, folderPlatform: String?) {
        if (file.name.startsWith(".")) return
        val extension = file.name.substringAfterLast('.', "").lowercase()
        val platform = folderPlatform ?: PlatformCatalog.platformForExtension(extension) ?: return
        if (!PlatformCatalog.isGameFile(platform, extension)) return
        val title = cleanTitle(file.name.substringBeforeLast('.').ifBlank { file.name })
        val id = "$platform:${file.id}"
        games[id] = GameEntry(
            id = id,
            platformId = platform,
            title = title,
            fileUri = DocumentsContract.buildDocumentUriUsingTree(tree, file.id).toString(),
        )
    }

    /** Drops everything from the first "(" or "[" on (version codes, regions, title ids), unless that leaves nothing. */
    private fun cleanTitle(name: String): String =
        name.substringBefore('(').substringBefore('[').trim().ifBlank { name }

    private fun children(tree: Uri, parentId: String): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return runCatching {
            resolver.query(uri, columns, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Entry(
                                id = cursor.getString(0),
                                name = cursor.getString(1).orEmpty(),
                                isDirectory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                            )
                        )
                    }
                }
            }
        }.getOrNull().orEmpty()
    }
}
