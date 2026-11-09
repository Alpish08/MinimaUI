package com.minimaui.app.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private const val DockedExtra = 60f
// Where the screen's right edge falls inside the docked pill, and where its fade begins, in design px from its left.
private const val EdgeInPill = 455f
private const val FadeFromInPill = 335f

/** Fades the content toward [EdgeInPill] (the screen's right edge when docked); [amount] 0 = no fade. */
private fun Modifier.fadeTowardScreenEdge(amount: () -> Float): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val t = amount().coerceIn(0f, 1f)
        if (t > 0f) {
            val from = FadeFromInPill.dp.toPx() / size.width
            val to = (EdgeInPill.dp.toPx() / size.width).coerceAtMost(1f)
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Black,
                    from to Color.Black,
                    to to Color.Black.copy(alpha = 1f - t),
                    1f to Color.Black.copy(alpha = 1f - t)
                ),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
            )
        }
    }

/** One pill holding the live clock (left) and battery level (right). */
@Composable
fun StatusPill(modifier: Modifier = Modifier, docked: () -> Float = { 0f }) {
    val context = LocalContext.current
    // The wait below does not tick while the device sleeps, so the time is read again (and the wait restarted) whenever the screen
    // turns on or the clock or time zone changes.
    var restart by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) { restart++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val now by produceState(initialValue = Date(), restart) {
        while (true) {
            val nowMillis = System.currentTimeMillis()
            value = Date(nowMillis)
            // Only minutes are shown, so wake once per minute, right after the minute rolls over.
            delay(60_000L - nowMillis % 60_000L + 50L)
        }
    }
    val battery = rememberBattery(context)
    val timeFormat = remember { SimpleDateFormat("h:mm", Locale.getDefault()) }
    val amPmFormat = remember { SimpleDateFormat("a", Locale.getDefault()) }

    // Docked (1): the pill's right side runs off the screen edge, so the clock and battery stay in a compact
    // group measured from the pill's left, instead of spreading toward the hidden right end.
    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                // Longer on the right when docked: that end runs past the screen edge.
                val width = (Dimens.StatusPillWidth.value + DockedExtra * docked().coerceIn(0f, 1f)).dp.roundToPx()
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, Dimens.StatusPillHeight.roundToPx()))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
    ) {
    // The pill itself; docked, its background fades out toward the screen's right edge.
    Box(
        Modifier
            .fillMaxSize()
            .fadeTowardScreenEdge { docked() }
            .glassPill(Dimens.StatusPillHeight / 2)
    )
    Row(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .layout { measurable, constraints ->
                // The clock and battery keep the same spacing wherever the pill is.
                val start = 30.dp.roundToPx()
                val width = 380.dp.roundToPx()
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, constraints.maxHeight))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(start, 0) }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(timeFormat.format(now)) }
                append(" ")
                append(amPmFormat.format(now).lowercase(Locale.getDefault()))
            },
            color = TextPrimary,
            fontSize = 33.sp,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${battery.percent}%",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 27.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(16.dp))
            BatteryGlyph(fillFraction = battery.percent / 100f, charging = battery.charging)
        }
    }
    }
}

/**
 * The battery as Iconify's landscape style "O" draws it (the one used in the status bar): a rounded outline with a small
 * cap, the level as a rounded bar inside it, and while [charging] (plugged in) a bolt over the middle with a clear gap around
 * it that cuts through the bar and the outline. The shapes are Iconify's own, on its 20 x 12 grid.
 */
@Composable
private fun BatteryGlyph(fillFraction: Float, charging: Boolean) {
    // The gap around the bolt clears what is under it, so the glyph is drawn in a layer of its own.
    Canvas(modifier = Modifier.size(45.dp, 27.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val toCanvas = android.graphics.Matrix().apply { setScale(size.width / BatteryGridWidth, size.height / BatteryGridHeight) }
        fun shape(data: String) = androidx.core.graphics.PathParser.createPathFromPathData(data).apply { transform(toCanvas) }
        val outline = shape(BatteryOutlinePath).asComposePath()
        val bolt = shape(BatteryBoltPath).asComposePath()
        val fillBounds = android.graphics.RectF().also { shape(BatteryFillPath).computeBounds(it, true) }

        drawPath(outline, BatteryOutlineColor)
        // The level: the fill area cut off at the level, its corners rounded by 2 px (a full battery reaches one past the edge).
        val level = fillFraction.coerceIn(0f, 1f)
        val right = if (level >= 1f) fillBounds.right + 1f else kotlin.math.floor(fillBounds.left + fillBounds.width() * level)
        if (right > fillBounds.left) {
            drawRoundRect(
                color = TextPrimary,
                topLeft = Offset(fillBounds.left, fillBounds.top),
                size = Size(right - fillBounds.left, fillBounds.height()),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
        if (charging) {
            drawPath(bolt, TextPrimary)
            // The gap: a stroke around the bolt, kept off the bolt itself, that clears the bar and the outline under it.
            val gap = maxOf(size.width / BatteryGridWidth * 2f, 3f)
            clipPath(bolt, ClipOp.Difference) {
                drawPath(bolt, Color.Black, style = Stroke(width = gap, join = StrokeJoin.Round), blendMode = BlendMode.Clear)
            }
        }
    }
}

// Iconify's landscape battery "O" (its config_landscapeBattery*PathO strings), on a 20 x 12 grid.
private const val BatteryGridWidth = 20f
private const val BatteryGridHeight = 12f
private val BatteryOutlineColor = Color.White.copy(alpha = 0.35f)
private const val BatteryOutlinePath = "M3.18,0.43C2.836,0.449 2.527,0.527 2.227,0.676C1.977,0.797 1.77,0.938 1.578,1.121C1.168,1.512 0.914,2.004 0.824,2.59C0.805,2.699 0.805,2.867 0.805,5.996C0.805,9.484 0.805,9.324 0.852,9.566C0.969,10.156 1.316,10.703 1.793,11.063C1.938,11.176 2.063,11.246 2.234,11.332C2.547,11.48 2.828,11.551 3.199,11.57C3.453,11.582 14.648,11.582 14.902,11.57C15.289,11.551 15.586,11.473 15.914,11.305C16.656,10.93 17.137,10.266 17.277,9.422C17.289,9.328 17.293,9.215 17.297,8.598L17.301,7.879L18.035,7.879C18.719,7.879 18.77,7.879 18.84,7.859C19.012,7.816 19.141,7.684 19.184,7.512C19.199,7.465 19.199,7.258 19.199,6C19.199,4.723 19.199,4.535 19.184,4.484C19.141,4.309 18.988,4.168 18.813,4.133C18.777,4.125 18.523,4.121 18.031,4.121L17.301,4.121L17.301,3.473C17.301,2.836 17.297,2.695 17.266,2.516C17.156,1.891 16.809,1.313 16.305,0.934C16.156,0.824 16.051,0.758 15.887,0.68C15.555,0.52 15.277,0.449 14.895,0.43C14.695,0.418 3.383,0.422 3.18,0.43ZM14.879,1.863C15.074,1.91 15.238,1.984 15.375,2.086C15.633,2.277 15.805,2.555 15.867,2.883C15.883,2.969 15.887,3.176 15.887,6C15.887,8.824 15.883,9.031 15.867,9.117C15.816,9.387 15.699,9.609 15.516,9.797C15.309,10 15.07,10.113 14.77,10.148C14.652,10.164 3.445,10.164 3.328,10.148C3.031,10.113 2.789,10 2.586,9.793C2.398,9.609 2.281,9.387 2.23,9.117C2.215,9.031 2.215,8.824 2.215,6C2.215,3.176 2.215,2.969 2.23,2.883C2.332,2.359 2.719,1.961 3.227,1.867C3.27,1.859 3.313,1.848 3.324,1.848C3.336,1.844 5.926,1.844 9.074,1.844C14.563,1.844 14.809,1.848 14.879,1.863ZM14.879,1.863"
private const val BatteryFillPath = "M4.379,3.262C4.215,3.273 4.07,3.336 3.934,3.441C3.805,3.547 3.707,3.691 3.656,3.867L3.637,3.945L3.637,6C3.637,7.965 3.637,8.059 3.652,8.121C3.719,8.344 3.848,8.516 4.035,8.629C4.145,8.691 4.254,8.73 4.387,8.738C4.52,8.754 13.621,8.754 13.754,8.738C13.887,8.73 13.992,8.691 14.105,8.629C14.289,8.52 14.422,8.348 14.48,8.133L14.504,8.055L14.504,3.945L14.48,3.867C14.434,3.691 14.336,3.547 14.207,3.445C14.059,3.328 13.934,3.277 13.75,3.262C13.613,3.246 4.512,3.25 4.379,3.262ZM4.379,3.262"
private const val BatteryBoltPath = "M8.801,2.09C8.672,2.129 8.551,2.207 8.465,2.313C8.434,2.352 8.191,2.816 7.578,4.043C7.117,4.965 6.727,5.758 6.711,5.797C6.688,5.867 6.684,5.891 6.684,6C6.684,6.109 6.688,6.133 6.715,6.207C6.758,6.34 6.84,6.449 6.945,6.523C7.023,6.578 7.09,6.613 7.172,6.637C7.23,6.652 7.297,6.656 8.391,6.656L9.551,6.66L8.961,7.84C8.637,8.488 8.359,9.047 8.348,9.082C8.328,9.133 8.324,9.168 8.324,9.27C8.324,9.383 8.328,9.402 8.355,9.484C8.418,9.68 8.559,9.824 8.758,9.898C8.848,9.93 9.031,9.941 9.129,9.914C9.266,9.883 9.406,9.789 9.492,9.68C9.543,9.613 11.207,6.301 11.242,6.195C11.273,6.094 11.273,5.906 11.242,5.805C11.16,5.559 10.953,5.387 10.695,5.352C10.645,5.344 10.246,5.34 9.508,5.34L8.398,5.34L8.988,4.16C9.313,3.512 9.59,2.953 9.602,2.918C9.629,2.828 9.637,2.688 9.617,2.59C9.563,2.348 9.387,2.156 9.148,2.09C9.055,2.063 8.895,2.063 8.801,2.09ZM8.801,2.09"

private data class BatteryState(val percent: Int, val charging: Boolean)

@Composable
private fun rememberBattery(context: Context): BatteryState {
    var percent by remember { mutableIntStateOf(100) }
    var charging by remember { androidx.compose.runtime.mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) percent = level * 100 / scale
                if (intent != null) charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sticky?.let { receiver.onReceive(context, it) }
        onDispose { context.unregisterReceiver(receiver) }
    }

    return BatteryState(percent, charging)
}
