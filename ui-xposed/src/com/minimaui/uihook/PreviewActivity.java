package com.minimaui.uihook;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Builds a copy of the volume panel's structure the way SystemUI builds it (the same ids, the same layered slider drawable, the same
 * plain white look SystemUI gives it) and runs the hook's own functions on it, so the hook can be seen working, and its field and
 * drawable handling checked, without LSPosed. Three panels: the volume at 100%, 60% and 15%, the ringer closed and open.
 */
public class PreviewActivity extends Activity {
    private static final String TAG = "MinimaUIPreview";

    /** What the hook reads from VolumeDialogImpl, under the same field names. */
    static class FakeImpl {
        Context mContext;
        ViewGroup mDialogView;
        ArrayList<FakeRow> mRows = new ArrayList<>();
        float mRingerDrawerClosedAmount = 1f;
        View mRingerAndDrawerContainer;
    }

    /** What the hook reads from VolumeDialogImpl.VolumeRow. */
    static class FakeRow {
        View view;
        SeekBar slider;
        ImageButton icon;
        TextView number;
        Drawable sliderProgressSolid;
        Drawable sliderProgressIcon;
    }

    /** Like SystemUI's RoundedCornerProgressDrawable: the child is as long as the level, but never shorter than it is high. */
    static class RoundedProgress extends InsetDrawable {
        RoundedProgress(Drawable drawable) {
            super(drawable, 0);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            int w = Math.max(bounds.height(), (int) (bounds.width() * getLevel() / 10000f));
            super.onBoundsChange(new Rect(bounds.left, bounds.top, bounds.left + w, bounds.bottom));
        }

        @Override
        protected boolean onLevelChange(int level) {
            onBoundsChange(getBounds());
            invalidateSelf();
            return true;
        }
    }

    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setBackgroundColor(Color.BLACK);
        row.addView(panel(100, false));
        row.addView(panel(60, true));
        row.addView(panel(15, false));
        setContentView(row);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private int id(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }

    private View panel(int level, boolean ringerOpen) {
        FakeImpl impl = new FakeImpl();
        impl.mContext = this;
        impl.mRingerDrawerClosedAmount = ringerOpen ? 0f : 1f;

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.END);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(dp(170), LinearLayout.LayoutParams.WRAP_CONTENT);
        columnParams.setMargins(dp(20), 0, dp(20), 0);
        column.setLayoutParams(columnParams);
        impl.mDialogView = column;

        // The ringer piece: as wide as the open drawer, SystemUI's plain look is a white circle for the selected mode.
        FrameLayout ringer = new FrameLayout(this);
        ringer.setId(id("volume_ringer_and_drawer_container"));
        ringer.setPadding(dp(7), dp(7), dp(7), dp(7));
        impl.mRingerAndDrawerContainer = ringer;
        FrameLayout drawer = new FrameLayout(this);
        drawer.setId(id("volume_drawer_container"));
        drawer.setBackgroundColor(0x40ffffff);
        drawer.addView(glyph("•", dp(42)), new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.START));
        drawer.addView(glyph("✕", dp(42)), new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));
        drawer.setAlpha(ringerOpen ? 1f : 0f);
        ringer.addView(drawer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        View selection = new View(this);
        selection.setId(id("volume_drawer_selection_background"));
        selection.setBackgroundColor(Color.WHITE);
        selection.setAlpha(ringerOpen ? 1f : 0f);
        ringer.addView(selection, new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.END));
        FrameLayout active = new FrameLayout(this);
        active.setId(id("volume_new_ringer_active_icon_container"));
        active.setBackgroundColor(Color.WHITE);
        ImageView activeIcon = new ImageView(this);
        activeIcon.setId(id("volume_new_ringer_active_icon"));
        activeIcon.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
        activeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xff202020)); // SystemUI's dark panel colour
        active.addView(activeIcon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        ringer.addView(active, new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.END));
        column.addView(ringer, new LinearLayout.LayoutParams(dp(ringerOpen ? 140 : 56), dp(56)));

        // The slider's row, built like volume_dialog_row with volume_row_seekbar.
        FakeRow row = new FakeRow();
        FrameLayout rowView = new FrameLayout(this);
        rowView.setBackgroundColor(0);
        row.view = rowView;
        FrameLayout frame = new FrameLayout(this);
        frame.setClipChildren(false);
        SeekBar slider = new SeekBar(this);
        slider.setBackground(null);
        slider.setThumb(null);
        slider.setPadding(0, 0, 0, 0);
        slider.setSplitTrack(false);
        slider.setRotation(270f);
        slider.setMax(100);
        slider.setProgress(level);
        row.slider = slider;
        frame.addView(slider, new FrameLayout.LayoutParams(dp(192), dp(192), Gravity.CENTER));
        rowView.addView(frame, new FrameLayout.LayoutParams(dp(56), dp(192), Gravity.CENTER));
        GradientDrawable solid = new GradientDrawable();
        solid.setSize(-1, dp(42));
        solid.setCornerRadius(dp(21));
        solid.setColor(Color.WHITE);
        LayerDrawable inner = new LayerDrawable(new Drawable[]{solid});
        inner.setId(0, id("volume_seekbar_progress_solid"));
        GradientDrawable track = new GradientDrawable();
        track.setSize(-1, dp(4));
        track.setColor(0xff404040);
        LayerDrawable progress = new LayerDrawable(new Drawable[]{new InsetDrawable(track, 0), new RoundedProgress(inner)});
        progress.setId(0, android.R.id.background);
        progress.setId(1, android.R.id.progress);
        progress.setLayerGravity(0, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);
        progress.setLayerGravity(1, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);
        slider.setProgressDrawable(progress);
        row.sliderProgressSolid = solid;
        row.icon = new ImageButton(this);
        row.number = new TextView(this);
        impl.mRows.add(row);
        LinearLayout rowsContainer = new LinearLayout(this);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        rowsContainer.setId(id("volume_dialog_rows_container"));
        rowsContainer.setBackgroundColor(0);
        rowsContainer.addView(rowView, new LinearLayout.LayoutParams(dp(56), dp(206)));
        column.addView(rowsContainer, new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout settings = new FrameLayout(this);
        settings.setId(id("settings_container"));
        settings.setBackgroundColor(0x40ffffff);
        settings.addView(glyph("⋯", dp(42)), new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));
        rowsContainer.addView(settings, new LinearLayout.LayoutParams(dp(56), dp(49)));

        // The hook's own functions, in the order SystemUI runs them.
        try {
            VolumeStyle.styleSlider(impl, row);
            VolumeStyle.styleDialog(impl);
            VolumeStyle.styleRows(impl);
            VolumeStyle.tintRow(row);
        } catch (Throwable t) {
            Log.e(TAG, "hook failed", t);
        }
        return column;
    }

    private TextView glyph(String text, int size) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xfff2f2f2);
        t.setTextSize(20);
        t.setGravity(Gravity.CENTER);
        t.setWidth(size);
        t.setHeight(size);
        return t;
    }
}
