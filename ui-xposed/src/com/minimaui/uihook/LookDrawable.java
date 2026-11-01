package com.minimaui.uihook;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RuntimeShader;
import android.graphics.drawable.Drawable;

/**
 * A pill (or circle) in one of MinimaUI's two Android looks, drawn by one AGSL shader (the same distance-to-edge maths as the launcher's
 * FadingOutline.kt), so the fade is smooth at any size and SystemUI's tinting cannot change it: every tint and colour filter is ignored.
 *
 *  - OFF (what an off quick tile looks like): a flat fill with a wide, dim white rim fading inward.
 *  - ON (what an on tile looks like): a lifted dark interior with a bright rim fading inward, plus a faint white rim over it.
 *
 * These are the numbers of ui-overlay/generate.py (OFF, RIM, ITEM, BAR_RIM_DEPTH), here in dp.
 */
class LookDrawable extends Drawable {
    private static final String AGSL = ""
            + "uniform float2 size;\n"
            + "uniform float radius;\n"
            + "uniform float3 fill;\n"
            + "uniform float4 ramp;\n"   // x: 1 for the on look, y: edge gray, z: interior gray, w: depth in px
            + "uniform float2 ring;\n"   // x: strength, y: depth in px
            + "half4 main(float2 p) {\n"
            + "    float2 h = size * 0.5;\n"
            + "    float2 q = abs(p - h) - (h - float2(radius));\n"
            + "    float outside = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;\n"
            + "    float cov = clamp(0.5 - outside, 0.0, 1.0);\n"
            + "    if (cov <= 0.0) return half4(0.0);\n"
            + "    float d = max(-outside, 0.0);\n"
            + "    float3 base = fill;\n"
            + "    if (ramp.x > 0.5) {\n"
            + "        float t = clamp(d / ramp.w, 0.0, 1.0);\n"
            + "        float g = ramp.z + (ramp.y - ramp.z) * (1.0 - t) * (1.0 - t);\n"
            + "        base = float3(g);\n"
            + "    }\n"
            + "    float tr = clamp(d / ring.y, 0.0, 1.0);\n"
            + "    float a = ring.x * (1.0 - tr) * (1.0 - tr);\n"
            + "    float3 col = mix(base, float3(1.0), a);\n"
            + "    return half4(half3(col * cov), half(cov));\n"
            + "}\n";

    /** The off look: [fill] with a dim rim (strength 0.20, 14 dp). */
    static final int OFF = 0;
    /** The on look: a lifted interior with a bright rim (edge 0.70, interior 0.205, 14 dp) and a faint white rim (0.26, 10 dp). */
    static final int ON = 1;

    private final RuntimeShader shader = new RuntimeShader(AGSL);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int look;
    private final float density;
    private final float fillR, fillG, fillB;
    private int intrinsicW = -1;
    private int intrinsicH = -1;

    LookDrawable(int look, int fillColor, float density) {
        this.look = look;
        this.density = density;
        fillR = ((fillColor >> 16) & 0xff) / 255f;
        fillG = ((fillColor >> 8) & 0xff) / 255f;
        fillB = (fillColor & 0xff) / 255f;
        paint.setShader(shader);
    }

    /** Keeps the layout of the drawable this one replaces (a layer's size comes from its intrinsic size). */
    LookDrawable intrinsic(int width, int height) {
        intrinsicW = width;
        intrinsicH = height;
        return this;
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect b = getBounds();
        drawLook(canvas, b.left, b.top, b.width(), b.height());
    }

    /** Draws the look as a pill of [w] x [h] px at ([left], [top]); the corner radius is half the shorter side. */
    final void drawLook(Canvas canvas, float left, float top, float w, float h) {
        if (w < 2f || h < 2f) {
            return;
        }
        shader.setFloatUniform("size", w, h);
        shader.setFloatUniform("radius", Math.min(w, h) / 2f);
        shader.setFloatUniform("fill", fillR, fillG, fillB);
        if (look == ON) {
            shader.setFloatUniform("ramp", 1f, 0.70f, 0.205f, 14f * density);
            shader.setFloatUniform("ring", 0.26f, 10f * density);
        } else {
            shader.setFloatUniform("ramp", 0f, 0f, 0f, 1f);
            shader.setFloatUniform("ring", 0.20f, 14f * density);
        }
        canvas.save();
        canvas.translate(left, top);
        canvas.drawRect(0f, 0f, w, h, paint);
        canvas.restore();
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicW;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicH;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return paint.getAlpha();
    }

    /** SystemUI tints and filters its drawables with the panel's colours; this look keeps its own. */
    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    /** An invisible pill outline: a view that clips to its outline (the captions toggle) still gets the pill, and no shadow is cast. */
    @Override
    public void getOutline(android.graphics.Outline outline) {
        android.graphics.Rect b = getBounds();
        outline.setRoundRect(b, Math.min(b.width(), b.height()) / 2f);
        outline.setAlpha(0f);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
