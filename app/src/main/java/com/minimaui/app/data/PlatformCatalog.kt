package com.minimaui.app.data

import com.minimaui.app.model.Platform

/** The platforms MinimaUI knows about, in XMB order, and how ROM folders and files map onto them. */
object PlatformCatalog {
    val platforms: List<Platform> = listOf(
        Platform("psp", "PSP", 172f / 1000f),
        Platform("steam", "Steam", 202f / 640f),
        Platform("switch", "Nintendo Switch", 140f / 640f),
        Platform("android", "Android", 97f / 640f),
        Platform("vita", "PS Vita", 118f / 640f),
        Platform("ps2", "PS2", 195f / 1000f),
        Platform("n3ds", "N3DS", 78f / 640f),
        Platform("wiiu", "Wii U", 192f / 640f),
        Platform("xbox", "XBOX", 205f / 1213f),
        Platform("windows", "Windows", 135f / 640f),
    )

    /** Folder names that hold a platform's games. Windows and Steam have none: they come from .art files and pinned shortcuts. */
    private val folderAliases = mapOf(
        "n3ds" to "n3ds", "3ds" to "n3ds", "nintendo3ds" to "n3ds",
        "ps2" to "ps2", "playstation2" to "ps2",
        "psp" to "psp", "playstationportable" to "psp",
        "switch" to "switch", "nintendoswitch" to "switch",
        "wiiu" to "wiiu", "nintendowiiu" to "wiiu",
        "xbox" to "xbox",
        "android" to "android",
        "vita" to "vita", "psvita" to "vita", "psv" to "vita",
        "artemis" to "windows",
    )

    /** The file extensions each platform's games use; anything else in its folder is ignored. */
    private val platformExtensions = mapOf(
        "switch" to setOf("nsp", "xci", "nsz", "xcz", "nro"),
        "n3ds" to setOf("cci", "3ds", "cia"),
        "psp" to setOf("iso", "cso", "pbp"),
        "ps2" to setOf("chd", "iso"),
        "wiiu" to setOf("wux", "wud", "wua"),
        "xbox" to setOf("xiso", "iso"),
        "vita" to setOf("psvita"),
        "windows" to setOf("art"),
    )

    /** Extensions that name their platform wherever the file is stored: Artemis streams (.art) and Vita games. */
    private val extensionPlatforms = mapOf("art" to "windows", "psvita" to "vita")

    /** Folders that are never read, even though a platform has that name. */
    private val ignoredFolders = setOf("windows", "pc", "steam", "winlator")

    fun isIgnoredFolder(name: String): Boolean = name.lowercase().filter { it.isLetterOrDigit() } in ignoredFolders

    fun isGameFile(platformId: String, extension: String): Boolean =
        platformExtensions[platformId]?.contains(extension.lowercase()) == true

    /** The platform a folder holds games for, going by its name ("Xbox", "ps2", "PS Vita"...), or null. */
    fun platformForFolder(name: String): String? =
        folderAliases[name.lowercase().filter { it.isLetterOrDigit() }]

    fun platformForExtension(extension: String): String? = extensionPlatforms[extension.lowercase()]
}
