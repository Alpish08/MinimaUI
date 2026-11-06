package com.minimaui.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.HintLabel
import com.minimaui.app.ui.theme.TextPrimary

data class ControlHint(val key: String, val label: String)

/**
 * Button hints, e.g. "Y - All Games", as bare text. As the global [pill] they float above the pages (they are not
 * part of them, so page changes don't touch them) and are slightly larger; otherwise they are the plain row used inside the
 * menus (21sp, or the same size as the pages' with [pageSize]). When the hints change, only the ones that change move: a new
 * one opens its place and rises in, a gone one fades as its place closes, and the hints that stay don't flicker.
 */
@Composable
fun ControlHintBar(
    hints: List<ControlHint>,
    modifier: Modifier = Modifier,
    pill: Boolean = true,
    pageSize: Boolean = false,
    fontSize: TextUnit? = null,
) {
    if (!pill) {
        HintRow(hints, large = pageSize, modifier = modifier, fontSize = fontSize)
        return
    }
    Box(modifier = modifier.height(Dimens.HintBarHeight), contentAlignment = Alignment.CenterEnd) {
        HintRow(hints, large = true, modifier = Modifier.height(Dimens.HintBarHeight).padding(horizontal = 17.dp))
    }
}

/** A hint on screen: [shown] goes false when it is no longer among the hints, and it is dropped once it has faded out. */
private class ShownHint(val hint: ControlHint, val shown: MutableTransitionState<Boolean>)

@Composable
private fun HintRow(hints: List<ControlHint>, large: Boolean, modifier: Modifier, fontSize: TextUnit? = null) {
    val gap = if (large) 43.dp else 32.dp
    // The hints on screen: the current ones in order, plus the ones fading out where they were. The first set shows at once.
    val onScreen = remember { mutableStateListOf<ShownHint>().apply { hints.forEach { add(ShownHint(it, MutableTransitionState(true))) } } }
    if (onScreen.filter { it.shown.targetState }.map { it.hint } != hints) {
        onScreen.forEach { if (it.hint !in hints) it.shown.targetState = false }
        hints.forEachIndexed { index, hint ->
            if (onScreen.none { it.hint == hint && it.shown.targetState }) {
                // In front of the next current hint that is already on screen (or at the end).
                val next = hints.drop(index + 1).firstNotNullOfOrNull { h -> onScreen.indexOfFirst { it.hint == h && it.shown.targetState }.takeIf { it >= 0 } }
                onScreen.add(next ?: onScreen.size, ShownHint(hint, MutableTransitionState(false).apply { targetState = true }))
            }
        }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        onScreen.forEachIndexed { index, item ->
            key(item) {
                AnimatedVisibility(
                    visibleState = item.shown,
                    enter = expandHorizontally(tween(260, easing = EaseInOut), expandFrom = Alignment.Start) +
                        fadeIn(tween(220, delayMillis = 60, easing = EaseInOut)) + slideInVertically(tween(260, easing = EaseInOut)) { it / 5 },
                    exit = fadeOut(tween(140)) + shrinkHorizontally(tween(260, delayMillis = 60, easing = EaseInOut), shrinkTowards = Alignment.Start)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold)) {
                                append("${item.hint.key} - ")
                            }
                            withStyle(SpanStyle(color = HintLabel, fontWeight = FontWeight.Normal)) {
                                append(item.hint.label)
                            }
                        },
                        fontSize = fontSize ?: if (large) 27.4.sp else 21.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = if (index == 0) 0.dp else gap)
                    )
                }
                if (item.shown.isIdle && !item.shown.currentState) SideEffect { onScreen.remove(item) }
            }
        }
    }
}
