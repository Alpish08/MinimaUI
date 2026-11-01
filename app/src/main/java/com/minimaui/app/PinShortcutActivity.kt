package com.minimaui.app

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import com.minimaui.app.data.LibraryStore
import com.minimaui.app.data.PinnedShortcuts

/**
 * Receives "add to home screen" requests from other apps (Winlator Cmod, GameHub...). Android only sends
 * these to the default home app. The shortcut is accepted and saved as a Steam game; there is no UI.
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = getSystemService(LauncherApps::class.java).getPinItemRequest(intent)
        android.util.Log.d("MinimaUI", "Pin request: type=${request?.requestType} valid=${request?.isValid}")
        val info = request?.takeIf {
            it.isValid && it.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT
        }?.shortcutInfo
        if (request != null && info != null && request.accept()) {
            val label = info.shortLabel?.toString() ?: info.id
            PinnedShortcuts(LibraryStore(applicationContext)).add(info.`package`, info.id, label)
            Toast.makeText(this, getString(R.string.shortcut_added, label), Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
