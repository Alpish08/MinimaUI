package com.minimaui.app.ui.menu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaui.app.ui.components.ControlHint
import com.minimaui.app.ui.components.ControlHintBar
import com.minimaui.app.ui.components.FocusRing
import com.minimaui.app.ui.components.UnfocusedOutline
import com.minimaui.app.ui.components.GlowRect
import com.minimaui.app.ui.components.SelectionGlow
import com.minimaui.app.ui.components.backgroundOutline
import com.minimaui.app.ui.components.focusFadeOutline
import com.minimaui.app.ui.components.outlineDepthFor
import com.minimaui.app.ui.components.pillOutline
import com.minimaui.app.ui.components.progressiveBlur
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.MenuPanelFill
import com.minimaui.app.ui.theme.MenuPillActive
import com.minimaui.app.ui.theme.MenuPillIdle
import com.minimaui.app.ui.theme.TextPrimary
import kotlin.math.abs

enum class KeyAction { Letter, Space, Delete, Enter, Shift }

/** One key of the search keyboard, in design pixels relative to the keyboard's panel. */
class KbKey(val label: String, val action: KeyAction, val x: Float, val y: Float, val width: Float, val height: Float) {
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f
}

/** The search keyboard's keys (three letter rows, Space, and Delete and Enter stacked at the right) and how the cursor moves between them. */
object SearchKeyboardLayout {
    // The panel spans the screen's width near the bottom and runs past its edge.
    const val X = 0f
    const val Y = 430f
    const val Width = 1920f
    const val Height = 700f
    const val KeyHeight = 100f

    private const val KeyWidth = 150f
    private const val KeyPitch = 182f
    private const val RowPitch = 130f
    private const val FirstRowY = 36f
    private const val SideKeyWidth = 217f
    private const val SideKeyX = 1580f
    private const val TapSlop = 15f

    val keys: List<KbKey> = buildList {
        fun rowY(row: Int) = FirstRowY + row * RowPitch
        fun letters(row: Int, text: String, x: Float) =
            text.forEachIndexed { i, c -> add(KbKey(c.toString(), KeyAction.Letter, x + i * KeyPitch, rowY(row), KeyWidth, KeyHeight)) }
        letters(0, "QWERTYUIOP", 66f)
        letters(1, "ASDFGHJKL", 157f)
        letters(2, "ZXCVBNM", 248f)
        add(KbKey("Space", KeyAction.Space, 506f, rowY(3), 911f, KeyHeight))
        // Enter and Delete are as tall as the other keys, stacked under the L key's centre: Enter on the M key's line, Delete on the space bar's.
        add(KbKey("Enter", KeyAction.Enter, SideKeyX, rowY(2), SideKeyWidth, KeyHeight))
        add(KbKey("Delete", KeyAction.Delete, SideKeyX, rowY(3), SideKeyWidth, KeyHeight))
        // Shift is Delete mirrored over the middle of the screen; it toggles the capitals like the L button.
        add(KbKey("Shift", KeyAction.Shift, 1920f - SideKeyX - SideKeyWidth, rowY(3), SideKeyWidth, KeyHeight))
    }

    /** The number pad (R1): 1 to 9 in three rows, 0 under the 5, Enter and Delete at the right and the space bar under them. */
    val numpadKeys: List<KbKey> = buildList {
        fun rowY(row: Int) = FirstRowY + row * RowPitch
        val columns = floatArrayOf(703f, 885f, 1067f)
        for (n in 1..9) add(KbKey(n.toString(), KeyAction.Letter, columns[(n - 1) % 3], rowY((n - 1) / 3), KeyWidth, KeyHeight))
        add(KbKey("0", KeyAction.Letter, columns[1], rowY(3), KeyWidth, KeyHeight))
        add(KbKey("Space", KeyAction.Space, 1322f, rowY(3), 409f, KeyHeight))
        add(KbKey("Enter", KeyAction.Enter, 1514f, rowY(1), SideKeyWidth, KeyHeight))
        add(KbKey("Delete", KeyAction.Delete, 1514f, rowY(2), SideKeyWidth, KeyHeight))
    }

    fun keysFor(digits: Boolean): List<KbKey> = if (digits) numpadKeys else keys

    /**
     * The key the cursor lands on moving from [from] by [dx] or [dy] (the cursor stops at the edges; it doesn't wrap).
     * Sideways: the nearest key on that side that shares a row with it. Up and down: the nearest row above or below
     * that lies over or under it, and in it the key whose centre is closest; with nothing straight above or below
     * (Z over the space bar), the nearest row's closest key.
     */
    fun move(from: Int, dx: Int, dy: Int, digits: Boolean = false): Int {
        val keys = keysFor(digits)
        val key = keys.getOrNull(from) ?: return 0
        if (dx != 0) {
            return keys.indices
                .filter { i ->
                    val other = keys[i]
                    (other.centerX - key.centerX) * dx > 0f && other.y < key.y + key.height && key.y < other.y + other.height
                }
                .minByOrNull { abs(keys[it].centerX - key.centerX) } ?: from
        }
        val beyond = keys.indices.filter { (keys[it].centerY - key.centerY) * dy > 10f }
        val straight = beyond.filter { keys[it].x < key.x + key.width && key.x < keys[it].x + keys[it].width }
        val candidates = straight.ifEmpty { beyond }
        val nearestRow = candidates.minOfOrNull { abs(keys[it].centerY - key.centerY) } ?: return from
        return candidates
            .filter { abs(keys[it].centerY - key.centerY) <= nearestRow + 30f }
            .minBy { abs(keys[it].centerX - key.centerX) }
    }

    /** The character the letter or digit key [index] types, or null for the other keys. */
    fun charFor(index: Int, digits: Boolean = false): Char? {
        val key = keysFor(digits).getOrNull(index) ?: return null
        return if (key.action == KeyAction.Letter) key.label[0] else null
    }

    /** The key under a touch at ([x], [y]) in the panel's coordinates; the gaps between keys count for the nearest one. */
    fun keyAt(x: Float, y: Float, digits: Boolean = false): Int? {
        val keys = keysFor(digits)
        return keys.indices.firstOrNull { i ->
            val key = keys[i]
            x in (key.x - TapSlop)..(key.x + key.width + TapSlop) && y in (key.y - TapSlop)..(key.y + key.height + TapSlop)
        }
    }
}

private const val FocusGrowWidth = 22f
private const val FocusGrowHeight = 12f
// The panel rises this far while it fades in (like the search field), and sinks as far when it goes.
private const val SpaceIconWidth = 66f
private const val SpaceIconHeight = 16f
private const val SpaceIconStroke = 6f
private const val SlideDistance = 70f

// Where the panel starts to fade and where it is gone, as shares of its height (the screen ends at 650 of 700).
private const val FadeStart = 0.3f
private const val FadeEnd = 0.93f
private const val HintBarTop = 578f

/**
 * The on-screen keyboard of the search: a panel along the bottom of the screen with a pill per key. The focused key
 * grows and carries the glow and the fading focus outline; the others keep the gray outline. [shown] (0 to 1) slides
 * the panel in from the bottom, [active] is whether the cursor is on the keyboard at all.
 */
@Composable
fun SearchKeyboard(
    focus: Int,
    active: Boolean,
    enterReady: Boolean,
    shown: () -> Float,
    modifier: Modifier = Modifier,
    digits: Boolean = false,
    /** Null: no Caps hint (the search types lowercase). Otherwise whether Caps is on, shown with the letters. */
    caps: Boolean? = null,
) {
    // Switching between the letters and the number pad fades the keys out, swaps them and fades them in (like the settings entries).
    var shownDigits by remember { mutableStateOf(digits) }
    val keysFade = remember { Animatable(1f) }
    LaunchedEffect(digits) {
        if (shownDigits == digits) return@LaunchedEffect
        keysFade.animateTo(0f, tween(90))
        shownDigits = digits
        keysFade.animateTo(1f, tween(140))
    }
    val keys = SearchKeyboardLayout.keysFor(shownDigits)
    Box(
        modifier = modifier
            .offset(SearchKeyboardLayout.X.dp, SearchKeyboardLayout.Y.dp)
            .size(SearchKeyboardLayout.Width.dp, SearchKeyboardLayout.Height.dp)
            .graphicsLayer {
                translationY = (1f - shown()) * SlideDistance.dp.toPx()
                alpha = shown()
            }
            .progressiveBlur(26.dp) { 1f - shown() }
    ) {
        // The panel and its outline fade out toward the bottom of the screen.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0f to Color.Black, FadeStart to Color.Black, FadeEnd to Color.Transparent),
                        blendMode = BlendMode.DstIn
                    )
                }
                .clip(RoundedCornerShape(Dimens.PanelCorner))
                .background(MenuPanelFill)
                .backgroundOutline(depth = 24.dp, corner = Dimens.PanelCorner)
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = keysFade.value }) {
        val focused = keys[focus.coerceIn(keys.indices)]
        val focusRect = if (active) {
            GlowRect(
                focused.x - FocusGrowWidth / 2f,
                focused.y - FocusGrowHeight / 2f,
                focused.width + FocusGrowWidth,
                focused.height + FocusGrowHeight
            )
        } else {
            null
        }
        SelectionGlow(focusRect, adaptiveBlur = true)

        keys.forEachIndexed { i, key ->
            KeyPill(
                key,
                label = SearchKeyboardLayout.charFor(i, shownDigits)?.toString() ?: key.label,
                active = active && i == focus,
                dim = key.action == KeyAction.Enter && !enterReady,
                lit = key.action == KeyAction.Shift && caps == true
            )
        }

        FocusRing(focusRect, fadeDepth = outlineDepthFor(focused.height), underStrength = UnfocusedOutline)
        }

        ControlHintBar(
            hints = listOfNotNull(
                if (!digits && caps != null) ControlHint("L", if (caps) "Caps On" else "Caps Off") else null,
                ControlHint("R", if (digits) "ABC" else "123"),
            ) + listOf(ControlHint("Y", "Space"), ControlHint("X", "Delete"), ControlHint("START", "Enter"), ControlHint("B", "Cancel")),
            pill = false,
            fontSize = 24.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = HintBarTop.dp, end = 87.dp)
        )
    }
}

@Composable
private fun KeyPill(key: KbKey, label: String, active: Boolean, dim: Boolean, lit: Boolean = false) {
    val grow by animateFloatAsState(if (active) 1f else 0f, tween(200), label = "keyGrow")
    Box(
        modifier = Modifier
            .offset((key.x - FocusGrowWidth * grow / 2f).dp, (key.y - FocusGrowHeight * grow / 2f).dp)
            .size((key.width + FocusGrowWidth * grow).dp, (key.height + FocusGrowHeight * grow).dp)
            .clip(CircleShape)
            .background(if (active || lit) MenuPillActive else MenuPillIdle)
            .focusFadeOutline(outlineDepthFor(key.height))
            // Shift while the capitals are on: a steady white outline (it does not blink).
            .then(if (lit) Modifier.pillOutline(outlineDepthFor(key.height)) { 1f } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (key.action == KeyAction.Space) {
            // An open box, like the space key of a real keyboard.
            Canvas(Modifier.size(SpaceIconWidth.dp, SpaceIconHeight.dp)) {
                val stroke = SpaceIconStroke.dp.toPx()
                val half = stroke / 2f
                val path = Path().apply {
                    moveTo(half, 0f)
                    lineTo(half, size.height - half)
                    lineTo(size.width - half, size.height - half)
                    lineTo(size.width - half, 0f)
                }
                drawPath(path, TextPrimary, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        } else {
            Text(
                text = label,
                color = TextPrimary.copy(alpha = if (dim) 0.4f else 1f),
                fontWeight = FontWeight.Bold,
                fontSize = if (key.action == KeyAction.Letter) 40.sp else 26.sp
            )
        }
    }
}
