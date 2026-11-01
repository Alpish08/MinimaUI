package com.minimaui.app.model

/**
 * A single library entry. Title and cover art are always supplied manually —
 * MinimaUI never scrapes or fetches metadata.
 *
 * [coverUri] is the 1:1 image the user picked for this game from Favorites (a persisted content URI);
 * null shows the blank placeholder tile. [xmbCoverUri] is the separate 16:9 image picked from the All Games (XMB)
 * page, where it is the only cover shown: a game without one shows no cover there.
 */
data class GameEntry(
    val id: String,
    val platformId: String,
    val title: String,
    val coverUri: String? = null,
    val xmbCoverUri: String? = null,
    val isFavorite: Boolean = false,
    /** The ROM file (a persisted content URI), used to launch the game. */
    val fileUri: String? = null,
    /** For games added as app shortcuts (Winlator, GameHub): the owning app's package and the shortcut id. */
    val shortcutPackage: String? = null,
    val shortcutId: String? = null,
    /** An app that is only a favorite (added from the Apps page): it is in Favorites, but not listed under its platform. */
    val favoritesOnly: Boolean = false,
    /** The package of the emulator the user chose for this game (instead of the platform's usual one), or null. */
    val customEmulator: String? = null,
    /** The emulator the user chose for the game's whole platform (used when the game has no emulator of its own), or null. */
    val platformEmulator: String? = null,
)
