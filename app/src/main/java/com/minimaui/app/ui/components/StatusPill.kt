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
import com.minimaui.app.ui.theme.TextSecondary
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
    val batteryPercent = rememberBatteryPercent(context)
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
                text = "$batteryPercent%",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 27.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(16.dp))
            BatteryGlyph(fillFraction = batteryPercent / 100f)
        }
    }
    }
}

@Composable
private fun BatteryGlyph(fillFraction: Float) {
    Canvas(modifier = Modifier.size(46.dp, 24.dp)) {
        val bodyWidth = size.width - 5.dp.toPx()
        val stroke = 2.5.dp.toPx()
        val corner = 5.dp.toPx()

        drawRoundRect(
            color = TextSecondary,
            size = Size(bodyWidth, size.height),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke)
        )
        val capHeight = size.height * 0.4f
        drawRoundRect(
            color = TextSecondary,
            topLeft = Offset(bodyWidth + 1.5.dp.toPx(), (size.height - capHeight) / 2f),
            size = Size(3.dp.toPx(), capHeight),
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        )
        val inset = stroke * 1.6f
        val fillWidth = ((bodyWidth - inset * 2) * fillFraction).coerceAtLeast(0f)
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(inset, inset),
            size = Size(fillWidth, size.height - inset * 2),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }
}

@Composable
private fun rememberBatteryPercent(context: Context): Int {
    var percent by remember { mutableIntStateOf(100) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) percent = level * 100 / scale
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

    return percent
}
