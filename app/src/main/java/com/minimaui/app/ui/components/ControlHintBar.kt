package com.minimaui.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
 * menus (21sp, or the same size as the pages' with [pageSize]). When the hints change, the new ones fade in over the old ones
 * fading out, right-aligned, with no movement.
 */
@Composable
fun ControlHintBar(
    hints: List<ControlHint>,
    modifier: Modifier = Modifier,
    pill: Boolean = true,
    pageSize: Boolean = false,
    fontSize: TextUnit? = null,
) {
    val large = pill || pageSize
    val rowModifier = if (pill) Modifier.height(Dimens.HintBarHeight).padding(horizontal = 17.dp) else Modifier
    AnimatedContent(
        targetState = hints,
        transitionSpec = {
            fadeIn(tween(220, delayMillis = 60, easing = EaseInOut)) togetherWith fadeOut(tween(140)) using
                SizeTransform(clip = false) { _, _ -> snap() }
        },
        contentAlignment = Alignment.CenterEnd,
        modifier = if (pill) modifier.height(Dimens.HintBarHeight) else modifier,
        label = "controlHints"
    ) { items ->
        HintRow(items, large = large, modifier = rowModifier, fontSize = fontSize)
    }
}

@Composable
private fun HintRow(hints: List<ControlHint>, large: Boolean, modifier: Modifier, fontSize: TextUnit? = null) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (large) 43.dp else 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        hints.forEach { hint ->
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold)) {
                        append("${hint.key} - ")
                    }
                    withStyle(SpanStyle(color = HintLabel, fontWeight = FontWeight.Normal)) {
                        append(hint.label)
                    }
                },
                fontSize = fontSize ?: if (large) 27.4.sp else 21.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
