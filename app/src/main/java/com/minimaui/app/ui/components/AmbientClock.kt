package com.minimaui.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaui.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

// Everything is in design pixels (1 dp is 1 px of the 1920 x 1080 canvas).
private const val CenterX = 960f
private const val CenterY = 540f
// The face is drawn at this scale of its first size.
private const val Scale = 1.2f
private const val Radius = 270f * Scale

// The dimmed, round-faded patch behind the clock (the boot logo's, a little lighter so the disc can show what is under it).
// Where the vignette starts, as a share of the distance from the centre to the corner.
private const val VignetteStart = 0.45f
private const val PatchRadius = 760f
// A fifth more opaque than before (0x5C).
private val Glass = Color(0x6E181818)
// The disc's soft outline: the same fading glow as everywhere else, reaching far in.
// The brighter, shallower fade next to the white edge.
private const val EdgeGlowDepth = 56f * Scale
private const val EdgeGlowStrength = 0.5f
private const val RimDepth = 170f * Scale
// The same dim, grey strength as the other background outlines.
private const val RimStrength = BackgroundOutline
private val MinuteRed = Color(0xFFE5483F)
private val Hub = Color(0xFF9B9B9B)

/** Moves the face a few pixels every minute, so no pixel stays lit in the same place for hours (the screen is an OLED). */
private fun shiftFor(minuteOfDay: Int): Offset {
    val x = (minuteOfDay * 5 % 9 - 4) * 3f
    val y = (minuteOfDay * 7 % 9 - 4) * 3f
    return Offset(x, y)
}

private fun currentMinuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

/**
 * The ambient clock shown over the dimmed screen: a glass disc with the hours (numbers at 12, 3, 6 and 9, short bars between),
 * a white hour hand, a long red minute hand and a grey hub. It is as cheap as an analog clock can be: nothing animates, and
 * the face is only redrawn once a minute (there is no second hand), when it also moves a little against burn-in.
 */
@Composable
fun AmbientClock(visible: Boolean, fade: Animatable<Float, AnimationVector1D>, modifier: Modifier = Modifier) {
    // [fade] (0 gone, 1 shown) is driven by the caller, which also blurs the screen under the clock with it.
    val shown by remember(fade) { derivedStateOf { fade.value > 0.001f } }
    if (!shown) return

    var minuteOfDay by remember { mutableIntStateOf(currentMinuteOfDay()) }
    LaunchedEffect(visible) {
        while (visible) {
            minuteOfDay = currentMinuteOfDay()
            // Sleep until the next minute starts.
            delay(60_000L - System.currentTimeMillis() % 60_000L + 100L)
        }
    }
    val shift = shiftFor(minuteOfDay)

    Box(modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) {
        Canvas(Modifier.fillMaxSize()) {
            // The vignette: the corners and edges of the screen darken toward the black.
            drawRect(
                Brush.radialGradient(
                    0f to Color.Transparent,
                    VignetteStart to Color.Transparent,
                    1f to Color(0xE6000000),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = kotlin.math.hypot(size.width, size.height) / 2f
                )
            )
            drawRect(
                Brush.radialGradient(
                    0f to Color(0x99000000),
                    0.55f to Color(0x73000000),
                    1f to Color.Transparent,
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = PatchRadius.dp.toPx()
                )
            )
            val center = Offset((CenterX + shift.x).dp.toPx(), (CenterY + shift.y).dp.toPx())
            val radius = Radius.dp.toPx()
            drawCircle(Glass, radius, center)
            inset(center.x - radius, center.y - radius, size.width - center.x - radius, size.height - center.y - radius) {
                // Brighter just inside the white edge, shifting gradually into the deeper, greyer fade.
                drawFadingOutline(radius, RimDepth.dp.toPx(), RimStrength, Color.White, gain = 1f)
                drawFadingOutline(radius, EdgeGlowDepth.dp.toPx(), EdgeGlowStrength, Color.White, gain = 1f)
            }

            // The hour bars between the numbers.
            for (hour in 0 until 12) {
                if (hour % 3 == 0) continue
                val angle = Math.toRadians(hour * 30.0)
                val dx = sin(angle).toFloat()
                val dy = -cos(angle).toFloat()
                drawLine(
                    TextPrimary,
                    Offset(center.x + dx * radius * 0.84f, center.y + dy * radius * 0.84f),
                    Offset(center.x + dx * radius * 0.70f, center.y + dy * radius * 0.70f),
                    strokeWidth = (9f * Scale).dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // 12, 3, 6 and 9.
        for ((label, hour) in listOf("12" to 0, "3" to 3, "6" to 6, "9" to 9)) {
            val angle = Math.toRadians(hour * 30.0)
            val x = CenterX + shift.x + sin(angle).toFloat() * Radius * 0.77f
            val y = CenterY + shift.y - cos(angle).toFloat() * Radius * 0.77f
            Box(Modifier.offset((x - 60f).dp, (y - 60f).dp).size(120.dp), contentAlignment = Alignment.Center) {
                Text(label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = (64f * Scale).sp)
            }
        }

        // The hands and the hub go over the numbers and the bars.
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset((CenterX + shift.x).dp.toPx(), (CenterY + shift.y).dp.toPx())
            val radius = Radius.dp.toPx()
            val minute = minuteOfDay % 60
            val hourAngle = Math.toRadians(((minuteOfDay / 60) % 12 + minute / 60.0) * 30.0)
            val minuteAngle = Math.toRadians(minute * 6.0)
            drawLine(
                TextPrimary, center,
                Offset(center.x + sin(hourAngle).toFloat() * radius * 0.50f, center.y - cos(hourAngle).toFloat() * radius * 0.50f),
                strokeWidth = (15f * Scale).dp.toPx(), cap = StrokeCap.Round
            )
            drawLine(
                MinuteRed, center,
                Offset(center.x + sin(minuteAngle).toFloat() * radius * 0.86f, center.y - cos(minuteAngle).toFloat() * radius * 0.86f),
                strokeWidth = (9f * Scale).dp.toPx(), cap = StrokeCap.Round
            )
            drawCircle(Hub, (17f * Scale).dp.toPx(), center)
        }

    }
}
