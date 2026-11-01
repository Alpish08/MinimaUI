package com.minimaui.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import com.minimaui.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.DotDim
import com.minimaui.app.ui.theme.IndicatorOn
import com.minimaui.app.ui.theme.TextPrimary

/** The RP6 logo at the bottom centre of Favorites; [alpha] fades it in after the boot splash. */
@Composable
fun RetroidLogo(alpha: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(Dimens.HintBarHeight), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.retroid_logo),
            contentDescription = "RP6",
            modifier = Modifier.requiredHeight(46.dp).graphicsLayer { this.alpha = alpha }
        )
    }
}

/**
 * The bottom-left page name ("Favorites —", "Apps —", "Edit Mode —"), level with the hint bar; [icon] sits before it.
 * With [centered] and no [dash] it is a plain name (a folder's), which the caller puts in the middle.
 */
@Composable
fun ScreenLabel(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 42.sp,
    icon: Int? = null,
    centered: Boolean = false,
    dash: Boolean = true,
) {
    Box(
        modifier = modifier.height(Dimens.HintBarHeight),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(54.dp)
                )
                androidx.compose.foundation.layout.Spacer(Modifier.width(26.dp))
            }
            // A new name (Edit Mode, a folder) comes into focus as the old one blurs away.
            BlurFadeSwitch(targetState = text) { shown ->
                Text(
                    text = if (dash) "$shown —" else shown,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Page indicator: just dots (no backing), the current one larger and bright. Every dot sits in a fixed slot as big as
 * the active one, so growing and shrinking never shifts the row, which stays centered.
 */
@Composable
fun PageDots(
    count: Int,
    current: Int,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    activeSize: Dp = 18.dp,
    inactiveSize: Dp = 14.dp,
    gap: Dp = 16.dp,
) {
    val dots = @Composable {
        repeat(count) { index -> PageDot(index == current, activeSize, inactiveSize) }
    }
    if (vertical) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) { dots() }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) { dots() }
    }
}

@Composable
private fun PageDot(active: Boolean, activeSize: Dp, inactiveSize: Dp) {
    val size by animateDpAsState(if (active) activeSize else inactiveSize, tween(220, easing = EaseInOut), label = "dotSize")
    val color by animateColorAsState(if (active) IndicatorOn else DotDim, tween(220), label = "dotColor")
    Box(modifier = Modifier.size(activeSize), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(size).background(color, CircleShape))
    }
}
