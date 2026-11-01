package com.minimaui.qspreview;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Draws the overlay drawables roughly the way SystemUI uses them, on a black "shade", so the look can be judged without flashing.
 * Launch with `--ez night false` for the day variants.
 */
public class MainActivity extends Activity {
    private Context ctx;
    private float d;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean night = getIntent().getBooleanExtra("night", true);
        Configuration cfg = new Configuration(getResources().getConfiguration());
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        ctx = createConfigurationContext(cfg);
        ctx.setTheme(night ? android.R.style.Theme_DeviceDefault : android.R.style.Theme_DeviceDefault_Light);
        d = getResources().getDisplayMetrics().density;

        if ("tiles".equals(getIntent().getStringExtra("mode"))) {
            tileVariants();
            return;
        }
        if ("toast".equals(getIntent().getStringExtra("mode"))) {
            toastTest();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(night ? 0xFF000000 : 0xFFE9E9E9);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout left = column(), right = column();
        root.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        root.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        // Quick settings tiles, tinted the way SystemUI does it with this theme: transparent when off, white when on (text turns light)
        LinearLayout row = new LinearLayout(this);
        row.addView(tile("Wi-Fi\nOff", 0x00000000, 0xFFF2F2F2), tileParams());
        row.addView(tile("Torch\nOn", 0xFFFFFFFF, 0xFFF2F2F2), tileParams());
        left.addView(row);
        row = new LinearLayout(this);
        row.addView(tile("Mode\nOn", 0xFFFFFFFF, 0xFFF2F2F2), tileParams());
        row.addView(tile("Dark\nOff", 0x00000000, 0xFFF2F2F2), tileParams());
        left.addView(row);

        // Notifications
        left.addView(box("Messages  •  new message", "notification_material_bg", 380, 56), margin(8));
        left.addView(box("Android System\nUSB debugging connected\nCharging this device via USB", "notification_material_bg", 380, 112), margin(8));
        left.addView(box("Manage", "notif_footer_btn_background", 130, 44), margin(8));

        // Brightness bar
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setProgressDrawable(drawable("brightness_progress_drawable"));
        bar.setMax(100);
        bar.setProgress(62);
        left.addView(bar, new LinearLayout.LayoutParams(dp(380), dp(48)));
        ((LinearLayout.LayoutParams) bar.getLayoutParams()).topMargin = dp(10);

        // Quick settings footer
        LinearLayout foot = new LinearLayout(this);
        foot.addView(box("1 app is active  >", "qs_security_footer_background", 200, 60), margin(0));
        foot.addView(box("⚙", "qs_footer_action_circle", 60, 60), margin(6));
        TextView power = box("⏻", "qs_footer_action_circle_color", 60, 60);
        power.setTextColor(Color.BLACK);
        foot.addView(power, margin(6));
        right.addView(foot);

        // Power menu
        FrameLayout menu = new FrameLayout(this);
        menu.setBackground(drawable("global_actions_lite_background"));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        buttons.addView(box("⏻", "global_actions_lite_button", 82, 82), margin(8));
        buttons.addView(box("↻", "global_actions_lite_button", 82, 82), margin(8));
        menu.addView(buttons, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(262), dp(130));
        mp.topMargin = dp(10);
        right.addView(menu, mp);

        // Toast
        TextView toast = new TextView(ctx);
        toast.setText("Screenshot saved");
        toast.setTextColor(night ? 0xFFF2F2F2 : Color.BLACK);
        toast.setTextSize(14);
        toast.setBackground(drawable("toast_frame"));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = dp(12);
        right.addView(toast, tp);

        setContentView(root);
    }

    /** The tile "on" variants side by side, tinted the way SystemUI does it here: transparent when off, white when on. */
    private void tileVariants() {
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));
        String[] names = {"a", "d", "c"};
        String[] titles = {"A", "D (chosen)", "C"};
        for (int v = 0; v < 3; v++) {
            LinearLayout col = column();
            TextView head = new TextView(this);
            head.setText(titles[v]);
            head.setTextColor(0xFF9A9A9A);
            head.setTextSize(13);
            col.addView(head);
            boolean darkText = v == 2;
            String[][] rows = {{"Bluetooth", "Off"}, {"JamesDSP", "On"}, {"Airplane mode", "On"}, {"Joystick LED", "Off"}};
            for (int i = 0; i < rows.length; i++) {
                boolean on = rows[i][1].equals("On");
                TextView t = new TextView(ctx);
                t.setText(rows[i][0] + "\n" + rows[i][1]);
                t.setTextSize(15);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setTextColor(on && darkText ? 0xFF111111 : 0xFFF2F2F2);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding(dp(34), 0, 0, 0);
                Drawable bg = getDrawable(getResources().getIdentifier("qs_tile_variant_" + names[v], "drawable", getPackageName())).mutate();
                bg.setTint(on ? 0xFFFFFFFF : 0x00000000);
                t.setBackground(bg);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(300), dp(80));
                p.setMargins(0, dp(8), dp(10), dp(8));
                col.addView(t, p);
            }
            root.addView(col, new LinearLayout.LayoutParams(dp(330), ViewGroup.LayoutParams.MATCH_PARENT));
        }
        setContentView(root);
    }

    /** The toast SystemUI draws (text_toast.xml): 16 dp padding and margin, an icon slot and the text, on the framework's toast_frame. */
    private void toastTest() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF000000);
        LinearLayout toast = new LinearLayout(ctx);
        toast.setGravity(Gravity.CENTER_VERTICAL);
        toast.setBackground(drawable("toast_frame"));
        TextView t = new TextView(ctx);
        t.setText("Screenshot saved");
        t.setTextColor(0xFFF2F2F2);
        t.setTextSize(14);
        t.setPadding(0, dp(12), 0, dp(12));
        toast.addView(t);
        root.addView(toast, new LinearLayout.LayoutParams(-2, -2));
        setContentView(root);
    }

    private int dp(float v) { return (int) (v * d); }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    private LinearLayout.LayoutParams tileParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(180), dp(80));
        p.setMargins(0, dp(4), dp(8), dp(4));
        return p;
    }

    private LinearLayout.LayoutParams margin(int left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.leftMargin = dp(left);
        p.topMargin = left == 8 ? dp(8) : 0;
        return p;
    }

    private Drawable drawable(String name) {
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        return ctx.getDrawable(id).mutate();
    }

    private View label(TextView v) { return v; }

    private TextView tile(String text, int tint, int textColor) {
        TextView v = new TextView(ctx);
        v.setText(text);
        v.setTextColor(textColor);
        v.setTextSize(15);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(30), 0, 0, 0);
        Drawable bg = getDrawable(getResources().getIdentifier("qs_tile_background_shape", "drawable", getPackageName())).mutate();
        bg.setTint(tint);
        v.setBackground(bg);
        return v;
    }

    private TextView box(String text, String drawableName, int w, int h) {
        TextView v = new TextView(ctx);
        v.setText(text);
        v.setTextColor(0xFFF2F2F2);
        v.setTextSize(14);
        v.setGravity(Gravity.CENTER);
        v.setBackground(drawable(drawableName));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), dp(h)));
        return v;
    }
}
