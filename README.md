# MinimaUI
A minimalist frontend for android handhelds. 

***Minimalist Aesthetics -***  Focused to help with "choice paralysis." A simple home for your retro games with fluid animations and a polished UI, designed to end mindless scrolling through your massive game library, just click-and-play!

# Screenshots
<p>
  <img src="screenshots/favorites.png" width="49%" alt="Favorites">
  <img src="screenshots/all-games.png" width="49%" alt="All Games">
</p>
<p>
  <img src="screenshots/apps.png" width="49%" alt="Apps">
  <img src="screenshots/search-keyboard.png" width="49%" alt="Search with the on-screen keyboard">
</p>
<p>
  <img src="screenshots/launcher-menu.png" width="49%" alt="The launcher menu">
  <img src="screenshots/ambient-clock.png" width="49%" alt="The ambient clock when idle">
</p>

# Minimal Load on the Battery
No -
* Online scraping,
* Discord integration,
* RetroAchievements support,
* Background processes.

When nothing has been touched for 30 seconds the focus glow and the scrolling titles stop, the wave stops, the screen dims and an analog clock fades in (with the Idle Clock turned off it just dims, to 20% of your brightness instead of 40%); the first button press only wakes it.

# Features
* **Favorites**: a bento of your favorite games and apps, with folders and a recently played view.
* **All Games**: an XMB-style row of platforms with a column of covers under each one (16:9 covers).
* **Apps**: your apps in pages, separate from the games, placed in any slot you like, with Vita-style bubble folders (see [Apps and folders](#apps-and-folders)).
* **Search** (L2 + R2): type with MinimaUI's own on-screen keyboard (letters and a number pad), or with the touch screen.
* **Edit Mode** (from the Customization menu, or hold a cover or an app icon, or hold **Select** on one): rearrange favorites, platforms and apps; remove games from the library (and bring them back from the *Hidden Games* page of *Edit Current Platform*, in Edit Mode on All Games).
* **Per-game and per-platform emulators**: pick any installed app as the emulator, or go back to the original.
* **Platform tiles** show the platform's logo from `MinimaUI/Platform Icons/` when it has one (named like the console, e.g. `Sony Playstation.png`), otherwise its name as text; give a platform its own title or image if you like. A platform shows on All Games while it has games (Android and Steam always); switch any platform on or off by hand in Customization (in Edit Mode on All Games or Favorites).
* An optional animated XMB wave with its own blur and brightness on each page, background music, UI sounds, your own boot animation, settings backups, and an ambient clock.
* Touch, buttons or both.

# Supported platforms and emulators (for now)
More platforms and emulators will be added as feedback comes in, so please tell me what you use.

| Platform | Games | Launched with |
|---|---|---|
| Android | apps you add by hand | the app itself |
| Steam | games you add by hand (title + GameHub id) | GameHub |
| Windows | `.art` files, or shortcuts pinned from Winlator / GameHub | Artemis, the pinned shortcut |
| Nintendo Switch | `.nsp .xci .nsz .xcz .nro` | Eden |
| Nintendo 3DS | `.cci .3ds .cia` | Azahar |
| Wii U | `.wux .wud .wua` | Cemu |
| PSP | `.iso .cso .pbp` | PPSSPP / PPSSPP Gold |
| PS2 | `.chd .iso` | ArmSX2 / AetherSX2 / NetherSX2 |
| PS Vita | `.psvita` (a text file holding the game's title id) | Vita3K |
| Xbox | `.xiso .iso` | HakuX |
| PS1 | `.chd .cue .pbp .iso .m3u` | DuckStation |
| PS3 | `.iso`, or a `.ps3` file named after the game's serial and holding it (an installed game) | aPS3e, ARMSX3 |
| Xbox 360 | `.iso .xex .zar` | aX360e, XenDroid, Xenra |
| GameCube | `.iso .gcm .rvz .gcz .ciso .wia .tgc .dol` | Dolphin |
| Wii | `.iso .wbfs .rvz .wia .wad .gcz .ciso .dol` | Dolphin |
| Nintendo 64 | `.n64 .z64 .v64 .ndd .zip` | Mupen64Plus FZ |
| Nintendo DS | `.nds .dsi .zip` | melonDS, DraStic, NooDS |
| Game Boy / Color / Advance | `.gb .gbc .gba .zip` | Pizza Boy, My Boy / My OldBoy, GBA.emu / GBC.emu |
| SNES (and NES, in the same folder) | `.sfc .smc .swc .fig .bs` and more; `.nes .fds .unf`; `.zip` | Snes9x EX+ / NES.emu / Nesoid, or RetroArch (Snes9x and FCEUmm cores) |
| Dreamcast | `.gdi .cdi .chd .cue .iso .m3u` | Flycast, Redream |

The PS1, PS3, Xbox 360, GameCube, Wii, N64, DS, Game Boy, SNES / NES and Dreamcast entries come from ES-DE's published launch rules and have **not been tried on a device yet**: if one doesn't start, please open an issue with the emulator and its version.

You can send any ROM game to a different emulator: *Start menu → Edit Current Game → Custom Emulator* (one game), or in the platform Edit Mode *Edit Current Platform → Default Emulator* (the whole platform). Press X on either to go back to the original.

MinimaUI does not include any games, BIOS files, emulators or artwork of the platforms. You bring your own.

# Installation
1. **Requirements**: Android 13 or newer, a landscape 16:9 screen (made on a Retroid Pocket 6).
2. **Install the APK** from the [Releases](../../releases) page (allow "install unknown apps" for your browser or file manager).
3. **Open MinimaUI** and allow **All files access** when asked. MinimaUI needs it to read your ROM folders and to keep its own `MinimaUI/` folder.
4. **Make it your home app** (optional): Android Settings → Apps → Default apps → Home app → MinimaUI. Pressing Home then returns to it.
5. **Add your ROM folders**: press **Start** → **ROMs Folder(s)** → the **+** pill, then pick the folder that holds your platform folders. Inside it, name one folder per platform: `PSP`, `PS1`, `PS2`, `PS3`, `Switch`, `3DS`, `WiiU`, `Wii`, `GameCube`, `N64`, `DS`, `GBA` (or `GB`, `GBC`, `Game Boy`), `SNES` (or `NES`), `Dreamcast`, `Vita` (or `PSV`), `Xbox`, `Xbox360`, `Android`, `Artemis` (the Windows `.art` files). Names are not case sensitive and spaces are ignored (`Nintendo Switch` works). Press **Y** on that screen to rescan whenever you add games.
6. **Add Android games and apps**: turn on **Edit Mode** (Start → Customization), then on All Games the Android and Steam rows end with a **+** (Steam games take a title and their GameHub id), and on the Apps page press **A** on an empty slot to pick an app.

# Getting around (no first-time screen needed)
| Button | What it does |
|---|---|
| D-pad | move |
| **A** | launch / enter |
| **B** | back |
| **Y** | Favorites ⇄ All Games |
| **X** | Apps ⇄ games |
| **L1 / R1**, right stick | turn pages (on All Games, L1 / R1 jump to the first / last game) |
| **L2 + R2** | search |
| **Start** (tap) | the menu |
| **Start** (hold) | Android's own settings |
| **Select** (tap) | details card of the focused game (time played and more) |
| **Select** (hold) | Edit Mode |
| **M1 / M2** | send a ripple through the wave |

The **Start menu** has four pages:
* **Edit Current Game** (or Platform / App): title, cover image, favorite, folder, custom emulator and remove from library (for a platform: icon, title and default emulator, and a second page listing the hidden games).
* **Customization**: XMB wave, touch, **Edit Mode**, UI smoothness (60 / 120 Hz), **Boot Animation**, **Idle Clock**, sounds, music and volume, and (in Edit Mode on All Games or Favorites) which platforms are shown.
* **ROMs Folder(s)**: your ROM folders (**X** removes one, **Y** rescans).
* **Backup / Restore**: save and restore all settings.

The keyboard: **A** types, **X** deletes, **Y** is space, **Start** is Enter, **L** is Caps (or the Shift key), **R** switches between the letters and the number pad, **B** cancels.

# The MinimaUI folder
MinimaUI creates these folders on internal storage (`/sdcard/MinimaUI/`) itself the first time it runs with *All files access*:

```
MinimaUI/
  Backup/            settings backups (and Custom Wave/ for saved wave looks)
  GameID/            gamehub_games.txt  ->  one game per line: Title | id   (edit it by hand if you like)
  Music/             your own background music; pick a track in Customization, or set Background Music to SHUFFLE to play every file in here
  Sounds/            your own UI sounds (see below)
  Cover Art/         put your covers here and pick them from the menus
    Favorites/
    All Games/<platform>/
  Platform Icons/    optional: your own platform images; a platform takes the one named after it (see the console-logos names below) until you pick another
  Logo.png           optional: your own logo for the bottom of Favorites
```

**Sounds**: drop files named like these in `Sounds/` (`.wav`, `.ogg`, `.mp3` or `.m4a`); any that are missing fall back to Android's click:
`navigation`, `settings_menu_navigation`, `menu`, `edit_mode`, `error`, `page_switch`, `app_launch`, `game_launch`, `app_exit`, `game_exit`, `cancel_confirm`, `keyboard`, `toast_menu`, `mode_switch`. `settings_menu_navigation` is for opening the settings menu and the search and for switching between Favorites, All Games and Apps, `menu` for opening the launcher (nine-dot) menu (falls back to `settings_menu_navigation`), `edit_mode` for entering Edit Mode and `error` for a game that won't launch or a failed backup (both silent when missing), `mode_switch` for the keyboard's ABC / 123 switch, and `cancel_confirm` for choosing an item in a menu.

Sounds are not included. If you want a PS Vita feel, the community-made [PS Vita SFX pack on DeckThemes](https://deckthemes.com/packs/view?themeId=9dab732c-e05c-4f4b-a6b4-c648a8c144d3) has the system sounds, including ones MinimaUI doesn't use. Its files are named differently from the list above, so listen to them, pick the ones you like, copy them into `Sounds/` and rename each one to the name MinimaUI expects. That pack is made by someone else and I'm not affiliated with it or with Sony; the sounds belong to their owners, so use them for your own device only.

**Boot animation**: *Customization → Boot Animation* set to CUSTOM opens a file picker; choose a video (any format Android can play, a 16:9 one fills the screen) and it plays instead of the logo every time MinimaUI starts (a button or a tap skips it). Set it back to DEFAULT to forget the video.

**The logo** at the bottom of Favorites is the MinimaUI logo. To use your own, put a PNG at `MinimaUI/Logo.png` (about 5:1 wide, light on transparent works best) and restart MinimaUI. A proper logo picker will come later.

**Covers and platform icons** are not bundled: pick your own images with *Select Cover Image* and *Edit Current Platform → Select Platform Icon*. MinimaUI never downloads artwork or metadata.

**Platform icons**: tiles show the platform's name as text until you pick an image. A big collection of console logos (many more consoles than MinimaUI supports yet) is in the [console-logos repository](https://github.com/PRO100BYTE/console-logos/tree/main/Recommended%20Versions%20%28Normal%29%20%281%20Per%20Platform%29%20v2.1). Download the ones you want (check that repository for its terms), put them in `MinimaUI/Platform Icons/`, then in Edit Mode on the All Games page open the Start menu → *Edit Current Platform → Select Platform Icon* and pick the image. The logos belong to their owners.

**Steam launchers**: besides GameHub, a Steam game can be started by GameNative (by its Steam App ID) or GameHub Lite: choose the app as the game's *Custom Emulator*, or for the whole Steam platform in Edit Mode (*Edit Current Platform → Default Emulator*). Games pinned from Moonlight ("Add to home screen") show up under Windows, like the Artemis ones. Neither launcher has been tried on a device yet.

# Apps and folders
Apps and folders sit in any slot of the Apps page (rows of 3, 4 and 3); gaps stay where you leave them. In **Edit Mode**:
* **A** picks the focused app or folder up and carries it: the cursor moves it while everything else stays put, and the one in the slot under it is shown where yours came from. **A** puts it down (the two swap), **B** puts it back. Near the top or bottom (touch) or with **L1 / R1** it moves to another page; there is always a blank page at the end.
* **A** on an empty slot opens the app picker; the apps you choose go there.
* **X** takes an app off the page, deletes a folder (its apps go back to the page) or, inside a folder, moves an app back to the page.
* **Y** opens a folder.

**Folders** are bubbles that show the apps inside in small. Make and fill them from the Start menu: *Edit Current App → Add To Folder* (pick a folder, or **+** for a new one named with the on-screen keyboard; it takes the app's place). In a folder the entry is *Remove From Folder*; on a bubble the menu offers *Rename Folder* and *Delete Folder*. A folder holds ten apps, in the same layout. **A** opens it, **B** (or a tap outside) closes it.

# The wave
The first time you run MinimaUI the XMB wave is on, in the author's own look. *Start → Customization → XMB Wave* switches it between OFF, ORIGINAL (the original wave) and CUSTOM (the MinimaUI wave).

With CUSTOM selected, press **X** on that row to **Customize** it. There is a page for each of All Games, Apps and Favorites, each with a **Blur** and a **Brightness** slider and a preview of the wave. At 0% brightness the wave isn't drawn behind that page at all, which also saves battery. Turn the pages with **L1 / R1**, the right stick, a swipe or the page dots. **X** rolls a new random wave, and **Y** saves the current one as a file in `MinimaUI/Backup/Custom Wave/`; restore one from *Backup / Restore*. The files are plain `key = value` text, so you can also fine-tune them by hand.

Out of the box the wave is the author's own: sharp behind All Games, blurred and dimmer behind Apps, and at half brightness behind Favorites, where the dock becomes frosted glass over it. It fades out toward the bottom of the screen.

# Optional extras
* **Volume and brightness bars** over other apps: MinimaUI has an accessibility service for this (Android Settings → Accessibility → MinimaUI). It's off until you enable it, and nothing else needs it. The same service also watches the Home button (that one key only, nothing else you press) so that **holding Home for 5 seconds restarts MinimaUI**, which replays the boot animation.
* **[ui-overlay](ui-overlay)**: a Magisk module that gives Android's own quick settings, notifications and power menu MinimaUI's soft fading outline. Only for rooted devices, and entirely optional. See its README.

<p>
  <img src="screenshots/android-overlays.png" width="49%" alt="Android's quick settings and notifications with the ui-overlay module">
  <img src="screenshots/volume-bar-over-a-game.png" width="49%" alt="The volume bar over a running game">
</p>

# Future Plans
* More platforms and emulators (and a menu to add your own)
* More emulator intents
* A logo picker (instead of the MinimaUI logo)
* Original default BGM
* UI polish
* Dual-screen & non 16:9 display support
* And many more yet to come!

# Feedback
Bugs, ideas, or an emulator or platform you'd like to see: open an [issue](../../issues). MinimaUI is a personal project, so I don't take code contributions; see [CONTRIBUTING.md](CONTRIBUTING.md).

# Credits
* The custom boot animation: idea and first prototype by [Yexclsv25](https://github.com/Yexclsv25).

# License
MinimaUI is **source-available** under the **PolyForm Strict License 1.0.0**. The code is public so you can see exactly what the app does (it needs *All files access*, and has no network features), and you can use it for any noncommercial purpose, but you may not change it, distribute it or make new works based on it. See [LICENSE](LICENSE).

Versions 2.0 and 2.0.1 were released earlier under the GNU General Public License v3.0; those two releases stay available under that license for anyone who has them. Everything after 2.0.1 is under PolyForm Strict.

# Disclaimer
* Artificial Intelligence was used in the making of this frontend. I am not familiar with Android Studio, so Claude Code makes the backend for me. However, since Kotlin (coding language used for this app) is similar to Java, which I have experience with, I optimize the code to the best of my abilities. UI design was 100% done by me, no AI was used for assets.
* All platform, emulator and game names are trademarks of their owners. MinimaUI is not affiliated with any of them.
