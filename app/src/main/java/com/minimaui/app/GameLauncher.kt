package com.minimaui.app

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Process
import android.widget.Toast
import com.minimaui.app.data.GameHubGames
import com.minimaui.app.data.InstalledApps
import com.minimaui.app.data.PinnedShortcuts
import com.minimaui.app.model.GameEntry

/** Starts a game: a pinned shortcut, or a ROM in the first installed emulator for its platform. */
class GameLauncher(private val context: Context) {
    /** An emulator activity, as "package/Class" ("/.Class" is relative to the package). */
    private class Target(entry: String) {
        val packageName = entry.substringBefore('/')
        val className = entry.substringAfter('/').let { if (it.startsWith('.')) packageName + it else it }
    }

    /** What an emulator is told about a game: the document URI, the file's path (null if it has none) and its extension. */
    private class Rom(val uri: Uri, val path: String?, val extension: String)

    /**
     * One way to start games in an emulator: the activity to open ([entry], "package/Class"), the action and category to send, and
     * what to pass. [fresh] clears the emulator's task first (so a running game is replaced). [data] puts the game URI in the intent's
     * data; [extras] add the extras the emulator reads (the URI or path under the name it expects).
     */
    private class Emu(
        entry: String,
        val action: String? = Intent.ACTION_VIEW,
        val category: String? = null,
        val fresh: Boolean = false,
        val data: Boolean = true,
        val extras: (Rom) -> Map<String, Any> = { emptyMap() },
    ) {
        val target = Target(entry)

        fun intent(rom: Rom): Intent {
            val intent = Intent()
                .setComponent(ComponentName(target.packageName, target.className))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            action?.let(intent::setAction)
            category?.let(intent::addCategory)
            if (fresh) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (data) intent.data = rom.uri
            // The game's URI also rides along as a clip, so the access MinimaUI has to the ROM folder is handed on with it
            // even where the emulator only gets the URI as an extra.
            intent.clipData = android.content.ClipData.newRawUri("rom", rom.uri)
            for ((name, value) in extras(rom)) {
                when (value) {
                    is Boolean -> intent.putExtra(name, value)
                    is Int -> intent.putExtra(name, value)
                    else -> intent.putExtra(name, value.toString())
                }
            }
            return intent
        }
    }

    /** No slide: MinimaUI fades itself out first, then the game's window simply appears. */
    private val noTransition: android.os.Bundle =
        android.app.ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()

    /** Starts [game]; false (with a toast saying why) if nothing took it. */
    fun launch(game: GameEntry): Boolean {
        val error = when {
            game.shortcutId?.startsWith(GameHubGames.PREFIX) == true -> {
                val launcher = game.customEmulator ?: game.platformEmulator
                val id = game.shortcutId.removePrefix(GameHubGames.PREFIX)
                // The game's Steam launcher (chosen per game or for the Steam platform) starts it; otherwise GameHub does.
                if (launcher != null && launcher != GameHubGames.PACKAGE) startSteamGame(launcher, id)
                else if (startGameHub(id)) null else "Could not start this GameHub game"
            }
            game.shortcutPackage != null && game.shortcutId != null ->
                if (startShortcut(game.shortcutPackage, game.shortcutId)) null else "Could not start this shortcut"
            game.shortcutPackage != null -> if (launchPackage(game.shortcutPackage)) null else "Could not start this app"
            game.fileUri != null -> startRom(game.platformId, Uri.parse(game.fileUri), game.customEmulator ?: game.platformEmulator)
            else -> "Could not start this game"
        }
        if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        return error == null
    }

    fun launchPackage(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION), noTransition)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Starts a GameHub game the way GameHub's own home-screen shortcuts do (read from `dumpsys shortcut`): a VIEW
     * intent to its DeepLinkActivity, with the target in extras. An imported game is started by its "local_..." id.
     * A Steam download is "SteamAppId/GameHubGameId": GameHub's page for it opens and starts it (Steam games may
     * show GameHub's own launch-options prompt first). A bare Steam app id can only open GameHub.
     */
    private fun startGameHub(gameId: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setComponent(ComponentName(GameHubGames.PACKAGE, GAMEHUB_DEEP_LINK_ACTIVITY))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val steamId = gameId.substringBefore('/')
        if (steamId.isNotEmpty() && steamId.all { it.isDigit() }) {
            val serverId = gameId.substringAfter('/', "")
            intent.putExtra("app_nav_target", "game_detail")
                .putExtra("app_nav_steam_app_id", steamId)
                .putExtra("app_nav_source_id", steamId)
                .putExtra("app_nav_source_type", 1)
                .putExtra("app_nav_launch_type", 1407)
                .putExtra("app_nav_auto_start_game", true)
                .putExtra("app_nav_epic_app_name", "")
                .putExtra("app_nav_source_slug", "")
                .putExtra("app_nav_game_type", "")
            if (serverId.isNotEmpty()) intent.putExtra("app_nav_game_id", serverId)
        } else {
            // Older entries may be saved as "local:<id>" or without the prefix; GameHub's ids start with "local_".
            val id = gameId.removePrefix("local:").let { if (it.startsWith("local_")) it else "local_$it" }
            intent.putExtra("app_nav_target", "local_game_launch").putExtra("app_nav_game_id", id)
        }
        return try {
            context.startActivity(intent, noTransition)
            true
        } catch (e: Exception) {
            launchPackage(GameHubGames.PACKAGE)
        }
    }

    /**
     * Starts a game of the Steam platform in [packageName], a launcher the user chose: GameNative (by the game's Steam App ID) or
     * GameHub Lite (by the Steam App ID, or the id of a game imported into it). Both are from ES-DE's rules; not tried on a device yet.
     */
    private fun startSteamGame(packageName: String, gameId: String): String? {
        val steamId = gameId.substringBefore('/').takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        val intent = when {
            packageName == GAMENATIVE_PACKAGE -> {
                val appId = steamId?.toIntOrNull() ?: return "GameNative starts a game by its Steam App ID (this one has none)"
                Intent("app.gamenative.LAUNCH_GAME").setComponent(ComponentName(packageName, "app.gamenative.MainActivity"))
                    .putExtra("game_source", "STEAM").putExtra("app_id", appId)
            }
            // GameHub Lite comes under several package names, so any chosen app that isn't GameNative is sent GameHub Lite's launch intent.
            else -> Intent("gamehub.lite.LAUNCH_GAME").setComponent(ComponentName(packageName, GAMEHUB_LITE_ACTIVITY))
                .putExtra("autoStartGame", true).apply {
                    if (steamId != null) putExtra("steamAppId", steamId)
                    else putExtra("localGameId", gameId.removePrefix("local:").let { if (it.startsWith("local_")) it else "local_$it" })
                }
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION), noTransition)
            null
        } catch (e: ActivityNotFoundException) {
            "${appLabel(packageName)} is not installed (or has no such launch intent)"
        } catch (e: Exception) {
            "${appLabel(packageName)} refused: ${e.javaClass.simpleName}"
        }
    }

    private fun startShortcut(packageName: String, id: String): Boolean = try {
        if (id.startsWith(PinnedShortcuts.LEGACY)) {
            val intent = Intent.parseUri(id.removePrefix(PinnedShortcuts.LEGACY), Intent.URI_INTENT_SCHEME)
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION), noTransition)
        } else {
            context.getSystemService(LauncherApps::class.java)
                .startShortcut(packageName, id, null, noTransition, Process.myUserHandle())
        }
        true
    } catch (e: Exception) {
        false
    }

    /** Null if an emulator took the game, else why none did. */
    private fun startRom(platformId: String, rom: Uri, customEmulator: String? = null): String? {
        if (customEmulator != null) return openRomWith(customEmulator, appLabel(customEmulator), rom)
        if (platformId == "vita") return startVita(rom)
        if (platformId in byLabel) return startByLabel(byLabel.getValue(platformId), rom)
        if (platformId in modernPlatforms) return startModern(platformId, rom)
        val targets = emulators[platformId].orEmpty().map(::Target)
        if (targets.isEmpty()) return "No emulator is set up for $platformId yet"
        val path = filePath(rom)
        var rejected: String? = null
        for (target in targets) {
            val intent = Intent(Intent.ACTION_VIEW)
                .setComponent(ComponentName(target.packageName, target.className))
                .setData(rom)
                .putExtra("bootPath", path)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_GRANT_READ_URI_PERMISSION or freshTaskFlags(target.packageName))
            try {
                context.startActivity(intent, noTransition)
                return null
            } catch (_: ActivityNotFoundException) {
                // Not installed (or no such activity): try the next one.
            } catch (e: Exception) {
                rejected = "${target.packageName} refused: ${e.javaClass.simpleName}"
            }
        }
        return rejected ?: fallbackLabels[platformId]?.let { startByLabel(it, rom) } ?: "No supported emulator found for $platformId"
    }

    private fun romOf(uri: Uri): Rom {
        val path = filePath(uri)
        val name = path ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull().orEmpty()
        return Rom(uri, path, name.substringAfterLast('.', "").lowercase())
    }

    /** The first line of a small text file that stands for a game (a PS3 serial in a .ps3 file), or null. */
    private fun firstLine(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()

    /** Starts [platformId]'s game in the first installed emulator of [modernEmulators] that takes it. */
    private fun startModern(platformId: String, uri: Uri): String? {
        val rom = romOf(uri)
        // A .ps3 file holds the game's serial; the emulators start the installed game by it.
        val launch = if (platformId == "ps3" && rom.extension == "ps3") {
            val serial = firstLine(uri) ?: return "Could not read the .ps3 file (it should hold the game's serial)"
            Rom(uri, rom.path, "serial:$serial")
        } else rom
        var rejected: String? = null
        for (emu in emulatorsFor(platformId, launch.extension)) {
            try {
                context.startActivity(emu.intent(launch), noTransition)
                return null
            } catch (_: ActivityNotFoundException) {
                // Not installed (or no such activity): try the next one.
            } catch (e: Exception) {
                rejected = "${emu.target.packageName} refused: ${e.javaClass.simpleName}"
            }
        }
        return rejected ?: "No supported emulator found for ${platformLabels[platformId] ?: platformId}"
    }

    /** The emulators to try for a game with this extension (Game Boy games go to the Game Boy or the Game Boy Advance ones by it). */
    private fun emulatorsFor(platformId: String, extension: String): List<Emu> = when (platformId) {
        "gb" -> when (extension) {
            "gba" -> gbaEmulators
            "gb", "gbc" -> gbcEmulators
            else -> gbaEmulators + gbcEmulators
        }
        else -> modernEmulators[platformId].orEmpty()
    }

    /** Vita3K boots a game from its title id, which is what a .psvita file holds. */
    private fun startVita(rom: Uri): String? {
        val titleId = runCatching {
            context.contentResolver.openInputStream(rom)?.bufferedReader()?.use { it.readText().trim() }
        }.getOrNull()?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
            ?: return "Could not read the .psvita file"
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName("org.vita3k.emulator", "org.vita3k.emulator.Emulator"))
            .putExtra("AppStartParameters", arrayOf("-r", titleId))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        return try {
            context.startActivity(intent, noTransition)
            null
        } catch (e: ActivityNotFoundException) {
            "Vita3K is not installed"
        } catch (e: Exception) {
            "Vita3K refused: ${e.javaClass.simpleName}"
        }
    }

    /** For emulators without a known activity: finds the installed app by name and lets Android pick its handler. */
    private fun startByLabel(label: String, rom: Uri): String? {
        val app = InstalledApps.list(context).firstOrNull { it.label.contains(label, ignoreCase = true) }
            ?: return "$label is not installed"
        return openRomWith(app.packageName, app.label, rom)
    }

    /** Opens [rom] in the app [packageName]: its own handler for the file, else the app's front door with the file attached. */
    private fun openRomWith(packageName: String, label: String, rom: Uri): String? {
        val grant = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        // An emulator whose own activity is known (ArmSX2 only starts a game that way) gets it first.
        val known = emulators.values.flatten().map(::Target).firstOrNull { it.packageName == packageName && it.packageName in freshTask }
        // An emulator whose intent is known (see modernEmulators) is started the way it wants, first.
        val romInfo = romOf(rom)
        val modern = (modernEmulators.values.flatten() + gbaEmulators + gbcEmulators).firstOrNull { it.target.packageName == packageName }
        val attempts = listOfNotNull(
            modern?.intent(romInfo),
            known?.let { Intent(Intent.ACTION_VIEW).setComponent(ComponentName(it.packageName, it.className)).setData(rom).putExtra("bootPath", filePath(rom)) },
            Intent(Intent.ACTION_VIEW).setPackage(packageName).setData(rom).putExtra("bootPath", filePath(rom)),
            context.packageManager.getLaunchIntentForPackage(packageName)?.setData(rom),
        )
        for (intent in attempts) {
            try {
                context.startActivity(intent.addFlags(grant or freshTaskFlags(packageName)), noTransition)
                return null
            } catch (_: Exception) {
            }
        }
        return "$label could not open the game"
    }

    /** ArmSX2 is started with clear-task + clear-top, so a game that is already running is replaced. */
    private fun freshTaskFlags(packageName: String): Int =
        if (packageName in freshTask) Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP else 0

    private fun appLabel(packageName: String): String = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
    }.getOrDefault(packageName)

    /** The plain file path of a document on shared storage, for emulators that want one instead of a URI. */
    private fun filePath(rom: Uri): String? = runCatching {
        val id = DocumentsContract.getDocumentId(rom)
        val volume = id.substringBefore(':')
        val relative = id.substringAfter(':')
        if (volume == "primary") "/storage/emulated/0/$relative" else "/storage/$volume/$relative"
    }.getOrNull()

    private companion object {
        const val GAMEHUB_DEEP_LINK_ACTIVITY = "com.xiaoji.egggame.DeepLinkActivity"
        const val GAMENATIVE_PACKAGE = "app.gamenative"
        const val GAMEHUB_LITE_ACTIVITY = "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity"

        /** The platforms started through [modernEmulators]. */
        val modernPlatforms = setOf("ps1", "gc", "wii", "dreamcast", "n64", "nds", "gb", "ps3", "xbox360")

        val platformLabels = mapOf(
            "ps1" to "PS1", "gc" to "GameCube", "wii" to "Wii", "dreamcast" to "Dreamcast", "n64" to "N64", "nds" to "DS",
            "gb" to "Game Boy", "ps3" to "PS3", "xbox360" to "Xbox 360",
        )

        private fun uriExtra(name: String): (Rom) -> Map<String, Any> = { mapOf(name to it.uri.toString()) }

        // aPS3e takes an ISO as a URI, or the installed game by its serial (the folder it keeps its games in); ARMSX3 takes a path, or a serial.
        private fun aps3e(entry: String) = Emu(entry, action = "aenu.intent.action.APS3E", data = false) { rom ->
            if (rom.extension.startsWith("serial:")) {
                val pkg = entry.substringBefore('/')
                mapOf("game_dir" to "/storage/emulated/0/Android/data/$pkg/files/aps3e/config/dev_hdd0/game/${rom.extension.removePrefix("serial:")}")
            } else mapOf("iso_uri" to rom.uri.toString())
        }

        private val armsx3 = Emu("com.armsx3/com.armsx2.Main", action = null, data = false) { rom ->
            if (rom.extension.startsWith("serial:")) mapOf("title_id" to rom.extension.removePrefix("serial:"))
            else mapOf("path" to (rom.path ?: rom.uri.toString()))
        }

        private fun dolphin(entry: String, mmjr: Boolean = false) =
            Emu(entry, action = if (mmjr) Intent.ACTION_VIEW else Intent.ACTION_MAIN, category = if (mmjr) null else Intent.CATEGORY_LEANBACK_LAUNCHER,
                data = false, extras = uriExtra("AutoStartFile"))

        private val gbaEmulators = listOf(
            Emu("it.dbtecno.pizzaboygbapro/it.dbtecno.pizzaboygbapro.MainActivity", action = null, fresh = true, data = false, extras = uriExtra("rom_uri")),
            Emu("it.dbtecno.pizzaboygba/it.dbtecno.pizzaboygba.MainActivity", action = null, fresh = true, data = false, extras = uriExtra("rom_uri")),
            Emu("com.fastemulator.gba/.EmulatorActivity"),
            Emu("com.explusalpha.GbaEmu/com.imagine.BaseActivity"),
        )

        private val gbcEmulators = listOf(
            Emu("it.dbtecno.pizzaboypro/it.dbtecno.pizzaboypro.MainActivity", action = null, fresh = true, data = false, extras = uriExtra("rom_uri")),
            Emu("it.dbtecno.pizzaboy/it.dbtecno.pizzaboy.MainActivity", action = null, fresh = true, data = false, extras = uriExtra("rom_uri")),
            Emu("com.fastemulator.gbc/.EmulatorActivity"),
            Emu("com.explusalpha.GbcEmu/com.imagine.BaseActivity"),
        )

        /**
         * From ES-DE's Android rules (es_find_rules.xml and es_systems.xml), tried in order; none of these has been tried on a device yet.
         */
        val modernEmulators: Map<String, List<Emu>> = mapOf(
            "ps1" to listOf(
                Emu("com.github.stenzek.duckstation/.EmulationActivity", action = null, fresh = true, data = false) {
                    mapOf("resumeState" to false, "bootPath" to it.uri.toString())
                },
            ),
            "gc" to listOf(
                dolphin("org.dolphinemu.dolphinemu/.ui.main.TvMainActivity"),
                dolphin("org.dolphinemu.mmjr/org.dolphinemu.dolphinemu.ui.main.MainActivity", mmjr = true),
                dolphin("org.mm.jr/org.dolphinemu.dolphinemu.ui.main.MainActivity", mmjr = true),
            ),
            "wii" to listOf(
                dolphin("org.dolphinemu.dolphinemu/.ui.main.TvMainActivity"),
                dolphin("org.dolphinemu.mmjr/org.dolphinemu.dolphinemu.ui.main.MainActivity", mmjr = true),
                dolphin("org.mm.jr/org.dolphinemu.dolphinemu.ui.main.MainActivity", mmjr = true),
            ),
            "dreamcast" to listOf(
                Emu("com.flycast.emulator/com.flycast.emulator.MainActivity"),
                Emu("com.flycast.emulator/com.reicast.emulator.MainActivity"),
                Emu("io.recompiled.redream/.MainActivity"),
            ),
            "n64" to listOf(
                Emu("org.mupen64plusae.v3.fzurita/paulscode.android.mupen64plusae.SplashActivity"),
                Emu("org.mupen64plusae.v3.fzurita.pro/paulscode.android.mupen64plusae.SplashActivity"),
                Emu("org.mupen64plusae.v3.fzurita.amazon/paulscode.android.mupen64plusae.SplashActivity"),
                Emu("org.mupen64plusae.v3.alpha/paulscode.android.mupen64plusae.SplashActivity"),
            ),
            "nds" to listOf(
                Emu("me.magnum.melonds/.ui.emulator.EmulatorActivity", action = "me.magnum.melonds.LAUNCH_ROM", data = false, extras = uriExtra("uri")),
                Emu("me.magnum.melondualds/me.magnum.melonds.ui.emulator.EmulatorActivity", action = "me.magnum.melonds.LAUNCH_ROM", data = false, extras = uriExtra("uri")),
                Emu("me.magnum.melonds.nightly/me.magnum.melonds.ui.emulator.EmulatorActivity", action = "me.magnum.melonds.nightly.LAUNCH_ROM", data = false, extras = uriExtra("uri")),
                Emu("com.dsemu.drastic/.DraSticActivity", fresh = true),
                Emu("com.hydra.noods/.FileBrowser", action = null, fresh = true, data = false) { mapOf("LaunchPath" to (it.path ?: it.uri.toString())) },
            ),
            "ps3" to listOf(
                aps3e("aenu.aps3e.premium/aenu.aps3e.EmulatorActivity"),
                aps3e("aenu.aps3e/aenu.aps3e.EmulatorActivity"),
                armsx3,
            ),
            "xbox360" to listOf(
                Emu("aenu.ax360e/aenu.ax360e.EmulatorActivity", action = "aenu.intent.action.AX360E", data = false, extras = uriExtra("game_uri")),
                Emu("aenu.ax360e.free/aenu.ax360e.EmulatorActivity", action = "aenu.intent.action.AX360E", data = false, extras = uriExtra("game_uri")),
                Emu("xendroid.compose/.EmulatorHostActivity"),
                Emu("Ali.Xanite.green/Ali.Xanite.LauncherActivity"),
                Emu("Ali.Xanite/Ali.Xanite.LauncherActivity"),
            ),
        )

        /** Platforms launched through an emulator found by its app name. */
        val byLabel = mapOf("xbox" to "hakux")

        /** Emulators that want their task cleared when a game starts. */
        val freshTask = setOf("come.nanodata.armsx2", "com.armsx2")

        /** Tried by app name when none of a platform's known packages is installed. */
        val fallbackLabels = mapOf("n3ds" to "azahar", "switch" to "eden", "wiiu" to "cemu")

        /** From ES-DE's Android find rules, for the platforms MinimaUI has. */
        val emulators: Map<String, List<String>> = mapOf(
            "switch" to listOf("dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity"),
            "n3ds" to listOf(
                "org.azahar_emu.azahar/org.citra.citra_emu.activities.EmulationActivity",
                "io.github.azahar_emu.azahar/org.citra.citra_emu.activities.EmulationActivity",
            ),
            "psp" to listOf(
                "org.ppsspp.ppsspp/org.ppsspp.ppsspp.PpssppActivity",
                "org.ppsspp.ppssppgold/org.ppsspp.ppsspp.PpssppActivity",
            ),
            "ps2" to listOf(
                "come.nanodata.armsx2/com.armsx2.MainActivity",
                "com.armsx2/com.armsx2.MainActivity",
                "xyz.aethersx2.android/xyz.aethersx2.android.EmulationActivity",
                "xyz.nethersx2.android/xyz.nethersx2.android.EmulationActivity",
                "xyz.aethersx2.android/xyz.aethersx2.android.MainActivity",
                "xyz.nethersx2.android/xyz.nethersx2.android.MainActivity",
            ),
            "wiiu" to listOf("info.cemu.cemu/info.cemu.cemu.emulation.EmulationActivity"),
            "vita" to listOf("org.vita3k.emulator/org.vita3k.emulator.Emulator"),
        )
    }
}
