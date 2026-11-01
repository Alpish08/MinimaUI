package com.minimaui.uihook;

import android.graphics.Canvas;
import android.view.View;

/**
 * The background of the ringer piece: a circle while the drawer is closed, a pill reaching out from the circle as it opens. SystemUI
 * shrinks its own background drawable's bounds for this, but not on every ROM, so the extent comes from the drawer's closed amount
 * (1 closed, 0 open) and the view's own size instead (the view is always as big as the open drawer). Pinned to the end of the view
 * (the bottom when the panel is portrait).
 */
final class RingerBackground extends LookDrawable {
    interface Closed {
        float amount();
    }

    private final View view;
    private final Closed closed;

    RingerBackground(int fillColor, float density, View view, Closed closed) {
        super(OFF, fillColor, density);
        this.view = view;
        this.closed = closed;
    }

    @Override
    public void draw(Canvas canvas) {
        float vw = view.getWidth();
        float vh = view.getHeight();
        float open = 1f - Math.max(0f, Math.min(1f, closed.amount()));
        if (vw >= vh) {
            float w = vh + open * (vw - vh);
            drawLook(canvas, vw - w, 0f, w, vh);
        } else {
            float h = vw + open * (vh - vw);
            drawLook(canvas, 0f, vh - h, vw, h);
        }
    }
}
