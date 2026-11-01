package com.minimaui.app.data

import android.os.Environment
import java.io.File

/** The MinimaUI/ folder layout on internal storage, created on launch (storage access isn't needed if the system lets the app make them; otherwise they appear once it is granted). */
object MinimaFolders {
    private val folders = listOf(
        "Backup",
        "GameID",
        "Music",
        "Sounds",
        "Cover Art/Favorites",
    ) + listOf("Android", "Nintendo Switch", "PS2", "PSP", "Steam", "Vita", "Windows", "WiiU", "XBOX", "3DS")
        .map { "Cover Art/All Games/$it" }

    /** Creates any folder that is missing; returns true once all of them exist, so a launch without access tries again later. */
    fun ensure(): Boolean {
        val root = File(Environment.getExternalStorageDirectory(), "MinimaUI")
        return folders.map { File(root, it).let { dir -> dir.isDirectory || dir.mkdirs() } }.all { it }
    }
}
