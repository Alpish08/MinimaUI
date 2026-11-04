package com.minimaui.app.ui.menu

import androidx.compose.runtime.getValue
import com.minimaui.app.ui.components.backgroundOutline
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.minimaui.app.R
import com.minimaui.app.ui.components.ControlHint
import com.minimaui.app.ui.components.ControlHintBar
import com.minimaui.app.ui.components.EaseInOut
import com.minimaui.app.ui.components.FocusRing
import com.minimaui.app.ui.components.GlowRect
import com.minimaui.app.ui.components.SelectionGlow
import com.minimaui.app.ui.components.UnfocusedOutline
import com.minimaui.app.ui.components.softOutline
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.MenuPanelFill
import com.minimaui.app.ui.theme.MenuScrim

/** What the Favorites launcher tile offers: Folders, Recently Played, Random Game, Settings, Apps, Search (a 3x2 grid). */
const val MoreMenuEntries = 6
const val MoreMenuColumns = 3

private const val PanelX = 535f
private const val PanelY = 240f
private const val PanelW = 850f
private const val PanelH = 600f
private const val SlotSize = 190f
private const val SlotPitch = 242f
private const val RowPitch = 250f
private const val SlotCorner = 44f
private const val SlotGrow = 0.11f
private val SlotFill = androidx.compose.ui.graphics.Color(0xFF2B2B2B)
private val SlotFillFocused = androidx.compose.ui.graphics.Color(0xFF1B1B1B)

private fun centerX(i: Int) = 960f + (i % MoreMenuColumns - 1f) * SlotPitch
private fun centerY(i: Int) = 540f + (i / MoreMenuColumns - 0.5f) * RowPitch

/**
 * The small menu the Favorites launcher tile opens: a row of square choices (Folder, Recently Played, Random Game). The chosen one is a little larger, with the standard glow and ring.
 */
@Composable
fun FavoritesMoreMenu(
    selected: Int,
    hints: List<ControlHint>,
    modifier: Modifier = Modifier,
    onTapSlot: (Int) -> Unit = {},
    onTapOutside: () -> Unit = {},
) {
    val density = LocalDensity.current.density
    val icons = listOf(R.drawable.ic_folder, R.drawable.ic_history, R.drawable.ic_dice, R.drawable.ic_menu, R.drawable.ic_apps, R.drawable.ic_search)
    val iconSizes = listOf(92, 112, 114, 96, 94, 105)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MenuScrim)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val x = offset.x / density
                    val y = offset.y / density
                    if (x < PanelX || x > PanelX + PanelW || y < PanelY || y > PanelY + PanelH) {
                        onTapOutside()
                        return@detectTapGestures
                    }
                    for (i in 0 until MoreMenuEntries) {
                        if (kotlin.math.abs(x - centerX(i)) <= SlotSize / 2f && kotlin.math.abs(y - centerY(i)) <= SlotSize / 2f) onTapSlot(i)
                    }
                }
            }
    ) {
        val panelShape = RoundedCornerShape(120.dp)
        Box(
            Modifier
                .offset(PanelX.dp, PanelY.dp)
                .size(PanelW.dp, PanelH.dp)
                .clip(panelShape)
                .background(MenuPanelFill)
                .backgroundOutline(depth = 28.dp, corner = 120.dp)
        )
        SelectionGlow(GlowRect(centerX(selected) - SlotSize / 2f, centerY(selected) - SlotSize / 2f, SlotSize, SlotSize, SlotCorner))
        for (i in 0 until MoreMenuEntries) {
            val grow by animateFloatAsState(if (i == selected) 1f + SlotGrow else 1f, tween(200, easing = EaseInOut), label = "moreGrow")
            val shape = RoundedCornerShape(SlotCorner.dp)
            Box(
                modifier = Modifier
                    .offset((centerX(i) - SlotSize / 2f).dp, (centerY(i) - SlotSize / 2f).dp)
                    .size(SlotSize.dp)
                    .graphicsLayer {
                        scaleX = grow
                        scaleY = grow
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
                    .clip(shape)
                    .background(if (i == selected) SlotFillFocused else SlotFill)
                    // The covers' soft outline instead of a hard border; the chosen slot gets the ring.
                    .softOutline(depth = 21.dp, corner = SlotCorner.dp) { UnfocusedOutline },
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(icons[i]), contentDescription = null, modifier = Modifier.size(iconSizes[i].dp))
            }
        }
        FocusRing(
            GlowRect(centerX(selected) - SlotSize / 2f, centerY(selected) - SlotSize / 2f, SlotSize, SlotSize, SlotCorner),
            scale = { 1f + SlotGrow },
            // The covers' soft white-to-transparent focus outline.
            fadeDepth = 21.dp,
            underStrength = UnfocusedOutline
        )
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
