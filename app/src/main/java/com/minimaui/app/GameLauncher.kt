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

    /** No slide: MinimaUI fades itself out first, then the game's window simply appears. */
    private val noTransition: android.os.Bundle =
        android.app.ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()

    /** Starts [game]; false (with a toast saying why) if nothing took it. */
    fun launch(game: GameEntry): Boolean {
        val error = when {
            game.shortcutId?.startsWith(GameHubGames.PREFIX) == true ->
                if (startGameHub(game.shortcutId.removePrefix(GameHubGames.PREFIX))) null else "Could not start this GameHub game"
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
        val attempts = listOfNotNull(
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
