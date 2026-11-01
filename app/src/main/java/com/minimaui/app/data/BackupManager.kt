package com.minimaui.app.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.minimaui.app.model.WaveConfig
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes and reads settings backups: one text file ([EXTENSION], JSON inside) per backup, kept in
 * MinimaUI/Backup on internal storage. Needs "All files access" so the folder (and the backups)
 * survive reinstalling the app.
 */
class BackupManager(private val context: Context, private val store: LibraryStore) {
    fun hasStorageAccess(): Boolean = Environment.isExternalStorageManager()

    fun createBackup(): Result<File> = runCatching {
        val directory = File(Environment.getExternalStorageDirectory(), BACKUP_PATH)
        check(directory.isDirectory || directory.mkdirs()) { "Couldn't create the backup folder" }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        File(directory, "MinimaUI_backup_$stamp.$EXTENSION").also {
            it.writeText(store.exportJson().toString(2))
        }
    }

    /** Saves the custom wave [config] as the next Custom_wave_N.xmb (its own extension, so it never mixes with settings backups) in MinimaUI/Backup/Custom Wave. */
    fun saveWave(config: WaveConfig): Result<File> = runCatching {
        val directory = File(Environment.getExternalStorageDirectory(), WAVE_BACKUP_PATH)
        check(directory.isDirectory || directory.mkdirs()) { "Couldn't create the backup folder" }
        val taken = directory.listFiles().orEmpty().mapNotNull { WAVE_FILE.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
        File(directory, "Custom_wave_${(taken.maxOrNull() ?: 0) + 1}.$WAVE_EXTENSION").also {
            it.writeText(WaveConfig.backupText(config))
        }
    }

    /**
     * Restores a backup file. A settings backup replaces all settings (null is returned); a custom wave file changes
     * nothing but the wave, which is returned for the caller to apply.
     */
    fun restore(uri: Uri): Result<WaveConfig?> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Couldn't open the file")
        if (!text.trimStart().startsWith("{")) {
            return@runCatching WaveConfig.fromBackupText(text) ?: error("Not a MinimaUI backup")
        }
        check(store.importJson(JSONObject(text))) { "Not a MinimaUI backup" }
        null
    }

    companion object {
        const val EXTENSION = "backup"
        private const val BACKUP_PATH = "MinimaUI/Backup"
        private const val WAVE_BACKUP_PATH = "MinimaUI/Backup/Custom Wave"
        private const val WAVE_EXTENSION = "xmb"
        private val WAVE_FILE = Regex("Custom_wave_(\\d+)\\.$WAVE_EXTENSION")

        /** Where the restore file picker should start. */
        fun pickerStartUri(): Uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$BACKUP_PATH"
        )
    }
}
