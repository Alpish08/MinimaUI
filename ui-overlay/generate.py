#!/usr/bin/env python3
"""Writes the overlay drawables: MinimaUI's soft fading outline for Android's own UI.

Every outline is a stack of thin concentric rings whose opacity falls off toward the inside (the same curve as the covers' outline:
strength * (1 - depth fraction)^2), drawn over the item's fill. White rings on dark fills, black rings on light fills.

Three looks, like in MinimaUI:
  ITEM   the gray, unfocused look: tiles, buttons, chips, the brightness bar
  CARD   notification cards
  PANEL  the dim, background look: the power menu box, dialogs, toasts

Tune the constants below, run this script, then build.sh (module) or preview/build.sh (look at it on the device without flashing).

The quick settings tile is special: SystemUI tints that whole drawable with the tile's state colour (Drawable.setTint), so its outline is
built from layers that react to that tint on purpose (see tile_xml()).
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'

ITEM = 0.26    # edge brightness of the gray outline on small items (the covers' unfocused outline is 0.36)
OFF = 0.20     # off tiles, the brightness track: a wide, dim rim
CARD = 0.16    # notification cards: a wide, dim rim
PANEL = 0.24   # background panels and boxes
DARK_ON_LIGHT = 0.30   # the dark outline on white fills (on tiles, the power button, the brightness fill)
DPR = 1.8              # the RP6's density in pixels per dp (288 dpi). Fades are laid out in whole pixels, one band per pixel: the platform
                       # rounds fractional dp insets and stroke widths to pixels, which made bands overlap or leave gaps (visible steps)

OPACITY = 0.85  # the quick settings tiles, notification cards and the brightness bar are this opaque (the rest of the shade shows through)
NIGHT_FILL = "@android:color/system_neutral2_800"
# The night fill with some see-through. system_neutral2_800 is black on this ROM (ColorBlendr), and a colour resource can't take an
# alpha in XML, so the translucent version is plain black at OPACITY. (Day variants keep the theme colour, opaque.)
NIGHT_FILL_A = f"#{round(OPACITY * 255):02X}000000"
DAY_FILL = "@android:color/system_neutral2_10"
FLOATING = "?android:attr/colorBackgroundFloating"
ACCENT = ('<gradient android:startColor="@android:color/holo_blue_light" '
          'android:endColor="@android:color/holo_blue_dark" android:angle="0" />')
PILL = 200     # a radius bigger than half of any item: the item is a pill
ROUND = 37     # the user's corner radius (Iconify) for cards, boxes and chips


def n(x):
    return f"{x:g}"


def px(dp):
    return max(1, round(dp * DPR))


def hexa(a):
    return f"{round(max(0.0, min(1.0, a)) * 255):02X}"


def indent(lines, k):
    return [" " * k + l for l in lines]


def rings(strength, depth, on_dark, radius=PILL, shape="rectangle", extra_insets=None, tint_mode=None):
    """Concentric one-pixel stroke rings, outermost first, fading inward over `depth` dp. `extra_insets` (dp) adds fixed item insets
    (top/bottom...) around all rings."""
    steps = px(depth)
    rgb = "FFFFFF" if on_dark else "000000"
    out = []
    for k in range(steps):
        t = (k + 0.5) / steps
        a = strength * (1 - t) ** 2
        if round(a * 255) < 1:
            continue
        attrs = {"left": k, "top": k, "right": k, "bottom": k}
        for side, v in (extra_insets or {}).items():
            attrs[side] = attrs.get(side, 0) + px(v)
        item_attrs = " ".join(f'android:{sd}="{v}px"' for sd, v in attrs.items())
        tm = f' android:tintMode="{tint_mode}"' if tint_mode else ""
        out.append(f"<item {item_attrs}>")
        out.append(f'    <shape android:shape="{shape}"{tm}>')
        out.append('        <solid android:color="#00000000" />')  # without it a tinted shape is filled black
        out.append(f'        <stroke android:width="1px" android:color="#{hexa(a)}{rgb}" />')
        if shape == "rectangle":
            r = PILL if radius >= PILL else max(radius * DPR - k - 0.5, 0)
            out.append(f'        <corners android:radius="{n(r)}{"dp" if radius >= PILL else "px"}" />')
        out.append("    </shape>")
        out.append("</item>")
    return out


def fill_item(fill, radius, shape="rectangle", size=None, padding=None, insets=None, ident=None):
    """The item holding the fill: a solid colour (@color or #hex) or the accent gradient (fill == 'accent')."""
    attrs = []
    if ident:
        attrs.append(f'android:id="{ident}"')
    for side, v in (insets or {}).items():
        attrs.append(f'android:{side}="{px(v)}px"')
    head = "<item" + ("" if not attrs else " " + " ".join(attrs)) + ">"
    out = [head, f'    <shape android:shape="{shape}">']
    if size:
        out.append("        <size " + " ".join(f'android:{k}="{n(v)}dp"' for k, v in size.items()) + " />")
    if padding:
        out.append("        <padding " + " ".join(f'android:{k}="{n(v)}dp"' for k, v in padding.items()) + " />")
    out.append("        " + (ACCENT if fill == "accent" else f'<solid android:color="{fill}" />'))
    if shape == "rectangle":
        out.append(f'        <corners android:radius="{n(radius)}dp" />')
    out += ["    </shape>", "</item>"]
    return out


def ramp_items(edge, interior, depth, radius=PILL, shape="rectangle"):
    """Nested opaque gray fills from the item's edge inward, one pixel per step: brightness `edge` at the rim fading to `interior`."""
    steps = px(depth)
    out = []
    for k in range(steps + 1):
        t = (k + 0.5) / steps if k < steps else 1.0
        v = f"{round((interior + (edge - interior) * (1 - t) ** 2) * 255):02X}"
        out += [f'<item android:left="{k}px" android:top="{k}px" android:right="{k}px" android:bottom="{k}px">',
                f'    <shape android:shape="{shape}">', f'        <solid android:color="#{v}{v}{v}" />']
        if shape == "rectangle":
            if radius >= PILL:
                out.append(f'        <corners android:radius="{PILL}dp" />')
            else:
                out.append(f'        <corners android:radius="{n(max(radius * DPR - k, 0))}px" />')
        out += ["    </shape>", "</item>"]
    return out


def mask_item(radius, shape="rectangle"):
    out = ['<item android:id="@android:id/mask">', f'    <shape android:shape="{shape}">',
           '        <solid android:color="#00000000" />']
    if shape == "rectangle":
        out.append(f'        <corners android:radius="{n(radius)}dp" />')
    return out + ["    </shape>", "</item>"]


def xml(root_lines):
    return '<?xml version="1.0" encoding="utf-8"?>\n' + "\n".join(root_lines) + "\n"


def layer_list(items, ns=True, stack=False):
    mode = ' android:paddingMode="stack"' if stack else ""
    return [f"<layer-list{' ' + NS if ns else ''}{mode}>"] + indent(items, 4) + ["</layer-list>"]


def ripple(items, extra=""):
    return [f'<ripple {NS} android:color="?android:attr/colorControlHighlight"{extra}>'] + indent(items, 4) + ["</ripple>"]


def inset(inner, **sides):
    attrs = " ".join(f'android:{k}="{n(v)}dp"' for k, v in sides.items())
    return [f"<inset {NS} {attrs}>"] + indent(inner, 4) + ["</inset>"]


def write(module, qualifier, name, content):
    for root in (module, "preview"):
        folder = os.path.join(HERE, root, "res", "drawable" + qualifier)
        os.makedirs(folder, exist_ok=True)
        with open(os.path.join(folder, name + ".xml"), "w") as f:
            f.write(content)


def both(module, name, build):
    """Night (dark) and default (day) variants of a drawable. build(on_dark) returns the XML text."""
    write(module, "-night-v8", name, build(True))
    write(module, "", name, build(False))


# ---------------------------------------------------------------------------------------------------------------------------------
# Quick settings tile. SystemUI tints this whole drawable (Drawable.setTint) with the tile's state colour, and with the user's QS theme
# (Iconify's, which has no offStateColor) the colours are: an inactive tile gets a transparent tint (a transparent filter changes
# nothing, so the drawable's own colours show) and an active tile gets the accent (white). So:
#   * base: the inactive fill, src_atop: unchanged when inactive, white when active;
#   * ramp: nested opaque grays in "multiply" mode: invisible when inactive (a transparent tint multiplies to nothing), and when active
#     tint (white) times gray = that gray: a rim of brightness EDGE fading to the interior brightness INTERIOR over `depth`;
#   * rings: white at a low opacity in both states: the inactive tile's soft outline (and a touch more rim when active).
# EDGE above INTERIOR is a dark tile with a bright fading outline; EDGE below INTERIOR is a light tile with a dark fading outline.
TILE_VARIANTS = {
    "a": dict(edge=0.92, interior=0.17, depth=12.0, text="light"),   # dark tile, bright rim: "on" is the focused look
    "b": dict(edge=0.78, interior=0.24, depth=18.0, text="light"),   # softer, wider rim and a lifted interior
    "c": dict(edge=0.55, interior=0.80, depth=12.0, text="dark"),    # silver tile with a dark rim: still light, but not white
    "d": dict(edge=0.63, interior=0.185, depth=18.0, text="light"),  # B with the interior halfway to A, rim 10% dimmer, then all of it 10% dimmer again: the one chosen
}
TILE_CHOICE = "d"
# An "on" tile stays solid: SystemUI tints it with an opaque white, so under its bands there is always a white base, and translucent bands over
# that would be a lighter, nearly opaque tile anyway (see the README). Only the off look (the base fill) is see-through.
RIM = TILE_VARIANTS[TILE_CHOICE]  # the "on" look, reused by the power button and the brightness fill
BAR_RIM_DEPTH = 14.0  # the brightness bar is 48dp high, so its rim is a little narrower than the tiles'


def tile_xml(edge, interior, depth, **_):
    # The base is the off fill at OPACITY (white when the tile is on); the opaque ramp covers it when on, and is invisible when off.
    items = ["<item>", '    <shape android:tintMode="src_atop">', f'        <solid android:color="{NIGHT_FILL_A}" />',
             f'        <corners android:radius="{PILL}dp" />', "    </shape>", "</item>"]
    steps = px(depth)
    for k in range(steps + 1):
        t = (k + 0.5) / steps if k < steps else 1.0
        g = interior + (edge - interior) * (1 - t) ** 2
        v = f"{round(g * 255):02X}"
        items += [f'<item android:left="{k}px" android:top="{k}px" android:right="{k}px" android:bottom="{k}px">',
                  '    <shape android:tintMode="multiply">', f'        <solid android:color="#{v}{v}{v}" />',
                  f'        <corners android:radius="{PILL}dp" />', "    </shape>", "</item>"]
    items += rings(OFF, 18, True, tint_mode="src_atop")
    return xml(["<!-- Generated by generate.py. See the comment above tile_xml() for how the outline survives SystemUI's tinting. -->"]
               + layer_list(items))


def tile():
    write("systemui", "", "qs_tile_background_shape", tile_xml(**TILE_VARIANTS[TILE_CHOICE]))
    for name, spec in TILE_VARIANTS.items():  # every variant, for the preview app
        path = os.path.join(HERE, "preview", "res", "drawable")
        os.makedirs(path, exist_ok=True)
        with open(os.path.join(path, f"qs_tile_variant_{name}.xml"), "w") as f:
            f.write(tile_xml(**spec))


# ---------------------------------------------------------------------------------------------------------------------------------
def footer():
    def circle(on_dark):
        fill = NIGHT_FILL if on_dark else DAY_FILL
        items = mask_item(PILL) + fill_item(fill, PILL) + rings(ITEM, 10, on_dark)
        return xml(inset(ripple(items), inset=4))

    def circle_color(on_dark):  # the power button: the "on" look (a lit rim on a dark circle), as the on tiles have
        items = mask_item(PILL) + ramp_items(RIM["edge"], RIM["interior"], 10, PILL) + rings(ITEM, 10, True)
        return xml(inset(ripple(items), inset=4))

    def security(on_dark):
        fill = NIGHT_FILL if on_dark else DAY_FILL
        items = mask_item(ROUND) + fill_item(fill, ROUND) + rings(OFF, 14, on_dark, radius=ROUND)
        return xml(inset(ripple(items), insetTop=4, insetBottom=4))

    both("systemui", "qs_footer_action_circle", circle)
    both("systemui", "qs_footer_action_circle_color", circle_color)
    both("systemui", "qs_security_footer_background", security)


def notifications():
    def card(on_dark):
        fill = NIGHT_FILL_A if on_dark else DAY_FILL
        transparent = ["<item>", "    <shape>", '        <solid android:color="#00000000" />', "    </shape>", "</item>"]
        # SystemUI casts the first layer to a GradientDrawable and sets its corner radii, so it stays a plain shape.
        items = transparent + transparent + fill_item(fill, ROUND) + rings(CARD, 16, on_dark, radius=ROUND)
        return xml(ripple(items))

    def manage_button(on_dark):  # "Manage" / "Clear all" in the shade; the invisible last item keeps the button's padding
        fill = NIGHT_FILL_A if on_dark else DAY_FILL
        items = (fill_item(fill, ROUND, insets={"top": 2, "bottom": 2})
                 + rings(ITEM, 12, on_dark, radius=ROUND, extra_insets={"top": 2, "bottom": 2})
                 + ["<item android:top=\"6dp\" android:bottom=\"6dp\" android:start=\"4dp\" android:end=\"4dp\">", "    <shape>",
                    '        <padding android:left="20dp" android:right="20dp" />', '        <solid android:color="#00000000" />',
                    f'        <corners android:radius="{ROUND - 4}dp" />', "    </shape>", "</item>"])
        return xml(layer_list(items))

    both("systemui", "notification_material_bg", card)
    both("systemui", "notif_footer_btn_background", manage_button)


def brightness(preview):
    def bar(on_dark):  # the track: the off tile's fill and outline
        fill = NIGHT_FILL_A if on_dark else DAY_FILL
        track = layer_list(fill_item(fill, ROUND, size={"height": 48}) + rings(OFF, 14, on_dark, radius=ROUND), ns=False)
        progress_tag = ('<clip android:drawable="@drawable/brightness_progress_full_drawable" />' if preview else
                        '<com.android.systemui.util.RoundedCornerProgressDrawable android:drawable="@drawable/brightness_progress_full_drawable" />')
        items = (['<item android:id="@android:id/background" android:gravity="center_vertical|fill_horizontal">']
                 + indent(track, 4) + ["</item>",
                 '<item android:id="@android:id/progress" android:gravity="center_vertical|fill_horizontal">',
                 "    " + progress_tag, "</item>"])
        return xml(layer_list(items))

    def full(on_dark):  # the fill: the "on" tile look (lifted dark with a bright fading rim)
        # Solid, like the on tiles (translucent bands over the translucent track would pile up and look lighter, see the README).
        items = (fill_item(NIGHT_FILL, ROUND, size={"height": 48})
                 + ramp_items(RIM["edge"], RIM["interior"], BAR_RIM_DEPTH, ROUND) + rings(ITEM, 10, True, radius=ROUND))
        return xml(layer_list(items))

    return bar, full


def power_menu():
    def box(on_dark):
        items = fill_item(FLOATING, ROUND) + rings(PANEL, 14, on_dark, radius=ROUND)
        return xml(layer_list(items))

    def button(on_dark):
        fill = NIGHT_FILL if on_dark else DAY_FILL
        items = fill_item(fill, 0, shape="oval") + rings(OFF, 14, on_dark, shape="oval")
        return xml(ripple(items))

    both("systemui", "global_actions_lite_background", box)
    both("systemui", "global_actions_lite_button", button)


def dialogs():
    def background(on_dark):  # the Internet / Bluetooth / other quick settings dialogs
        items = fill_item("?android:attr/colorBackground", ROUND) + rings(PANEL, 14, on_dark, radius=ROUND)
        return xml(layer_list(items))

    both("systemui", "qs_dialog_bg", background)


def toast():
    def frame(on_dark):
        items = fill_item(FLOATING, PILL, padding={"left": 18, "top": 9, "right": 18, "bottom": 9}) + rings(OFF, 14, on_dark)
        # "stack": by default (nest) every layer is inset by the padding of the layers before it, which shrank the rings inside the frame.
        return xml(layer_list(items, stack=True))

    both("android", "toast_frame", frame)


def theme():
    """Text and icons on an "on" tile are drawn in textColorOnAccent / textColorSecondaryInverse, which are dark because the accent is
    white. The "on" tiles are dark now, so those two turn light. The other six items are what Iconify's quick settings style (QSSN2)
    already sets, because this style replaces it. Needs the private attribute names, so this overlay links against the device framework."""
    folder = os.path.join(HERE, "systemui", "res", "values")
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, "styles.xml"), "w") as f:
        f.write('''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.SystemUI.QuickSettings" parent="@*android:style/Theme.DeviceDefault.SystemUI">
        <item name="android:colorBackground">?android:attr/colorBackground</item>
        <item name="android:windowIsFloating">true</item>
        <item name="android:colorPrimary">@android:color/holo_blue_light</item>
        <item name="android:colorAccent">#ffffffff</item>
        <item name="android:colorSecondary">@android:color/holo_blue_dark</item>
        <item name="*android:colorAccentPrimary">#ffffffff</item>
        <item name="*android:textColorOnAccent">#ffffffff</item>
        <item name="android:textColorSecondaryInverse">#b3ffffff</item>
    </style>
</resources>
''')


def main():
    theme()
    tile()
    footer()
    notifications()
    power_menu()
    dialogs()
    toast()
    bar, full = brightness(preview=False)
    both("systemui", "brightness_progress_full_drawable", full)
    # The real module uses SystemUI's own progress class; the preview app swaps in a plain clip drawable.
    for qualifier, on_dark in (("-night-v8", True), ("", False)):
        folder = os.path.join(HERE, "systemui", "res", "drawable" + qualifier)
        open(os.path.join(folder, "brightness_progress_drawable.xml"), "w").write(bar(on_dark))
    pbar, _ = brightness(preview=True)
    for qualifier, on_dark in (("-night-v8", True), ("", False)):
        folder = os.path.join(HERE, "preview", "res", "drawable" + qualifier)
        os.makedirs(folder, exist_ok=True)
        open(os.path.join(folder, "brightness_progress_drawable.xml"), "w").write(pbar(on_dark))
    print("written")


if __name__ == "__main__":
    main()
