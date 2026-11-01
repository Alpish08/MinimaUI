package com.minimaui.uihook;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * MinimaUI's look for Android's volume panel, as functions on SystemUI's VolumeDialogImpl (read by field name, so they also run on a
 * copy of its structure: see PreviewActivity). Every piece of the panel (the ringer circle, the pill of the slider and the settings
 * button) looks like an off quick tile; the slider's bar and the selected ringer mode look like an on tile, as the brightness bar does.
 * Views are found by their resource id names in the context's own package, which inside SystemUI is SystemUI's.
 */
final class VolumeStyle {
    /** The icons on the dark on-look fill. */
    static final int LIGHT = 0xfff2f2f2;

    private static final Map<String, Field> FIELDS = new HashMap<>();

    private VolumeStyle() {
    }

    /** The panel's static pieces, once SystemUI has built the dialog. */
    static void styleDialog(final Object impl) throws Throwable {
        Context context = (Context) get(impl, "mContext");
        ViewGroup dialogView = (ViewGroup) get(impl, "mDialogView");
        float density = context.getResources().getDisplayMetrics().density;
        int fill = context.getColor(android.R.color.system_neutral2_800);

        View ringer = find(context, dialogView, "volume_ringer_and_drawer_container");
        if (ringer != null) {
            ringer.setBackground(new RingerBackground(fill, density, ringer, new RingerBackground.Closed() {
                @Override
                public float amount() {
                    try {
                        return ((Float) get(impl, "mRingerDrawerClosedAmount")).floatValue();
                    } catch (Throwable t) {
                        return 1f;
                    }
                }
            }));
        }
        View drawer = find(context, dialogView, "volume_drawer_container");
        if (drawer != null) {
            drawer.setBackground(null); // the ringer piece's own pill covers it
        }
        for (String name : new String[]{"volume_drawer_selection_background", "volume_new_ringer_active_icon_container"}) {
            View selected = find(context, dialogView, name);
            if (selected != null) {
                selected.setBackground(new LookDrawable(LookDrawable.ON, fill, density));
            }
        }
        View selectedIcon = find(context, dialogView, "volume_new_ringer_active_icon");
        if (selectedIcon instanceof ImageView) {
            ((ImageView) selectedIcon).setImageTintList(ColorStateList.valueOf(LIGHT));
        }
        // The slider rows and the settings button share one pill: the container that holds both carries it, they carry none.
        View rows = find(context, dialogView, "volume_dialog_rows_container");
        if (rows != null) {
            rows.setBackground(new LookDrawable(LookDrawable.OFF, fill, density));
        }
        View settings = find(context, dialogView, "settings_container");
        if (settings != null) {
            settings.setBackground(null);
        }
        View captions = find(context, dialogView, "odi_captions");
        if (captions != null) {
            captions.setBackground(new LookDrawable(LookDrawable.OFF, fill, density));
        }
    }

    /** A slider's bar: no track of its own (the row's panel is the track), the fill in the on look. */
    static void styleSlider(Object impl, Object row) throws Throwable {
        Context context = (Context) get(impl, "mContext");
        float density = context.getResources().getDisplayMetrics().density;
        int fill = context.getColor(android.R.color.system_neutral2_800);
        SeekBar slider = (SeekBar) get(row, "slider");
        LayerDrawable bar = (LayerDrawable) slider.getProgressDrawable();
        bar.setDrawableByLayerId(android.R.id.background, new ColorDrawable(Color.TRANSPARENT));
        InsetDrawable progress = (InsetDrawable) bar.findDrawableByLayerId(android.R.id.progress);
        LayerDrawable inner = (LayerDrawable) progress.getDrawable();
        int solidId = context.getResources().getIdentifier("volume_seekbar_progress_solid", "id", context.getPackageName());
        Drawable solid = inner.findDrawableByLayerId(solidId);
        inner.setDrawableByLayerId(solidId, new LookDrawable(LookDrawable.ON, fill, density)
                .intrinsic(solid.getIntrinsicWidth(), solid.getIntrinsicHeight()));
    }

    /** The rows have no panel of their own (SystemUI gives them one, or a transparent one, on each update). */
    static void styleRows(Object impl) throws Throwable {
        ArrayList<?> rows = (ArrayList<?>) get(impl, "mRows");
        for (Object row : rows) {
            View view = (View) get(row, "view");
            if (view != null && view.getBackground() != null) {
                view.setBackground(null);
            }
        }
    }

    /** SystemUI tints the slider's icon, the row's icon and the number with the dark panel colour on every update. */
    static void tintRow(Object row) throws Throwable {
        ColorStateList light = ColorStateList.valueOf(LIGHT);
        Object sliderIcon = get(row, "sliderProgressIcon");
        if (sliderIcon instanceof Drawable) {
            ((Drawable) sliderIcon).setTintList(light);
        }
        Object icon = get(row, "icon");
        if (icon instanceof ImageView) {
            ((ImageView) icon).setImageTintList(light);
        }
        Object number = get(row, "number");
        if (number instanceof TextView) {
            ((TextView) number).setTextColor(light);
        }
    }

    /** The view with the resource id [idName] of the context's own package (SystemUI's, inside SystemUI). */
    private static View find(Context context, ViewGroup root, String idName) {
        int id = context.getResources().getIdentifier(idName, "id", context.getPackageName());
        return id == 0 ? null : root.findViewById(id);
    }

    /** A (private) field of [target], searched up its class hierarchy. */
    static Object get(Object target, String name) throws Throwable {
        Class<?> c = target.getClass();
        String key = c.getName() + "#" + name;
        Field field;
        synchronized (FIELDS) {
            field = FIELDS.get(key);
        }
        if (field == null) {
            for (Class<?> k = c; k != null && field == null; k = k.getSuperclass()) {
                try {
                    field = k.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                }
            }
            if (field == null) {
                throw new NoSuchFieldException(key);
            }
            field.setAccessible(true);
            synchronized (FIELDS) {
                FIELDS.put(key, field);
            }
        }
        return field.get(target);
    }
}
