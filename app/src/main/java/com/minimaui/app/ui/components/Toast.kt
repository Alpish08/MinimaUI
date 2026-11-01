package com.minimaui.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.PillSurface
import com.minimaui.app.ui.theme.TextPrimary

/** A short message in a pill at the bottom centre (like "Sort: Alphabetical"); [alpha] is read at draw time. */
@Composable
fun ToastPill(text: String, alpha: () -> Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) }) {
        Text(
            text = text,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Dimens.HintBarBottom)
                .background(PillSurface, CircleShape)
                .backgroundOutline(depth = 14.dp)
                .padding(horizontal = 58.dp, vertical = 22.dp)
        )
    }
}
