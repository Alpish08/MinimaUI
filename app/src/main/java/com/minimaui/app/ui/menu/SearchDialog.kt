package com.minimaui.app.ui.menu

import com.minimaui.app.ui.components.verticalDragSteps
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.minimaui.app.ui.components.progressiveBlur
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaui.app.ui.components.ScrollSlider
import com.minimaui.app.ui.components.ControlHint
import com.minimaui.app.ui.components.ControlHintBar
import com.minimaui.app.ui.components.EaseInOut
import com.minimaui.app.ui.components.FocusRing
import com.minimaui.app.ui.components.UnfocusedOutline
import com.minimaui.app.ui.components.GlowRect
import com.minimaui.app.ui.components.LocalDimmed
import com.minimaui.app.ui.components.MarqueeText
import com.minimaui.app.ui.components.gradualGrowth
import com.minimaui.app.ui.components.SelectionGlow
import com.minimaui.app.ui.components.unfocusedOutline
import com.minimaui.app.ui.components.BackgroundOutline
import com.minimaui.app.ui.components.pillOutline
import com.minimaui.app.ui.components.focusFadeOutline
import com.minimaui.app.ui.components.outlineDepthFor
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.MenuPanelFill
import com.minimaui.app.ui.theme.MenuPillActive
import com.minimaui.app.ui.theme.MenuPillIdle
import com.minimaui.app.ui.theme.MenuScrim
import com.minimaui.app.ui.theme.TextDim
import com.minimaui.app.ui.theme.TextPrimary

const val SearchVisibleRows = 4
private const val CursorBlinkMillis = 500L

// Positions are in design pixels, relative to the panel.
internal const val PanelWidth = 916f
internal const val FieldX = 88f
internal const val FieldY = 109f
internal const val FieldWidth = 740f
internal const val FieldHeight = 80f
private const val SearchGrow = 80f
private val SearchDivider = Color(0xFF444444)
private const val ResultsY = 267f
private const val ResultPitch = 100f
private const val ResultHeight = 70f
// A result's pill is as long as its title needs, between these (like the folder names' pills): the text plus its padding.
private const val ResultMinWidth = 300f
private const val ResultTextPadding = 86f

/**
 * Global search: a field on top and the matching games and apps under it. The panel is exactly as tall as it
 * needs to be (it grows and shrinks, eased, as the number of results changes); the outline moves between the
 * field and the result rows.
 */
@Composable
fun SearchDialog(
    query: String,
    results: List<String>,
    selected: Int,
    onResults: Boolean,
    keyFocus: Int,
    digits: Boolean,
    caps: Boolean,
    hints: List<ControlHint>,
    modifier: Modifier = Modifier,
    onTapKey: (Int) -> Unit = {},
    onTapResult: (Int) -> Unit = {},
    onTapOutside: () -> Unit = {},
    onStepResults: (Int) -> Unit = {},
    onSeekResults: (Float) -> Unit = {},
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    // The keyboard slides in from the bottom while typing and away again when the cursor moves to the results.
    val keyboardShown = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(onResults) {
        com.minimaui.app.ui.components.withHalfRateFrames { keyboardShown.animateTo(if (onResults) 0f else 1f, tween(420, easing = EaseInOut)) }
    }

    // The results are only shown (and the panel only grows for them) once the cursor is on them.
    val rows = if (onResults) minOf(results.size, SearchVisibleRows) else 0
    // Each result's own pill length (before the focus growth), measured from its title.
    val measurer = rememberTextMeasurer()
    val resultStyle = LocalTextStyle.current.merge(TextStyle(fontSize = 31.sp, fontWeight = FontWeight.Bold))
    val bases = remember(results, resultStyle, density) {
        results.map { title ->
            val textPx = measurer.measure(title, resultStyle, softWrap = false, maxLines = 1).size.width
            (textPx / density + ResultTextPadding).coerceIn(ResultMinWidth, FieldWidth)
        }
    }
    // Like the app picker: scrolls only as far as needed to keep the selected row on screen, rows fade at the ends.
    val holder = remember { floatArrayOf(0f) }
    if (selected < holder[0]) holder[0] = selected.toFloat()
    if (selected > holder[0] + SearchVisibleRows - 1) holder[0] = (selected - (SearchVisibleRows - 1)).toFloat()
    holder[0] = holder[0].coerceIn(0f, maxOf(results.size - SearchVisibleRows, 0).toFloat())
    val targetTop = holder[0]
    val top by animateFloatAsState(targetTop, tween(300), label = "searchTop")
    val selectedPosition by animateFloatAsState(selected.toFloat(), tween(220), label = "searchSelected")
    val resultsFocus by animateFloatAsState(if (onResults) 1f else 0f, tween(200), label = "resultsFocus")
    val dividerAlpha = if (results.isNotEmpty()) resultsFocus else 0f
    val height by animateDpAsState(
        targetValue = (if (rows == 0) 272f else 333f + ResultPitch * rows).dp,
        animationSpec = tween(320, easing = EaseInOut),
        label = "searchHeight"
    )
    val panelShape = RoundedCornerShape(Dimens.PanelCorner)
    val focusRect = if (!onResults || rows == 0) {
        null
    } else {
        val width = (bases.getOrNull(selected) ?: FieldWidth) + SearchGrow
        GlowRect(FieldX + FieldWidth / 2f - width / 2f, ResultsY + (selected - targetTop) * ResultPitch, width, ResultHeight)
    }

    // On first showing, the panel rises into place (the overlay's blur and fade come with it).
    val rise = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { com.minimaui.app.ui.components.withHalfRateFrames { rise.animateTo(1f, androidx.compose.animation.core.tween(420, easing = EaseInOut)) } }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MenuScrim)
            // Touch: drag up or down to scroll the results.
            .verticalDragSteps(ResultPitch) { onStepResults(it) }
            // Touch: tap a result to open it, tap outside the panel to close.
            .pointerInput(bases, rows, onResults, digits) {
                detectTapGestures { offset ->
                    // Touch: a tap on the keyboard presses the key.
                    val keyX = offset.x / density - SearchKeyboardLayout.X
                    val keyY = offset.y / density - SearchKeyboardLayout.Y
                    if (!onResults && keyX >= 0f && keyX <= SearchKeyboardLayout.Width && keyY >= 0f) {
                        SearchKeyboardLayout.keyAt(keyX, keyY, digits)?.let(onTapKey)
                        return@detectTapGestures
                    }
                    val x = offset.x / density - 502f
                    val y = offset.y / density - 94f
                    if (x < 0f || y < 0f || x > PanelWidth || y > height.value) {
                        onTapOutside()
                        return@detectTapGestures
                    }
                    for (index in results.indices) {
                        val slot = index - top
                        if (slot <= -1f || slot >= SearchVisibleRows) continue
                        val rowY = ResultsY + slot * ResultPitch
                        val half = (bases.getOrNull(index) ?: FieldWidth) / 2f
                        if (x in (FieldX + FieldWidth / 2f - half)..(FieldX + FieldWidth / 2f + half) && y in rowY..(rowY + ResultHeight)) {
                            onTapResult(index)
                            return@detectTapGestures
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .offset(502.dp, 94.dp)
                .graphicsLayer { translationY = (1f - rise.value) * 70.dp.toPx() }
                .size(PanelWidth.dp, height)
                .progressiveBlur(26.dp) { 1f - rise.value }
                .clip(panelShape)
                .background(MenuPanelFill.copy(alpha = MenuPanelFill.alpha * resultsFocus))
                .pillOutline(24.dp, Dimens.PanelCorner) { BackgroundOutline * resultsFocus }
        ) {
            SelectionGlow(focusRect)

            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Search", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 41.sp)
            }

            Box(
                modifier = Modifier
                    .offset(FieldX.dp, FieldY.dp)
                    .size(FieldWidth.dp, FieldHeight.dp)
                    .clip(CircleShape)
                    .background(MenuPillIdle)
                    .unfocusedOutline(outlineDepthFor(FieldHeight)),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(
                        text = query,
                        color = TextPrimary,
                        fontSize = 33.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                    // The text cursor is simply there or not (twice a second), still while the screen is dimmed, and gone with the keyboard.
                    val dimmed = LocalDimmed.current
                    var cursorOn by remember { mutableStateOf(true) }
                    androidx.compose.runtime.LaunchedEffect(dimmed, onResults) {
                        cursorOn = true
                        if (dimmed || onResults) return@LaunchedEffect
                        while (true) {
                            kotlinx.coroutines.delay(CursorBlinkMillis)
                            cursorOn = !cursorOn
                        }
                    }
                    Box(
                        Modifier
                            .padding(start = 4.dp)
                            .size(3.dp, 44.dp)
                            .graphicsLayer { alpha = if (cursorOn && !onResults) 1f else 0f }
                            .background(TextPrimary)
                    )
                }
            }

            // A line between the field and the results.
            Box(
                Modifier
                    .offset(233.dp, 227.dp)
                    .size(450.dp, 3.dp)
                    .graphicsLayer { alpha = dividerAlpha }
                    .background(SearchDivider)
            )

            for (index in results.indices) {
                val slot = index - top
                if (slot <= -1f || slot >= SearchVisibleRows) continue
                val fade = minOf(slot + 1f, SearchVisibleRows - slot).coerceIn(0f, 1f)
                val active = onResults && index == selected
                // The focused result is wider than the rest, its neighbours half as much.
                val grow = gradualGrowth(index - selectedPosition, SearchGrow) * resultsFocus
                val width = (bases.getOrNull(index) ?: FieldWidth) + grow
                key(index) {
                Box(
                    modifier = Modifier
                        .offset((FieldX + FieldWidth / 2f - width / 2f).dp, (ResultsY + slot * ResultPitch).dp)
                        .size(width.dp, ResultHeight.dp)
                        .graphicsLayer { alpha = fade * resultsFocus }
                        .clip(CircleShape)
                        .background(if (active) MenuPillActive else MenuPillIdle)
                        .focusFadeOutline(outlineDepthFor(ResultHeight)),
                    contentAlignment = Alignment.Center
                ) {
                    // Like the folder names: centred when it fits, scrolling while focused when it does not.
                    MarqueeText(
                        text = results[index],
                        color = if (active) TextPrimary else TextDim,
                        fontSize = 31.sp,
                        scrolling = active,
                        startIndent = 40.dp,
                        endIndent = 40.dp,
                        centerWhenFits = true,
                    )
                }
                }
            }

            FocusRing(focusRect, fadeDepth = outlineDepthFor(ResultHeight), underStrength = UnfocusedOutline)

            if (onResults && results.size > SearchVisibleRows) {
                val trackHeight = 150f
                val trackTop = ResultsY + ((SearchVisibleRows - 1) * ResultPitch + ResultHeight - trackHeight) / 2f
                val fraction = selected.coerceIn(0, results.lastIndex) / results.lastIndex.toFloat()
                ScrollSlider(860f, trackTop, trackHeight, fraction, onSeek = onSeekResults)
            }

            ControlHintBar(
                hints = hints,
                pill = false,
                fontSize = 24.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 72.dp, bottom = 30.dp).graphicsLayer { alpha = resultsFocus }
            )
        }

        SearchKeyboard(
            keyFocus,
            active = !onResults,
            enterReady = results.isNotEmpty(),
            shown = { keyboardShown.value },
            digits = digits,
            caps = caps
        )
    }
}
