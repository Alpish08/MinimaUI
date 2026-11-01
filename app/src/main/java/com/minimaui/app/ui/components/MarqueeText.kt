package com.minimaui.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

private val EdgeFade = 40.dp

/** A title scrolls at most this many rounds, and for about [ScrollSeconds] (a long title gets one round, a short one two), then rests: a screen that is otherwise still stops redrawing every frame. */
private const val ScrollLoops = 2
private const val ScrollSeconds = 30

// The marquee's own pace, spelled out so that how long a round takes is known.
private val ScrollVelocity = 30.dp
private const val ScrollRepeatDelayMillis = 1200
private const val ScrollInitialDelayMillis = 700

/**
 * One-line bold text that ends in "…" when too long, and scrolls sideways instead while [scrolling] is on (for about [ScrollSeconds] seconds, then it holds still).
 * [startIndent] and [endIndent] keep the text off the edges while it rests; a scrolling title moves out
 * across them and fades at both sides, so it slides away smoothly rather than being cut off. With
 * [centerWhenFits] the indents only apply to a title too long for the space; a shorter one is centred instead.
 * [alignment] places a title that fits (Alignment.CenterEnd right-aligns it).
 */
@Composable
fun MarqueeText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    scrolling: Boolean,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    startIndent: Dp = 0.dp,
    endIndent: Dp = 0.dp,
    centerWhenFits: Boolean = false,
    alignment: Alignment = Alignment.Center,
) {
    val density = LocalDensity.current
    val naturalWidth = rememberTextWidth(text, fontSize)
    BoxWithConstraints(modifier = modifier, contentAlignment = alignment) {
        val overflows = naturalWidth > with(density) { (maxWidth - startIndent - endIndent).toPx() }
        val scroll = scrolling && overflows && !LocalDimmed.current
        // One round: the text and a third of the width (the marquee's gap) at its pace, then the pause. Whole rounds that fit in ScrollSeconds.
        val roundMillis = (naturalWidth + constraints.maxWidth / 3f) / with(density) { ScrollVelocity.toPx() } * 1000f + ScrollRepeatDelayMillis
        val rounds = (ScrollSeconds * 1000f / roundMillis).toInt().coerceIn(1, ScrollLoops)
        val fade by animateFloatAsState(if (scroll) 1f else 0f, tween(250), label = "marqueeFade")
        val indent = if (centerWhenFits && !overflows) Modifier else Modifier.padding(start = startIndent, end = endIndent)
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            maxLines = 1,
            softWrap = false,
            textAlign = textAlign,
            overflow = if (scrolling) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier
                .then(if (fade > 0f) Modifier.fadingEdges(EdgeFade * fade) else Modifier)
                .then(if (scroll) Modifier.basicMarquee(iterations = rounds, repeatDelayMillis = ScrollRepeatDelayMillis, initialDelayMillis = ScrollInitialDelayMillis, velocity = ScrollVelocity) else Modifier)
                .then(indent)
        )
    }
}

/** The width in px of [text] on one line, bold at [fontSize]: what [MarqueeText] compares with the space it has. */
@Composable
fun rememberTextWidth(text: String, fontSize: TextUnit): Int {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.merge(TextStyle(fontSize = fontSize, fontWeight = FontWeight.Bold))
    return remember(text, style, density) { measurer.measure(text, style, softWrap = false, maxLines = 1).size.width }
}

/** Fades the content out toward its left and right edges over [width]. */
private fun Modifier.fadingEdges(width: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fraction = (width.toPx() / size.width).coerceIn(0f, 0.5f)
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Transparent,
                fraction to Color.Black,
                1f - fraction to Color.Black,
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }
