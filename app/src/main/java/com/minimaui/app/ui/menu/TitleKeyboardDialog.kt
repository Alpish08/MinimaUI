package com.minimaui.app.ui.menu

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaui.app.ui.components.ControlHint
import com.minimaui.app.ui.components.EaseInOut
import com.minimaui.app.ui.components.LocalDimmed
import com.minimaui.app.ui.components.outlineDepthFor
import com.minimaui.app.ui.components.progressiveBlur
import com.minimaui.app.ui.components.unfocusedOutline
import com.minimaui.app.ui.components.withHalfRateFrames
import com.minimaui.app.ui.theme.MenuPillIdle
import com.minimaui.app.ui.theme.MenuScrim
import com.minimaui.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay

private const val CursorBlinkMillis = 500L

/**
 * Typing a title (a game's, an app's, a platform's) or a folder name with MinimaUI's own keyboard: the heading and the field
 * on top, the keyboard along the bottom. [selected] shows the text as selected (the first key replaces it).
 */
@Composable
fun TitleKeyboardDialog(
    heading: String,
    text: String,
    selected: Boolean,
    keyFocus: Int,
    caps: Boolean,
    digits: Boolean,
    modifier: Modifier = Modifier,
    onTapKey: (Int) -> Unit = {},
) {
    val density = LocalDensity.current.density
    // Like the search: the field rises in with a blur, and the keyboard rises with it.
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { withHalfRateFrames { rise.animateTo(1f, tween(420, easing = EaseInOut)) } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MenuScrim)
            // Touch: a tap on the keyboard presses the key.
            .pointerInput(digits) {
                detectTapGestures { offset ->
                    val keyX = offset.x / density - SearchKeyboardLayout.X
                    val keyY = offset.y / density - SearchKeyboardLayout.Y
                    if (keyX >= 0f && keyX <= SearchKeyboardLayout.Width && keyY >= 0f) {
                        SearchKeyboardLayout.keyAt(keyX, keyY, digits)?.let(onTapKey)
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .offset(502.dp, 94.dp)
                .size(PanelWidth.dp, 272.dp)
                .graphicsLayer { translationY = (1f - rise.value) * 70.dp.toPx() }
                .progressiveBlur(26.dp) { 1f - rise.value }
        ) {
            Text(
                heading,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 41.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp)
            )
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
                        text = text,
                        color = TextPrimary,
                        fontSize = 33.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = if (selected) Modifier.background(Color(0x40FFFFFF), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp) else Modifier
                    )
                    // The text cursor is simply there or not (twice a second), still while the screen is dimmed.
                    val dimmed = LocalDimmed.current
                    var cursorOn by remember { mutableStateOf(true) }
                    LaunchedEffect(dimmed) {
                        cursorOn = true
                        if (dimmed) return@LaunchedEffect
                        while (true) {
                            delay(CursorBlinkMillis)
                            cursorOn = !cursorOn
                        }
                    }
                    Box(
                        Modifier
                            .padding(start = 4.dp)
                            .size(3.dp, 44.dp)
                            .graphicsLayer { alpha = if (cursorOn && !selected) 1f else 0f }
                            .background(TextPrimary)
                    )
                }
            }
        }

        SearchKeyboard(
            keyFocus,
            active = true,
            enterReady = text.isNotBlank(),
            shown = { rise.value },
            digits = digits,
            caps = caps
        )
    }
}
