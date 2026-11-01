package com.minimaui.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.minimaui.app.data.LibraryStore
import com.minimaui.app.data.PinnedShortcuts

/** Accepts old-style INSTALL_SHORTCUT broadcasts and saves the shortcut's intent to be started later. */
class LegacyShortcutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("MinimaUI", "INSTALL_SHORTCUT: ${intent.extras?.keySet()}")
        val target = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java) ?: return
        val label = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: return
        val owner = target.component?.packageName ?: target.`package` ?: return
        PinnedShortcuts(LibraryStore(context.applicationContext))
            .add(owner, PinnedShortcuts.LEGACY + target.toUri(Intent.URI_INTENT_SCHEME), label)
        Toast.makeText(context, context.getString(R.string.shortcut_added, label), Toast.LENGTH_SHORT).show()
    }
}
