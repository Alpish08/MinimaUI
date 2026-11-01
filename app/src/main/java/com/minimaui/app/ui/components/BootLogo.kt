package com.minimaui.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.minimaui.app.R
import com.minimaui.app.ui.theme.Dimens

private const val Scale = 2.7f

/**
 * The boot sequence: the logo large in the middle of the screen over a dimmed, round-faded patch, which then
 * fade out together (the RP6 logo fades in at the bottom of Favorites meanwhile). [progress] runs 0 (showing) to 1
 * (gone); [appear] fades the whole thing in at the start.
 */
@Composable
fun BootLogo(progress: Float, appear: Float, modifier: Modifier = Modifier) {
    val eased = progress.coerceIn(0f, 1f)
    Box(modifier = modifier.fillMaxSize()) {
        // The dimmed circle: darkest behind the logo, fading to nothing toward its edge; it fades out as the logo leaves.
        Box(
            Modifier
                .fillMaxSize()
                .alpha(appear * (1f - eased))
                .background(
                    Brush.radialGradient(
                        0f to Color(0xE6000000),
                        0.55f to Color(0xB3000000),
                        1f to Color.Transparent,
                        center = androidx.compose.ui.geometry.Offset(Dimens.DesignWidth / 2f, Dimens.DesignHeight / 2f),
                        radius = 760f
                    )
                )
        )
        Image(
            painter = painterResource(R.drawable.minima_logo),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .requiredHeight(66.dp)
                .graphicsLayer {
                    alpha = appear * (1f - eased)
                    scaleX = Scale
                    scaleY = Scale
                }
        )
    }
}
