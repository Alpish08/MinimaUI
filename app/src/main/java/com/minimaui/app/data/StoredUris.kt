package com.minimaui.app.data

import android.net.Uri
import android.os.Environment
import java.io.File

/**
 * Picked images and music are remembered as plain file paths whenever they sit on internal storage and MinimaUI has
 * "All files access". A file picker's content:// grant is lost when settings are restored or the app is reinstalled,
 * but a path keeps working, so covers survive a backup/restore without being picked again.
 */
object StoredUris {
    private const val DOCUMENT_PREFIX = "content://com.android.externalstorage.documents/document/"

    /** [uri] as a file:// URI when it names a readable file on internal storage; otherwise [uri] unchanged. */
    fun persistent(uri: String): String {
        if (!uri.startsWith(DOCUMENT_PREFIX) || !Environment.isExternalStorageManager()) return uri
        val documentId = Uri.decode(uri.removePrefix(DOCUMENT_PREFIX))
        if (!documentId.startsWith("primary:")) return uri
        val file = File(Environment.getExternalStorageDirectory(), documentId.removePrefix("primary:"))
        return if (file.isFile && file.canRead()) Uri.fromFile(file).toString() else uri
    }
}
