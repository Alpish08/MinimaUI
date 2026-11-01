package com.minimaui.app.model

/** An entry on the Apps page. [coverUri] is its icon/cover image; the page's glow takes that image's average colour. */
data class AppEntry(
    val id: String,
    val title: String,
    val coverUri: String? = null,
)
