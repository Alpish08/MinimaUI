package com.minimaui.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import java.io.File

class InstalledApp(val packageName: String, val label: String)

/** The apps on the device that have a launcher icon, for adding to the Apps page or the Android platform. */
object InstalledApps {
    fun list(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(query, 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * The app's icon filling a square, with an adaptive icon's padding cropped away so its artwork reaches the
     * edges. Callers clip it to a circle (or a tile) as needed.
     */
    fun iconBitmap(context: Context, packageName: String, size: Int): Bitmap {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Adaptive icons are 108dp with the visible 72dp in the middle.
        val pad = if (drawable is AdaptiveIconDrawable) (size * 18f / 72f).toInt() else 0
        if (drawable is AdaptiveIconDrawable) {
            drawable.background?.apply { setBounds(-pad, -pad, size + pad, size + pad); draw(canvas) }
            drawable.foreground?.apply { setBounds(-pad, -pad, size + pad, size + pad); draw(canvas) }
        } else {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
        return bitmap
    }

    /** Saves the app's icon as a PNG in app storage and returns its URI, so it can be the tile's cover. */
    fun saveIcon(context: Context, packageName: String): String? = runCatching {
        val dir = File(context.filesDir, "icons").apply { mkdirs() }
        val file = File(dir, "$packageName.png")
        file.outputStream().use { iconBitmap(context, packageName, 256).compress(Bitmap.CompressFormat.PNG, 100, it) }
        Uri.fromFile(file).toString()
    }.getOrNull()
}
