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
        // The proportions are those of the icons in MinimaUI/Platform Icons (see iconNames).
        Platform("ps1", "PS1", 383f / 1920f),
        Platform("ps3", "PS3", 699f / 1920f),
        Platform("gc", "GameCube", 434f / 1921f),
        Platform("wii", "Wii", 919f / 1920f),
        Platform("n64", "N64", 428f / 1434f),
        Platform("nds", "DS", 276f / 1921f),
        Platform("gb", "Game Boy", 343f / 1921f),
        Platform("snes", "SNES", 510f / 1920f),
        Platform("dreamcast", "Dreamcast", 330f / 1920f),
        Platform("xbox360", "Xbox 360", 396f / 1920f),
    )

    /**
     * The image names (without extension, any letter case) a platform's tile takes from MinimaUI/Platform Icons when the user
     * hasn't picked an image for it; the first one found wins.
     */
    val iconNames: Map<String, List<String>> = mapOf(
        "android" to listOf("Android"),
        "steam" to listOf("Steam"),
        "windows" to listOf("Windows"),
        "psp" to listOf("Sony PSP", "Sony PlayStation Portable", "Sony Playstation Portable", "PSP"),
        "switch" to listOf("Nintendo Switch"),
        "vita" to listOf("Sony PS Vita", "Sony PlayStation Vita", "Sony Playstation Vita", "PS Vita"),
        "ps1" to listOf("Sony Playstation", "Sony PlayStation", "PS1"),
        "ps2" to listOf("Sony Playstation 2", "Sony PlayStation 2", "PS2"),
        "ps3" to listOf("Sony Playstation 3", "Sony PlayStation 3", "PS3"),
        "n3ds" to listOf("Nintendo 3DS"),
        "wiiu" to listOf("Nintendo Wii U"),
        "wii" to listOf("Nintendo Wii"),
        "gc" to listOf("Nintendo GameCube"),
        "n64" to listOf("Nintendo 64"),
        "nds" to listOf("Nintendo DS"),
        "gb" to listOf("Nintendo Game Boy", "Nintendo Game Boy Advance"),
        "snes" to listOf("Super Nintendo Entertainment System", "Nintendo SNES", "Nintendo Entertainment System", "SNES"),
        "xbox" to listOf("Microsoft Xbox"),
        "xbox360" to listOf("Microsoft Xbox 360"),
        "dreamcast" to listOf("Sega Dreamcast"),
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
        "ps1" to "ps1", "psx" to "ps1", "psone" to "ps1", "playstation" to "ps1", "playstation1" to "ps1", "sonyplaystation" to "ps1",
        "ps3" to "ps3", "playstation3" to "ps3",
        "gc" to "gc", "ngc" to "gc", "gamecube" to "gc", "nintendogamecube" to "gc",
        "wii" to "wii", "nintendowii" to "wii",
        "n64" to "n64", "nintendo64" to "n64",
        "nds" to "nds", "ds" to "nds", "nintendods" to "nds",
        "gb" to "gb", "gbc" to "gb", "gba" to "gb", "gameboy" to "gb", "gameboycolor" to "gb", "gameboyadvance" to "gb",
        "snes" to "snes", "sfc" to "snes", "supernintendo" to "snes", "supernintendoentertainmentsystem" to "snes", "superfamicom" to "snes",
        "nes" to "snes", "fc" to "snes", "famicom" to "snes", "nintendoentertainmentsystem" to "snes",
        "dreamcast" to "dreamcast", "dc" to "dreamcast", "segadreamcast" to "dreamcast",
        "xbox360" to "xbox360", "x360" to "xbox360", "360" to "xbox360", "microsoftxbox360" to "xbox360",
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
        "ps1" to setOf("chd", "cue", "pbp", "iso", "m3u"),
        "ps3" to setOf("iso", "ps3"),
        "gc" to setOf("iso", "gcm", "rvz", "gcz", "ciso", "wia", "tgc", "dol"),
        "wii" to setOf("iso", "wbfs", "rvz", "wia", "wad", "gcz", "ciso", "dol"),
        "n64" to setOf("n64", "z64", "v64", "ndd", "zip"),
        "nds" to setOf("nds", "dsi", "zip"),
        "gb" to setOf("gb", "gbc", "gba", "zip"),
        "snes" to setOf("sfc", "smc", "swc", "fig", "bs", "bsx", "st", "gd3", "gd7", "dx2", "mgd", "bml", "nes", "fds", "unf", "unif", "zip"),
        "dreamcast" to setOf("gdi", "cdi", "chd", "cue", "iso", "m3u"),
        "xbox360" to setOf("iso", "xex", "zar"),
    )

    /** Extensions that name their platform wherever the file is stored: Artemis streams (.art) and Vita games. */
    private val extensionPlatforms = mapOf("art" to "windows", "psvita" to "vita", "ps3" to "ps3")

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
