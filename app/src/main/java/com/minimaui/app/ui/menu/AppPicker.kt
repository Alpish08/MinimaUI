package com.minimaui.app.ui.menu

import com.minimaui.app.ui.components.verticalDragSteps
import androidx.compose.runtime.getValue
import com.minimaui.app.ui.apps.AppOutlineDepth
import com.minimaui.app.ui.components.ScrollSlider
import com.minimaui.app.ui.components.backgroundOutline
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import com.minimaui.app.ui.components.FocusedOutlineGain
import com.minimaui.app.ui.components.UnfocusedOutline
import com.minimaui.app.ui.components.softOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import com.minimaui.app.data.InstalledApp
import com.minimaui.app.data.InstalledApps
import com.minimaui.app.ui.components.ControlHint
import com.minimaui.app.ui.components.EaseInOut
import com.minimaui.app.ui.components.FocusRing
import com.minimaui.app.ui.components.ControlHintBar
import com.minimaui.app.ui.components.GlowRect
import com.minimaui.app.ui.components.MarqueeText
import com.minimaui.app.ui.components.SelectionGlow
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.MenuPanelFill
import com.minimaui.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val PickerColumns = 6

// Positions are in design pixels, relative to the panel.
private const val Diameter = 190f
private const val FirstCenterX = 204f
private const val ColumnPitch = 258f
private const val PickerGrow = 0.08f
private const val FirstCenterY = 218f
private const val RowPitch = 291f
private const val LowestSelectedY = 520f

private val IconFill = Color(0xFF262626)

private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

/** Rows fade out toward the header at the top (toward the bottom, the whole grid fades smoothly: see [BottomFade]). */
private fun fadeAt(centerY: Float): Float = clamp01(centerY / 200f)

/** The grid fades out smoothly toward the panel's bottom edge, from just under the second row (the lowest the focus goes). */
private const val BottomFadeStart = 660f
private val BottomFade = Brush.verticalGradient(
    0f to Color.Black,
    BottomFadeStart / 844f to Color.Black,
    1f to Color.Transparent,
)

/**
 * A grid of installed apps, [PickerColumns] to a row, scrolling by rows so the selected one stays on the first or
 * second row. Rows fade out under the title and above the hints, and a slider on the right shows the position.
 * Apps that are already added are dimmed.
 */
@Composable
fun AppPicker(
    title: String,
    items: List<InstalledApp>,
    selected: Int,
    hints: List<ControlHint>,
    modifier: Modifier = Modifier,
    onTapItem: (Int) -> Unit = {},
    onTapOutside: () -> Unit = {},
    onSwipeRows: (Int) -> Unit = {},
    onSeekRows: (Float) -> Unit = {},
) {
    val density = LocalDensity.current.density
    val panelShape = RoundedCornerShape(Dimens.PanelCorner)
    val rows = (items.size + PickerColumns - 1) / PickerColumns
    val selectedRow = selected / PickerColumns
    // Scrolls only as far as needed to keep the selected row on screen; the rows beyond the limits fade out.
    val holder = remember { floatArrayOf(0f) }
    val selectedY = FirstCenterY + selectedRow * RowPitch
    if (selectedY - holder[0] > LowestSelectedY) holder[0] = selectedY - LowestSelectedY
    if (selectedY - holder[0] < FirstCenterY) holder[0] = selectedY - FirstCenterY
    holder[0] = holder[0].coerceAtLeast(0f)
    val targetTop = holder[0] / RowPitch
    val top = animateFloatAsState(targetTop, tween(300), label = "pickerTop")

    Box(
        modifier = modifier
            .fillMaxSize()
            // Touch: tap an app to add it, tap outside the panel to close, swipe up/down to scroll by a row.
            .pointerInput(items.size) {
                detectTapGestures { offset ->
                    val x = offset.x / density - Dimens.PanelX.value
                    val y = offset.y / density - Dimens.PanelY.value
                    if (x < 0f || y < 0f || x > Dimens.PanelWidth.value || y > Dimens.PanelHeight.value) {
                        onTapOutside()
                        return@detectTapGestures
                    }
                    val column = Math.round((x - FirstCenterX) / ColumnPitch)
                    val row = Math.round((y - FirstCenterY) / RowPitch + top.value)
                    val dx = x - (FirstCenterX + column * ColumnPitch)
                    val dy = y - (FirstCenterY + (row - top.value) * RowPitch)
                    val index = row * PickerColumns + column
                    if (column in 0 until PickerColumns && row >= 0 && index in items.indices && dx * dx + dy * dy <= (Diameter / 2f) * (Diameter / 2f)) {
                        onTapItem(index)
                    }
                }
            }
            .verticalDragSteps(110f) { onSwipeRows(it) }
    ) {
        Box(
            modifier = Modifier
                .offset(Dimens.PanelX, Dimens.PanelY)
                .size(Dimens.PanelWidth, Dimens.PanelHeight)
                .clip(panelShape)
                .background(MenuPanelFill)
                .backgroundOutline(depth = 24.dp, corner = Dimens.PanelCorner)
        ) {
            if (items.isEmpty()) {
                Text("No apps found", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 32.sp, modifier = Modifier.align(Alignment.Center))
            } else {
                val glowX = FirstCenterX + (selected % PickerColumns) * ColumnPitch - Diameter / 2f
                val glowY = FirstCenterY + (selectedRow - targetTop) * RowPitch - Diameter / 2f
                val focusRect = GlowRect(glowX, glowY, Diameter, Diameter, Diameter / 2f)
                SelectionGlow(focusRect)

                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(BottomFade, blendMode = BlendMode.DstIn)
                        }
                ) {
                for (i in maxOf((selectedRow - 3) * PickerColumns, 0)..minOf((selectedRow + 4) * PickerColumns + PickerColumns - 1, items.lastIndex)) {
                    key(items[i].packageName) { AppCell(
                        app = items[i],
                        row = i / PickerColumns,
                        column = i % PickerColumns,
                        top = top,
                        active = i == selected,
                        // Only the focused icon is a little larger.
                        focusScale = if (i == selected) 1f + PickerGrow else 1f
                    ) }
                }
                }

                FocusRing(
                    focusRect, color = Color.White, scale = { 1f + PickerGrow }, fadeDepth = AppOutlineDepth,
                    underStrength = UnfocusedOutline * FocusedOutlineGain
                )

                if (rows > 1) {
                    val fraction = selectedRow / (rows - 1).toFloat()
                    ScrollSlider(1637f, 313f, 217f, fraction, onSeek = onSeekRows)
                }
            }

            // Drawn last so the faded rows pass under it.
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 38.dp)
            )
        }

        // On the screen's bottom right, where the page hints normally are, not inside the panel.
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

@Composable
private fun AppCell(
    app: InstalledApp,
    row: Int,
    column: Int,
    top: androidx.compose.runtime.State<Float>,
    active: Boolean,
    focusScale: Float,
) {
    val scale by animateFloatAsState(focusScale, tween(200), label = "cellScale")
    val glide = tween<Float>(durationMillis = 260, easing = EaseInOut)
    val animatedRow by animateFloatAsState(row.toFloat(), glide, label = "cellRow")
    val animatedColumn by animateFloatAsState(column.toFloat(), glide, label = "cellColumn")
    val x = FirstCenterX + animatedColumn * ColumnPitch
    val centerY = { FirstCenterY + (animatedRow - top.value) * RowPitch }
    val icon = rememberAppIcon(app.packageName)
    val fade = { fadeAt(centerY()) }

    Box(
        modifier = Modifier
            .offset { IntOffset((x - Diameter / 2f).dp.roundToPx(), (centerY() - Diameter / 2f).dp.roundToPx()) }
            .size(Diameter.dp)
            .graphicsLayer {
                alpha = fade()
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(IconFill)
            .softOutline(depth = AppOutlineDepth, gain = FocusedOutlineGain) { UnfocusedOutline }
    ) {
        icon?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
    MarqueeText(
        text = app.label,
        color = TextPrimary,
        fontSize = 26.sp,
        scrolling = active,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .offset { IntOffset((x - 125f).dp.roundToPx(), (centerY() + Diameter * scale / 2f + 8f).dp.roundToPx()) }
            .size(250.dp, 38.dp)
            .graphicsLayer { alpha = fade() }
    )
}

private val iconCache = HashMap<String, ImageBitmap>()

/** The app's icon, kept in memory once loaded so scrolling back to a row never shows a blank or stale icon. */
@Composable
private fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    val icon by produceState(iconCache[packageName], packageName) {
        value = iconCache[packageName] ?: withContext(Dispatchers.IO) {
            runCatching { InstalledApps.iconBitmap(context, packageName, 300).asImageBitmap() }.getOrNull()
        }?.also { iconCache[packageName] = it }
    }
    return icon
}
