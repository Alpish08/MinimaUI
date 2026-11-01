package com.minimaui.app.ui.menu

import com.minimaui.app.ui.components.verticalDragSteps
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaui.app.ui.components.ScrollSlider
import com.minimaui.app.ui.components.ControlHint
import com.minimaui.app.ui.components.ControlHintBar
import com.minimaui.app.ui.components.FocusRing
import com.minimaui.app.ui.components.GlowRect
import com.minimaui.app.ui.components.MarqueeText
import com.minimaui.app.ui.components.SelectionGlow
import com.minimaui.app.ui.components.backgroundOutline
import com.minimaui.app.ui.components.focusFadeOutline
import com.minimaui.app.ui.components.gradualGrowth
import com.minimaui.app.ui.components.outlineDepthFor
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.MenuPanelFill
import com.minimaui.app.ui.theme.MenuPillActive
import com.minimaui.app.ui.theme.MenuPillIdle
import com.minimaui.app.ui.theme.MenuScrim
import com.minimaui.app.ui.theme.TextDim
import com.minimaui.app.ui.theme.TextPrimary

// Design pixels, relative to the panel.
private const val PanelX = 503f
private const val PanelY = 94f
private const val PanelW = 918f
private const val ChooserBottom = 44f
private const val RowsShown = 3
private const val RowsY = 156f // the first row: as far below the divider as the divider is below the title's letters
private const val RowPitch = 101f
private const val RowH = 71f
private const val RowBase = 400f
private const val RowGrow = 100f
private const val PlusRowW = 114f
private const val PlusRowH = 65f
private val Divider = Color(0xFF444444)

/**
 * The user's folders: a short scrolling list (three rows, the highlighted one longer, with a scrollbar) and a "+" button
 * at the top right that makes a new one. [selected] is -1 while the "+" button is highlighted.
 */
@Composable
fun FolderChooser(
    title: String,
    names: List<String>,
    selected: Int,
    hints: List<ControlHint>,
    modifier: Modifier = Modifier,
    onTapRow: (Int) -> Unit = {},
    onTapOutside: () -> Unit = {},
    onStepRows: (Int) -> Unit = {},
    onSeekRows: (Float) -> Unit = {},
) {
    val density = LocalDensity.current.density
    // The folders, then a small "+" row at the end (index names.size) that makes a new one.
    val rowCount = names.size + 1
    // Scrolls only as far as needed to keep the selected row on screen; rows at the ends fade.
    val holder = remember { floatArrayOf(0f) }
    if (selected < holder[0]) holder[0] = selected.toFloat()
    if (selected > holder[0] + RowsShown - 1) holder[0] = (selected - (RowsShown - 1)).toFloat()
    holder[0] = holder[0].coerceIn(0f, maxOf(rowCount - RowsShown, 0).toFloat())
    val top by animateFloatAsState(holder[0], tween(300), label = "chooserTop")
    val selectedPosition by animateFloatAsState(selected.toFloat(), tween(220), label = "chooserSel")
    // The panel is as tall as its rows need, up to three.
    val rowsNow = rowCount.coerceIn(1, RowsShown)
    val panelHeight by androidx.compose.animation.core.animateDpAsState(
        (RowsY + (rowsNow - 1) * RowPitch + RowH + ChooserBottom).dp, tween(300, easing = com.minimaui.app.ui.components.EaseInOut), label = "chooserH"
    )

    fun widthOf(i: Int, grow: Float) = if (i == names.size) PlusRowW + grow * 0.3f else RowBase + grow
    fun rowRect(i: Int, grow: Float): GlowRect {
        val w = widthOf(i, grow)
        val h = if (i == names.size) PlusRowH else RowH
        return GlowRect(PanelW / 2f - w / 2f, RowsY + (i - top) * RowPitch + (RowH - h) / 2f, w, h)
    }
    val focusRect = rowRect(selected, RowGrow)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MenuScrim)
            .verticalDragSteps(110f) { onStepRows(it) }
            .pointerInput(rowCount, top) {
                detectTapGestures { offset ->
                    val x = offset.x / density - PanelX
                    val y = offset.y / density - PanelY
                    if (x < 0f || y < 0f || x > PanelW || y > panelHeight.value) {
                        onTapOutside()
                        return@detectTapGestures
                    }
                    for (i in 0 until rowCount) {
                        val slot = i - top
                        if (slot <= -1f || slot >= RowsShown) continue
                        val rowY = RowsY + slot * RowPitch
                        val half = widthOf(i, 0f) / 2f
                        if (x in (PanelW / 2f - half)..(PanelW / 2f + half) && y in rowY..(rowY + RowH)) {
                            onTapRow(i)
                            return@detectTapGestures
                        }
                    }
                }
            }
    ) {
        val panelShape = RoundedCornerShape(Dimens.PanelCorner)
        Box(
            modifier = Modifier
                .offset(PanelX.dp, PanelY.dp)
                .size(PanelW.dp, panelHeight)
                .clip(panelShape)
                .background(MenuPanelFill)
                .backgroundOutline(depth = 24.dp, corner = Dimens.PanelCorner)
        ) {
            SelectionGlow(focusRect)
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 41.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 34.dp)
            )
            Box(Modifier.offset(234.dp, 112.dp).size(450.dp, 3.dp).background(Divider))

            for (i in 0 until rowCount) {
                val slot = i - top
                if (slot <= -1f || slot >= RowsShown) continue
                val fade = minOf(slot + 1f, RowsShown - slot).coerceIn(0f, 1f)
                val active = i == selected
                val grow = gradualGrowth(i - selectedPosition, RowGrow)
                val isPlus = i == names.size
                val w = widthOf(i, grow)
                val h = if (isPlus) PlusRowH else RowH
                key(i) {
                Box(
                    modifier = Modifier
                        .offset((PanelW / 2f - w / 2f).dp, (RowsY + slot * RowPitch + (RowH - h) / 2f).dp)
                        .size(w.dp, h.dp)
                        .graphicsLayer { alpha = fade }
                        .clip(CircleShape)
                        .background(if (active) MenuPillActive else MenuPillIdle)
                        .focusFadeOutline(active, outlineDepthFor(h)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlus) {
                        // The same plus as the ROMs folder list's.
                        androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
                            val stroke = 5.dp.toPx()
                            drawLine(TextPrimary, androidx.compose.ui.geometry.Offset(0f, size.height / 2f), androidx.compose.ui.geometry.Offset(size.width, size.height / 2f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                            drawLine(TextPrimary, androidx.compose.ui.geometry.Offset(size.width / 2f, 0f), androidx.compose.ui.geometry.Offset(size.width / 2f, size.height), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                        }
                    } else MarqueeText(
                        text = names[i],
                        color = if (active) TextPrimary else TextDim,
                        fontSize = 29.sp,
                        scrolling = active,
                        startIndent = 36.dp,
                        endIndent = 36.dp,
                        centerWhenFits = true,
                    )
                }
                }
            }
            FocusRing(focusRect, pulsing = true, fadeDepth = outlineDepthFor(focusRect.height))

            if (rowCount > RowsShown) {
                val trackHeight = 136f
                val fraction = selected / (rowCount - 1).toFloat()
                ScrollSlider(PanelW - 95f, 112f + 62f, trackHeight, fraction, onSeek = onSeekRows)
            }
        }
        ControlHintBar(
            hints = hints,
            pill = false,
            pageSize = true,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Dimens.HintBarEnd + 17.dp, bottom = Dimens.HintBarBottom + 9.dp)
        )
    }
}
