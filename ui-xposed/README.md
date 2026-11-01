# MinimaUI volume panel (LSPosed module)

Gives Android's volume panel MinimaUI's look: the ringer circle, the one pill holding the slider and the settings button look like **off quick tiles**
(flat fill, wide dim fading rim); the slider's bar and the selected ringer mode look like **on tiles** (lifted dark fill, bright fading
rim, light icons), the way the brightness bar looks with `../ui-overlay`.

Why a hook and not another overlay: SystemUI colours the slider's fill with the accent colour and its icons with the dark panel colour
from code, and resets the slider row's background to transparent on every update. A resource overlay cannot change either.

## Use

1. Install `build/MinimaUIVolumeHook.apk` (or `/sdcard/MinimaUI/MinimaUIVolumeHook.apk`).
2. LSPosed manager → Modules → *MinimaUI Volume Panel* → enable, scope **System UI**.
3. Restart System UI (reboot, or LSPosed's "restart" for the scope). Press a volume key.

To undo: switch the module off in LSPosed (and restart System UI). The Magisk overlay's own volume pieces stay as the fallback.

## How it works

- `Hook` (legacy Xposed API, `assets/xposed_init`) hooks `com.android.systemui.volume.VolumeDialogImpl`: `initDialog`, `initRow`,
  `updateRowsH`, `updateVolumeRowTintH`, `updateBackgroundForDrawerClosedAmount`, and the constructor of the ringer-icon colour animator
  (its two colours become light). Everything is wrapped so an exception is only logged (`MinimaUIHook:` in the LSPosed log).
- `VolumeStyle` does the work on SystemUI's views, found by their id names, and reads `VolumeDialogImpl`'s fields by name.
- `LookDrawable` draws a pill with one AGSL shader (the same distance-to-edge maths as the launcher's `FadingOutline.kt`) and ignores
  every tint and colour filter, so SystemUI's tinting cannot undo it. `RingerBackground` is the ringer piece: a circle while the drawer
  is closed, growing into a pill as it opens (from `mRingerDrawerClosedAmount` and the view's size).
- Numbers (dp): off = fill `system_neutral2_800`, rim 0.20 over 14; on = edge 0.70 to interior 0.205 over 14, plus a faint white rim
  0.26 over 10. They are the numbers of `../ui-overlay/generate.py`.
- Always the dark look (as MinimaUI is); written for this ROM's SystemUI (Android 13), where the class and field names are intact.

## Preview without LSPosed

The module's launcher icon, *Volume panel preview*, builds a copy of the panel's structure the way SystemUI does (same ids, same layered
slider drawable, SystemUI's plain white look) and runs the hook's own functions on it: three panels at 100 / 60 / 15 %, ringer closed and
open. If the module does something different in SystemUI, the difference is between this copy and the real thing.

## Build

`./build.sh` (aapt2, javac against `stubs/` for the Xposed API, d8, debug-signed). The stubs are compile-time only; LSPosed provides the real classes.
