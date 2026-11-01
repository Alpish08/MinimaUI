package com.minimaui.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * The volume / brightness bar over other apps: the home screen's label and outlined pill on a translucent bubble panel (rounded
 * bottom, bright rim) that hangs from the top edge and fades to solid black towards it.
 */
class LevelBarView(context: Context) : View(context) {
    private var label = ""
    private var fraction = 0f
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2F2F2")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    // The home screen's background outline (the dim, fading rim of FadingOutline.kt), drawn by the same shader.
    private val outlineShader = RuntimeShader(OUTLINE_AGSL)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { setShader(outlineShader) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val panelShader = RuntimeShader(PANEL_AGSL)
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { setShader(panelShader) }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x4D000000 }
    private val rect = RectF()

    /** How visible the bar is (0..1). Drawn into the canvas, not set as the view's alpha, which a window's root view does not show. */
    var barAlpha = 0f
        set(value) {
            field = value
            invalidate()
        }

    fun set(label: String, fraction: Float) {
        this.label = label
        this.fraction = fraction.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, (width * 400f / 1920f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        if (barAlpha <= 0.002f) return
        val layer = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (barAlpha.coerceIn(0f, 1f) * 255f).toInt())
        // Design pixels (a 1920-wide canvas) scaled to this screen.
        val u = width / 1920f
        val cx = 960f * u
        val barW = 580f * u
        val barH = 36f * u
        val top = (DROP + 72f) * u
        if (canvas.isHardwareAccelerated) {
            // The panel reaches above the screen, so its sides run straight off the top edge; the fade hides that edge.
            val panelW = barW + 2f * PANEL_PADDING * u
            val above = 200f * u
            val panelH = above + top + barH + PANEL_PADDING * u
            panelShader.setFloatUniform("size", panelW, panelH)
            panelShader.setFloatUniform("radius", 44f * u)
            panelShader.setFloatUniform("depth", 14f * u)
            panelShader.setFloatUniform("above", above)
            panelShader.setFloatUniform("fadeLength", (DROP + 136f) * u)
            canvas.save()
            canvas.translate(cx - panelW / 2f, -above)
            canvas.drawRect(0f, 0f, panelW, panelH, panel)
            canvas.restore()
        }

        labelPaint.textSize = 32f * u
        canvas.drawText(label, cx, DROP * u + 22f * u + 32f * u * 0.95f, labelPaint)

        val left = cx - barW / 2f
        rect.set(left, top, left + barW, top + barH)
        canvas.drawRoundRect(rect, barH / 2f, barH / 2f, track)
        if (canvas.isHardwareAccelerated) {
        outlineShader.setFloatUniform("size", barW, barH)
        outlineShader.setFloatUniform("radius", barH / 2f)
        outlineShader.setFloatUniform("depth", 10f * u)
        outlineShader.setFloatUniform("strength", BACKGROUND_OUTLINE)
        canvas.save()
        canvas.translate(left, top)
        canvas.drawRect(0f, 0f, barW, barH, outline)
        canvas.restore()
        }
        if (fraction > 0.004f) {
            val inset = 5f * u
            val innerH = barH - 2 * inset
            val innerW = barW - 2 * inset
            val w = (innerW * fraction).coerceAtLeast(innerH)
            rect.set(left + inset, top + inset, left + inset + w, top + inset + innerH)
            canvas.drawRoundRect(rect, innerH, innerH, fill)
        }
        canvas.restoreToCount(layer)
    }

    private companion object {
        const val DROP = 14f // how far the whole bar sits below where it started (design px); the panel grows by as much
        const val PANEL_PADDING = 46f // bar to panel edge, on the left, right and bottom (design px)
        const val BACKGROUND_OUTLINE = 0.22f // BackgroundOutline in FadingOutline.kt

        // The bubble panel: a 60% black fill with a white rim fading inward, going to solid black (no rim) over the first
        // [fadeLength] px below the top of the screen ([above] is how far the rect reaches over it).
        const val PANEL_AGSL = """
uniform float2 size;
uniform float radius;
uniform float depth;
uniform float above;
uniform float fadeLength;

half4 main(float2 p) {
    float2 h = size * 0.5;
    float2 q = abs(p - h) - (h - float2(radius));
    float outside = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float coverage = clamp(0.5 - outside, 0.0, 1.0);
    if (coverage <= 0.0) return half4(0.0);
    float t = clamp(-outside / depth, 0.0, 1.0);
    float rim = 0.64 * (1.0 - t) * (1.0 - t);
    float f = clamp((p.y - above) / fadeLength, 0.0, 1.0);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(1.0, rim + 0.6 * (1.0 - rim), f) * coverage;
    float white = rim * coverage * f;
    return half4(half3(white), half(a));
}
"""

        // The same distance-to-edge fade as FadingOutline.kt's shader (white, premultiplied).
        const val OUTLINE_AGSL = """
uniform float2 size;
uniform float radius;
uniform float depth;
uniform float strength;

half4 main(float2 p) {
    float2 h = size * 0.5;
    float2 q = abs(p - h) - (h - float2(radius));
    float outside = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float coverage = clamp(0.5 - outside, 0.0, 1.0);
    if (coverage <= 0.0) return half4(0.0);
    float t = clamp(-outside / depth, 0.0, 1.0);
    float a = min(strength * (1.0 - t) * (1.0 - t), 0.99) * coverage;
    return half4(half3(1.0) * half(a), half(a));
}
"""
    }
}
