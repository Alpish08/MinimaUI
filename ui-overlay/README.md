# MinimaUI outlines for Android (Magisk module)

SystemUI and framework overlays that give Android's own UI MinimaUI's soft fading outline (the covers' look), over Iconify's styles.
Fills, colours (ColorBlendr), text and icons stay as they are; only backgrounds and outlines change.

| Where | Drawable | Look |
|---|---|---|
| Quick settings tiles | `qs_tile_background_shape` + the QS theme style | off: dark with a gray fading outline; on: a slightly lifted dark tile with a bright fading rim and white text (not white) |
| QS footer buttons, power button, "app is active" chip | `qs_footer_action_circle`, `_circle_color`, `qs_security_footer_background` | gray fading outline; the power button has the "on" rim |
| Notification cards, Manage / Clear all | `notification_material_bg`, `notif_footer_btn_background` | gray fading outline |
| Brightness bar | `brightness_progress_drawable`, `brightness_progress_full_drawable` | track like an off tile; the fill like an on tile (lifted dark, bright fading rim) |
| Power menu | `global_actions_lite_background`, `global_actions_lite_button` | dim box outline, gray button outlines |
| Quick settings dialogs | `qs_dialog_bg` | dim outline |
| Toasts (framework) | `toast_frame` | dim outline |

Not covered: the volume panel (back to stock for now; its slider row is set to a transparent background by SystemUI on every update and its colours come from code, so it needs a hook or another approach), the recents cards (the launcher draws them in code, there is no drawable to replace), the volume slider tracks (SystemUI
looks their layers up by id), the auto-brightness button, switches.

## Opacity
`OPACITY` in `generate.py` (0.85): the off tiles, the notification cards, Manage / Clear all and the brightness track are 85% opaque in dark
mode (`NIGHT_FILL_A`: black, which is what `system_neutral2_800` is on this ROM; a colour resource can't take an alpha in XML). The day
variants keep the opaque theme colour.

The on tiles and the brightness fill stay solid. SystemUI tints the tile drawable with an opaque white when the tile is on, so there is
always a white base under the "on" bands, and bands that are see-through would show it (a lighter tile that is still about 97% opaque).
Nested translucent fills pile their opacity up toward the middle, and the alternative, one-pixel rings, showed bright seams at the rounded
ends (tried, see git history). A drawn bitmap (nine-patch) could get an on tile to roughly 91% at the rim and 97% inside.

## How an "on" tile is drawn
SystemUI tints the tile drawable with the state colour. With Iconify's quick settings style (no `offStateColor`) an off tile gets a
transparent tint (a transparent filter changes nothing, so the drawable's own colours show) and an on tile gets the accent (white).
The drawable uses that: the base fill is the off colour (`src_atop`: unchanged when off, white when on); a ramp of nested gray fills in
`multiply` mode is invisible when off (transparent times gray is nothing) and, when on, is white times gray, i.e. exactly those grays:
a bright rim fading into a dark interior. The on tile's text and icons are drawn in `textColorOnAccent` / `textColorSecondaryInverse`,
dark because the accent is white, so `systemui/res/values/styles.xml` replaces the QS theme style (Iconify's six items, copied, plus
those two turned light). That file needs the private attribute names, so the SystemUI overlay is linked against the device's own
`framework-res.apk` (`build.sh` pulls it into `.cache/`). The look is `TILE_VARIANTS` / `TILE_CHOICE` in `generate.py`.

## Flash
1. Remove or leave off the older "MinimaUI Quick Settings Tiles" module (it replaces the same tile drawable).
2. `./build.sh` makes `build/MinimaUI-Outlines-Magisk.zip` (also copied to `/sdcard/MinimaUI/`).
3. Magisk, Modules, Install from storage, pick the zip, reboot.
4. `service.sh` enables both overlays after boot and puts them above Iconify's styles. Check with
   `adb shell cmd overlay list | grep minimaui` (`[x]` means on).

## Your own screenshot sound (optional)
Put `screenshot.wav` (or `.ogg`, `.mp3`, `.m4a`) in `MinimaUI/Sounds/` on the device before `./build.sh`. The script pulls it, converts it to
Ogg Vorbis and puts it where Android keeps its shutter click (`camera_click.ogg`, the sound a screenshot plays; `/product` and `/system`, wherever
the device has the file), so the module replaces it systemlessly. Or point `SCREENSHOT_SOUND=/path/to/file` at a file on the Mac. Without a file,
the module is built as before. The sound only goes into the zip in `build/`, never into the repository. The real camera's shutter uses the same
sound, so it changes too. If the screenshot still makes the old sound, the firmware plays its own: check with
`adb shell find / -name "camera_click*" 2>/dev/null`.

## Undo
* Magisk, Modules, switch the module off (or remove it), reboot. If SystemUI misbehaves, boot Magisk safe mode (modules off) and remove it.
* To try without it for one boot: `su -c "cmd overlay disable com.minimaui.overlay.systemui"` (it comes back at the next boot).

## Why the fades are drawn in whole pixels
The platform rounds fractional dp insets and stroke widths to pixels, so bands laid out in 0.5 dp steps overlapped or left gaps and the
fade showed as three or four visible rings. `generate.py` now draws one band per pixel (`DPR` is the RP6's 1.8 px per dp), which gives a smooth fade.

## Tune the look
Edit the constants at the top of `generate.py` (edge brightness of the three looks, band width), then rebuild the module, or look at the
result on the device without flashing: `preview/build.sh`, `adb install -r preview/build/QSPreview.apk`, open "QSPreview"
(`adb shell am start -n com.minimaui.qspreview/.MainActivity --ez night false` for the day variants).

## Notes for changing things
* Never reference private framework resources or attributes from an overlay: their numeric ids differ between Android versions. Public
  ones (`@android:color/system_neutral2_800`, `?android:attr/colorBackgroundFloating`) are fine.
* Keep a drawable's structure when SystemUI inspects it: `notification_material_bg` must stay a ripple whose first layer is a plain shape
  (SystemUI sets its corner radii); the brightness bar keeps `@android:id/background` and `@android:id/progress`.
* A shape with a stroke but no `<solid>` is filled black when a tint is applied: every ring has `<solid android:color="#00000000" />`.
* The tile drawable is tinted as a whole (`setTint`), hence its tint-proof layers: see `tile()` in `generate.py`.
