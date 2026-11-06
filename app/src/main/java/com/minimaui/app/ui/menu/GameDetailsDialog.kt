package com.minimaui.app.ui.menu

import com.minimaui.app.ui.components.backgroundOutline
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaui.app.ui.components.ControlHint
import com.minimaui.app.ui.components.ControlHintBar
import com.minimaui.app.ui.components.MarqueeText
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.MenuPanelFill
import com.minimaui.app.ui.theme.MenuPillIdle
import com.minimaui.app.ui.theme.TextPrimary

/** The SELECT-button card: a game's title, time played, favorite state and platform. */
@Composable
fun GameDetailsDialog(
    title: String,
    hoursPlayed: String,
    favorite: Boolean,
    platform: String,
    modifier: Modifier = Modifier,
) {
    val panelShape = RoundedCornerShape(Dimens.PanelCorner)
    val rows = listOf(
        "",
        "Playtime: $hoursPlayed",
        "Favorite: ${if (favorite) "Yes" else "No"}",
        "Platform: $platform",
    )
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset(416.dp, 280.dp)
                .size(1088.dp, 520.dp)
                .clip(panelShape)
                .background(MenuPanelFill)
                .backgroundOutline(depth = 24.dp, corner = Dimens.PanelCorner)
        ) {
            Text(
                text = "Game Details",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp)
            )
            rows.forEachIndexed { i, text ->
                Box(
                    modifier = Modifier
                        .offset(174.dp, (92f + i * 103.7f).dp)
                        .size(740.dp, 80.dp)
                        .clip(CircleShape)
                        .background(MenuPillIdle)
                        .backgroundOutline(depth = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (i == 0) {
                        // "Title: " stays put; a long title scrolls after it, like titles everywhere else.
                        Row(
                            modifier = Modifier.padding(horizontal = 40.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Title: ", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 34.sp, maxLines = 1)
                            Box(Modifier.weight(1f, fill = false)) {
                                MarqueeText(text = title, color = TextPrimary, fontSize = 34.sp, scrolling = true, startIndent = 24.dp, endIndent = 24.dp, centerWhenFits = true)
                            }
                        }
                    } else {
                        Text(
                            text = text,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 40.dp)
                        )
                    }
                }
            }
        }
        // On the screen's bottom right, where the page hints normally are, not inside the panel.
        ControlHintBar(
            hints = listOf(ControlHint("B", "Back")),
            pill = false,
            pageSize = true,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Dimens.HintBarEnd + 17.dp, bottom = Dimens.HintBarBottom + 9.dp)
        )
    }
}
