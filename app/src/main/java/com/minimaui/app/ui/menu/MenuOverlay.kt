package com.minimaui.app.ui.menu

import com.minimaui.app.ui.components.verticalDragSteps
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.minimaui.app.ui.components.EaseInOut
import com.minimaui.app.ui.components.progressiveBlur
import com.minimaui.app.ui.components.ScrollSlider
import com.minimaui.app.ui.components.ControlHint
import com.minimaui.app.ui.components.ControlHintBar
import com.minimaui.app.ui.components.FocusRing
import com.minimaui.app.ui.components.GlowRect
import com.minimaui.app.ui.components.ListGrow
import com.minimaui.app.ui.components.PageDots
import com.minimaui.app.ui.components.listGrowth
import com.minimaui.app.ui.components.SelectionGlow
import com.minimaui.app.ui.components.UnfocusedOutline
import com.minimaui.app.ui.components.backgroundOutline
import com.minimaui.app.ui.components.outlineDepthFor
import com.minimaui.app.ui.components.pillOutline
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.DotDim
import com.minimaui.app.ui.theme.MenuPillActive
import com.minimaui.app.ui.theme.MenuPillIdle
import com.minimaui.app.ui.theme.GlowColor
import com.minimaui.app.ui.theme.IndicatorOn
import com.minimaui.app.ui.theme.MenuPanelFill
import com.minimaui.app.ui.theme.TextPrimary

enum class MenuFocus { Categories, Actions }

/** [HiddenList] is a scrolling list of plain pills (the games taken out of the library), laid out like the ROM folders. */
enum class MenuLayout { List, Settings, Grid, Folders, Audio, HiddenList }

/** The left-hand list. [pages] > 1 makes the category swipeable: Customization has three (settings, audio, platforms); Edit Current Platform gets a second one (the hidden games) by itself. */
enum class MenuCategory(val title: String, val pages: Int = 1) {
    EditGame("Edit Current Game"),
    Customize("Customization", pages = 3),
    RomFolders("ROMs Folder(s)"),
    Backup("Backup / Restore");

    /** How the entries of page [page] are arranged. */
    fun layout(page: Int): MenuLayout = when (this) {
        EditGame -> if (page == 1) MenuLayout.HiddenList else MenuLayout.List
        Backup -> MenuLayout.List
        Customize -> when (page) {
            0 -> MenuLayout.Settings
            1 -> MenuLayout.Audio
            else -> MenuLayout.Grid
        }
        RomFolders -> MenuLayout.Folders
    }
}

/**
 * One selectable entry in the right-hand column. [dot]: null = no indicator, true = a white dot, false = a dim one.
 * [slider]: a 0..100 value, which makes the entry a volume-style slider card. [danger]: a destructive entry, shown
 * red, smaller and set apart at the bottom of a list so it isn't pressed by accident.
 */
class MenuEntry(val label: String, val dot: Boolean? = null, val slider: Int? = null, val danger: Boolean = false, val plus: Boolean = false)

/** A rectangle inside the menu panel, in design pixels. */
private class UiRect(val x: Float, val y: Float, val width: Float, val height: Float)

private fun UiRect.grown(byW: Float, byH: Float = 0f) = UiRect(x - byW / 2f, y - byH / 2f, width + byW, height + byH)

private fun UiRect.toGlow(corner: Float = height / 2f) = GlowRect(x, y, width, height, corner)

// The darker box holding the sub-settings, and the centre every entry layout is arranged around.
private val SubBox = UiRect(718f, 43f, 926f, 759f)
private const val SubBoxCorner = 56f
private const val SubCenterX = 1181f
private const val SubCenterY = 422.5f

// The settings, audio and platform pages sit a little above the middle, which leaves room for the page dots under them.
private const val PagedCenterY = 400f
private const val PageDotsY = 769f
private const val PageDotSize = 18f
private const val PageDotGap = 10f

// Slim pills, centred vertically in the panel (as designed) and between the panel's left edge and the sub-box; the highlighted one is wider, its
// neighbours half as much.
private const val EntriesFadeOutMillis = 90
private const val EntriesFadeInMillis = 140

/** What the entries box showed last, so it can fade out while the next category's entries wait. */
private class ShownEntries(var category: MenuCategory, var page: Int, var selected: Int, var pages: List<List<MenuEntry>>)

private const val CategoryGrow = 67f
private const val CategoryNeighbor = 0.5f
private fun categoryRect(index: Int): UiRect {
    val count = MenuCategory.entries.size
    return UiRect(130f, 160f + (5 - count) * 53f + index * 106f, 456f, 82f)
}

// Lists are centred vertically in the panel; the grid and folder layouts have fixed tops.
private fun entryRect(layout: MenuLayout, index: Int, count: Int, scroll: Float = 0f, dangerLast: Boolean = false, paged: Boolean = true): UiRect = when (layout) {
    MenuLayout.List -> if (dangerLast) {
        // The regular entries stack, then a gap, then the small destructive one; the whole group is centred in the box.
        // With five or more regular entries the pills are slimmer so the group still fits.
        val regular = count - 1
        val tight = regular >= 5
        val pitch = if (tight) 114f else 134f
        val h = if (tight) 90f else 112f
        // The destructive entry sits as far from the last one as they are from each other.
        val gap = pitch - h
        val dangerH = if (tight) 66f else 72f
        val group = (regular - 1) * pitch + h + gap + dangerH
        // A little above the middle when page dots sit under it; in the middle when there are none.
        val top = (if (paged) PagedCenterY else SubCenterY) - group / 2f
        if (index < regular) UiRect(SubCenterX - 309f, top + index * pitch, 618f, h)
        else UiRect(SubCenterX - 195f, top + (regular - 1) * pitch + h + gap, 390f, dangerH)
    } else {
        // Fewer entries sit further apart; five or more squeeze together to fit the box.
        val pitch = when {
            count <= 3 -> 197f
            count == 4 -> 164f
            count == 5 -> 135f
            else -> 675f / count
        }
        val height = if (count <= 4) 123f else minOf(112f, pitch * 0.83f)
        UiRect(SubCenterX - 309f, SubCenterY - (count - 1) * pitch / 2f - height / 2f + index * pitch, 618f, height)
    }
    // Customization, page 1: slim pills close together.
    MenuLayout.Settings -> {
        // Five entries sit a little closer; more than five scroll, five showing at a time.
        val tight = count >= 5
        val pitch = if (tight) SettingsPitch else 138f
        val h = if (tight) SettingsRowHeight else 112f
        val shown = minOf(count, SettingsRowsShown)
        UiRect(SubCenterX - 309f, PagedCenterY - ((shown - 1) * pitch + h) / 2f + index * pitch - scroll, 618f, h)
    }
    // Page 2: sounds, music, pick, volume.
    MenuLayout.Audio -> when (index) {
        0 -> UiRect(855f, 119f, 652f, 117f)
        1 -> UiRect(855f, 269f, 652f, 118f)
        2 -> UiRect(SubCenterX - 155f, 419f, 310f, 92f)
        else -> UiRect(855f, 545f, 652f, 138f)
    }
    // Page 3: the platforms, two to a row.
    MenuLayout.Grid -> {
        val column = index % 2
        // Five rows show at a time; more scroll.
        val rows = minOf((count + 1) / 2, GridRowsShown)
        val top = PagedCenterY + 14f - ((rows - 1) * GridPitch + 80f) / 2f
        UiRect(if (column == 0) 852f else 1203f, top + index / 2 * GridPitch - scroll, 298f, 80f)
    }
    MenuLayout.Folders ->
        if (index < count - 1) {
            UiRect(SubCenterX - FolderRowWidth / 2f, FolderFirstY + index * FolderPitch - scroll, FolderRowWidth, FolderRowHeight)
        } else {
            UiRect(SubCenterX - PlusWidth / 2f, FolderFirstY + index * FolderPitch + (FolderRowHeight - PlusHeight) / 2f - scroll, PlusWidth, PlusHeight)
        }
    MenuLayout.HiddenList -> UiRect(SubCenterX - FolderRowWidth / 2f, HiddenFirstY + index * HiddenPitch - scroll, FolderRowWidth, HiddenRowHeight)
}

// In the settings the focused entry grows by ListGrow and its neighbours by a bit less than half of that.
private const val SettingsNeighbor = 0.3f

// Only the focused platform tile grows, and only slightly: a bit wider and taller.
private const val GridGrowW = 16f
private const val GridGrowH = 8f

/** The (width, height) growth of entry [i] when [selected] has the cursor, in design px. */
private fun entryGrowth(layout: MenuLayout, i: Int, selected: Int): Pair<Float, Float> = when (layout) {
    MenuLayout.List, MenuLayout.Settings, MenuLayout.Folders, MenuLayout.HiddenList -> listGrowth((i - selected).toFloat(), ListGrow, SettingsNeighbor) to 0f
    MenuLayout.Grid -> {
        // Only the focused tile.
        if (i == selected) GridGrowW to GridGrowH else 0f to 0f
    }
    MenuLayout.Audio -> {
        // Rows: sounds, music, pick, volume. The half-width button grows half as much.
        listGrowth(kotlin.math.abs(i - selected).toFloat(), if (i == 2) ListGrow / 2f else ListGrow, SettingsNeighbor) to 0f
    }
}

// The + pill at the end of the ROM folders.
private const val PlusWidth = 132f
private const val PlusHeight = 62f

private const val FolderPitch = 112f
private const val FolderRowHeight = 88f
private const val FolderRowWidth = 540f
private const val FolderRowsShown = 5
private const val FolderFirstY = 162f
private const val FolderCenterY = SubCenterY

// The platforms page: two columns, five rows at a time.
private const val GridRowsShown = 5
private const val GridPitch = 123f
private const val GridFirstY = PagedCenterY + 14f - ((GridRowsShown - 1) * GridPitch + 80f) / 2f

// The first page of Customization: five rows at a time, the pills keep their size however many entries there are.
private const val SettingsRowsShown = 5
private const val SettingsPitch = 126f
private const val SettingsRowHeight = 104f
private const val SettingsFirstY = PagedCenterY - ((SettingsRowsShown - 1) * SettingsPitch + SettingsRowHeight) / 2f

// The hidden games: five rows at a time, the ones at the ends fading out like the search results.
private const val HiddenRowsShown = 5
private const val HiddenPitch = 118f
private const val HiddenRowHeight = 92f
private const val HiddenTitleHeight = 36f
private const val HiddenTrackHeight = 156f
private const val HiddenFirstY = SubCenterY - ((HiddenRowsShown - 1) * HiddenPitch + HiddenRowHeight) / 2f

/**
 * How visible a hidden-games row is: the rows at the ends of the five fade out gradually (the outermost is half visible, the
 * next one gone), but only at an end that has more games beyond it, so the first and last games of the list stay whole.
 */
private fun hiddenFade(rect: UiRect, scroll: Float, count: Int, firstY: Float = HiddenFirstY, pitch: Float = HiddenPitch): Float {
    val slot = (rect.y - firstY) / pitch
    val above = (scroll / pitch).coerceIn(0f, 1f)
    val below = (count - HiddenRowsShown - scroll / pitch).coerceIn(0f, 1f)
    val top = 1f + (((slot + 1f) / 2f).coerceIn(0f, 1f) - 1f) * above
    val bottom = 1f + (((HiddenRowsShown - slot) / 2f).coerceIn(0f, 1f) - 1f) * below
    return minOf(top, bottom)
}

/** Folder rows fade out toward the top and bottom edges of the sub-box. */
private fun folderFade(rect: UiRect): Float {
    val centerY = rect.y + rect.height / 2f
    return ((centerY - SubBox.y) / 150f).coerceIn(0f, 1f) * ((SubBox.y + SubBox.height - centerY) / 150f).coerceIn(0f, 1f)
}

private fun scrollFade(layout: MenuLayout, rect: UiRect, scroll: Float, count: Int): Float =
    when (layout) {
        MenuLayout.HiddenList -> hiddenFade(rect, scroll, count)
        MenuLayout.Settings -> hiddenFade(rect, scroll, count, SettingsFirstY, SettingsPitch)
        MenuLayout.Grid -> hiddenFade(rect, scroll, (count + 1) / 2, GridFirstY, GridPitch)
        else -> folderFade(rect)
    }

/** The layouts whose list scrolls (and fades at its ends). */
private val MenuLayout.scrolls: Boolean get() = this == MenuLayout.Folders || this == MenuLayout.HiddenList || this == MenuLayout.Settings || this == MenuLayout.Grid

private fun folderScrollFor(layout: MenuLayout, count: Int, selected: Int): Float =
    if (layout == MenuLayout.HiddenList) {
        // The selected row stays in the middle of the five, except near the ends of the list.
        (selected - HiddenRowsShown / 2).coerceIn(0, maxOf(count - HiddenRowsShown, 0)) * HiddenPitch
    } else if (layout == MenuLayout.Settings) {
        (selected - SettingsRowsShown / 2).coerceIn(0, maxOf(count - SettingsRowsShown, 0)) * SettingsPitch
    } else if (layout == MenuLayout.Grid) {
        // The row of the selected platform stays in the middle of the five, except near the ends.
        (selected / 2 - GridRowsShown / 2).coerceIn(0, maxOf((count + 1) / 2 - GridRowsShown, 0)) * GridPitch
    } else if (layout.scrolls && count > FolderRowsShown) maxOf(FolderFirstY + selected * FolderPitch + FolderRowHeight / 2f - FolderCenterY, 0f) else 0f

/**
 * Two-column settings menu: categories on the left, the highlighted category's entries on the right. A category with
 * several pages (Customization) shows [page] of them in the box, swipeable by touch ([onSwipePage]), with page dots
 * under them. [entriesFor] gives the entries of any page, so a neighbouring page can slide in next to the current one.
 */
@Composable
fun MenuOverlay(
    category: MenuCategory,
    focus: MenuFocus,
    page: Int,
    entriesFor: (Int) -> List<MenuEntry>,
    pages: Int,
    selected: Int,
    hints: List<ControlHint>,
    editTitle: String,
    modifier: Modifier = Modifier,
    onTapCategory: (Int) -> Unit = {},
    onTapEntry: (Int) -> Unit = {},
    onTapOutside: () -> Unit = {},
    onSwipePage: (delta: Int) -> Unit = {},
    onSeekVolume: (Int) -> Unit = {},
    onStepList: (delta: Int) -> Unit = {},
    onSeekList: (fraction: Float) -> Unit = {},
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val panelShape = RoundedCornerShape(Dimens.PanelCorner)
    val subBoxShape = RoundedCornerShape(SubBoxCorner.dp)
    val layout = category.layout(page)
    val entries = entriesFor(page)
    // Page dots under the entries (the settings, audio and platform pages, and the hidden games) push them a little up.
    val paged = pages > 1
    val scope = rememberCoroutineScope()

    // Where the pages are, in page units (a drag moves it with the finger, otherwise it eases to the current page).
    val position = remember(category) { Animatable(page.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(category, page) {
        if (!dragging) position.animateTo(page.toFloat(), tween(360, easing = EaseInOut))
    }

    val folderScroll = folderScrollFor(layout, entries.size, selected)
    val dangerLast = entries.lastOrNull()?.danger == true

    // Changing category fades the entries out, swaps them, and fades them in; the old ones are kept for the fade-out.
    var shownCategory by remember { mutableStateOf(category) }
    val settled = shownCategory == category
    val entriesFade = remember { Animatable(1f) }
    val lastShown = remember { ShownEntries(category, page, selected, List(pages) { entriesFor(it) }) }
    if (settled) {
        lastShown.category = category
        lastShown.page = page
        lastShown.selected = selected
        lastShown.pages = List(pages) { if (it == page) entries else entriesFor(it) }
    }
    androidx.compose.runtime.LaunchedEffect(category) {
        if (shownCategory == category) return@LaunchedEffect
        entriesFade.animateTo(0f, tween(EntriesFadeOutMillis))
        shownCategory = category
        entriesFade.animateTo(1f, tween(EntriesFadeInMillis))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Touch: tap a category, an entry or a page dot, or outside the panel to close the menu.
            .pointerInput(category, page, entries.size, folderScroll) {
                detectTapGestures { offset ->
                    val x = offset.x / density - Dimens.PanelX.value
                    val y = offset.y / density - Dimens.PanelY.value
                    if (x < 0f || y < 0f || x > Dimens.PanelWidth.value || y > Dimens.PanelHeight.value) {
                        onTapOutside()
                        return@detectTapGestures
                    }
                    fun UiRect.contains() = x >= this.x && x <= this.x + width && y >= this.y && y <= this.y + height
                    MenuCategory.entries.indices.firstOrNull { categoryRect(it).contains() }?.let {
                        onTapCategory(it)
                        return@detectTapGestures
                    }
                    if (pages > 1 && y in (PageDotsY - 24f)..(PageDotsY + 24f)) {
                        // The dots: a tap on one goes to its page.
                        val pitch = PageDotSize + PageDotGap
                        val index = ((x - SubCenterX) / pitch + (pages - 1) / 2f).roundToInt().coerceIn(0, pages - 1)
                        if (kotlin.math.abs(x - (SubCenterX + (index - (pages - 1) / 2f) * pitch)) <= 30f) onSwipePage(index - page)
                        return@detectTapGestures
                    }
                    entries.indices.firstOrNull { i ->
                        entryRect(layout, i, entries.size, folderScroll, dangerLast, paged).contains() &&
                            (!layout.scrolls || scrollFade(layout, entryRect(layout, i, entries.size, folderScroll, dangerLast, paged), folderScroll, entries.size) > 0.3f)
                    }?.let { onTapEntry(it) }
                }
            }
            // Touch: drag up or down to scroll a long list (the ROM folders).
            .then(if (layout.scrolls) Modifier.verticalDragSteps(95f, onStepList) else Modifier)
            // Touch: drag sideways to turn the pages; they follow the finger and settle on release.
            .pointerInput(category, page) {
                if (pages <= 1) return@pointerInput
                val width = SubBox.width * density
                var start = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        start = position.value
                    },
                    onDragEnd = {
                        dragging = false
                        val moved = position.value - start
                        val delta = if (moved > 0.18f) 1 else if (moved < -0.18f) -1 else 0
                        val target = (page + delta).coerceIn(0, pages - 1)
                        if (target != page) onSwipePage(target - page)
                        scope.launch { position.animateTo(target.toFloat(), tween(300, easing = EaseInOut)) }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { position.animateTo(page.toFloat(), tween(300, easing = EaseInOut)) }
                    },
                ) { _, dx ->
                    scope.launch { position.snapTo((position.value - dx / width).coerceIn(-0.2f, pages - 0.8f)) }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .offset(Dimens.PanelX, Dimens.PanelY)
                .size(Dimens.PanelWidth, Dimens.PanelHeight)
                .clip(panelShape)
                .background(MenuPanelFill)
                .backgroundOutline(depth = 24.dp, corner = Dimens.PanelCorner)
        ) {
            // The darker box the sub-settings sit in.
            Box(
                modifier = Modifier
                    .offset(SubBox.x.dp, SubBox.y.dp)
                    .size(SubBox.width.dp, SubBox.height.dp)
                    .clip(subBoxShape)
                    .background(SubBoxFill)
                    .backgroundOutline(depth = 22.dp, corner = SubBoxCorner.dp)
            )

            // Only the part of the menu being navigated glows; the category left behind keeps just its white border.
            SelectionGlow(if (focus == MenuFocus.Categories) categoryRect(category.ordinal).grown(CategoryGrow).toGlow() else null)

            MenuCategory.entries.forEachIndexed { i, item ->
                // The highlighted category is wider than the others while you are choosing among them.
                val grow by animateFloatAsState(
                    if (focus == MenuFocus.Categories) listGrowth((i - category.ordinal).toFloat(), CategoryGrow, CategoryNeighbor) else 0f,
                    tween(200),
                    label = "categoryGrow"
                )
                MenuPill(
                    title = if (item == MenuCategory.EditGame) editTitle else item.title,
                    active = i == category.ordinal,
                    rect = categoryRect(i).grown(grow),
                    fontSize = 32.sp
                )
            }

            FocusRing(
                categoryRect(category.ordinal).grown(if (focus == MenuFocus.Categories) CategoryGrow else 0f).toGlow(),
                pulsing = focus == MenuFocus.Categories,
                fadeDepth = outlineDepthFor(categoryRect(category.ordinal).height),
                underStrength = UnfocusedOutline
            )

            // The pages, side by side in the sub-box; each has its own glow, pills and focus ring, and slides with the others.
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = entriesFade.value }) {
            val shownPages = lastShown.pages.size
            val shownPage = if (settled) page else lastShown.page
            for (p in 0 until shownPages) {
                val distance = p - (if (settled) position.value else lastShown.page.toFloat())
                if (kotlin.math.abs(distance) >= 1f) continue
                val here = p == shownPage
                // The box stays put and clips; the page slides inside it (so it never leaves the darker sub-box).
                Box(
                    modifier = Modifier
                        .offset(SubBox.x.dp, SubBox.y.dp)
                        .size(SubBox.width.dp, SubBox.height.dp)
                        .clip(subBoxShape)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .offset { IntOffset((distance * SubBox.width * density).roundToInt(), 0) }
                            .graphicsLayer { alpha = (1f - kotlin.math.abs(distance) * 1.4f).coerceIn(0f, 1f) }
                    ) {
                        EntryPage(
                            layout = lastShown.category.layout(p),
                            entries = if (settled) (if (here) entries else entriesFor(p)) else lastShown.pages[p],
                            selected = if (!here) -1 else if (settled) selected else lastShown.selected,
                            focused = here && focus == MenuFocus.Actions && settled,
                            // The entries leaving while the category changes keep their own (old) layout.
                            paged = (if (settled) pages else lastShown.pages.size) > 1,
                            onSeekVolume = onSeekVolume,
                            onSeekList = onSeekList,
                        )
                    }
                }
            }

            }

            if (pages > 1) {
                Box(
                    modifier = Modifier
                        .offset(SubBox.x.dp, (PageDotsY - PageDotSize / 2f).dp)
                        .size(SubBox.width.dp, PageDotSize.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PageDots(
                        modifier = Modifier.graphicsLayer { alpha = entriesFade.value },
                        count = pages,
                        current = page,
                        vertical = false,
                        activeSize = PageDotSize.dp,
                        inactiveSize = 14.dp,
                        gap = PageDotGap.dp
                    )
                }
            }
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

/** One page of entries, in the panel's own coordinates (the box this sits in is the sub-box, so everything is shifted back). */
@Composable
private fun EntryPage(
    layout: MenuLayout,
    entries: List<MenuEntry>,
    selected: Int,
    focused: Boolean,
    paged: Boolean,
    onSeekVolume: (Int) -> Unit,
    onSeekList: (Float) -> Unit,
) {
    val dangerLast = entries.lastOrNull()?.danger == true
    val folderScroll = folderScrollFor(layout, entries.size, selected.coerceAtLeast(0))
    val animatedScroll by animateFloatAsState(folderScroll, tween(200), label = "folderScroll")
    val selectedGrow = if (selected in entries.indices) entryGrowth(layout, selected, selected) else 0f to 0f

    Box(
        Modifier.layout { measurable, constraints ->
            // The panel-sized layer, placed back at the panel's origin (it is bigger than the box it sits in).
            val placeable = measurable.measure(Constraints.fixed(Dimens.PanelWidth.roundToPx(), Dimens.PanelHeight.roundToPx()))
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place((-SubBox.x).dp.roundToPx(), (-SubBox.y).dp.roundToPx()) }
        }
    ) {
        // Glow sits behind every item so it shows through neighbouring (translucent) pills.
        SelectionGlow(
            if (focused && selected in entries.indices) {
                entryRect(layout, selected, entries.size, folderScroll, dangerLast, paged).grown(selectedGrow.first, selectedGrow.second).toGlow()
            } else {
                null
            },
            color = if (selected in entries.indices && entries[selected].danger) DangerRed else GlowColor
        )

        entries.forEachIndexed { i, entry ->
            // The focused entry grows, and its neighbours a little.
            val growTarget = if (focused) entryGrowth(layout, i, selected) else 0f to 0f
            val growW by animateFloatAsState(growTarget.first, tween(200), label = "entryGrowW")
            val growH by animateFloatAsState(growTarget.second, tween(200), label = "entryGrowH")
            val rect = entryRect(layout, i, entries.size, animatedScroll, dangerLast, paged).grown(growW, growH)
            val alpha = if (layout.scrolls) scrollFade(layout, rect, animatedScroll, entries.size) else 1f
            if (alpha <= 0f) return@forEachIndexed
            val active = focused && i == selected
            if (entry.plus) {
                PlusPill(active = active, rect = rect, alpha = alpha)
            } else if (entry.slider != null) {
                SliderCard(title = entry.label, value = entry.slider, active = active, rect = rect, onSeek = onSeekVolume)
            } else {
                MenuPill(
                    title = entry.label,
                    active = active,
                    rect = rect,
                    dot = entry.dot,
                    textColor = if (entry.danger) DangerRed else TextPrimary,
                    alpha = alpha,
                    textPadding = if (layout == MenuLayout.Audio && i == 2) 14.dp else 40.dp,
                    dotInset = when (layout) {
                        MenuLayout.List, MenuLayout.Settings, MenuLayout.Audio -> 38.dp
                        else -> 25.dp
                    },
                    fontSize = when (layout) {
                        MenuLayout.List -> if (entry.danger) 26.sp else if (dangerLast && entries.size >= 6) 32.sp else 34.sp
                        MenuLayout.Settings -> 34.sp
                        MenuLayout.Audio -> if (i == 2) 26.sp else 34.sp
                        MenuLayout.Grid -> 28.sp
                        MenuLayout.Folders, MenuLayout.HiddenList -> 29.sp
                    }
                )
            }
        }

        // The entry's outline pulses.
        FocusRing(
            if (focused && selected in entries.indices) {
                entryRect(layout, selected, entries.size, folderScroll, dangerLast, paged)
                    .grown(selectedGrow.first, selectedGrow.second)
                    .toGlow(if (entries[selected].slider != null) 60f else entryRect(layout, selected, entries.size, 0f, dangerLast, paged).height / 2f)
            } else {
                null
            },
            fadeDepth = outlineDepthFor(
                if (selected in entries.indices) entryRect(layout, selected, entries.size, 0f, dangerLast, paged).height else 0f
            ),
            underStrength = UnfocusedOutline
        )

        val listTitle = when (layout) {
            MenuLayout.HiddenList -> "Hidden Games"
            MenuLayout.Grid -> if (entries.isNotEmpty()) "Platforms" else null
            else -> null
        }
        if (listTitle != null) {
            // Halfway between the top of the box and the first row.
            val titleY = (SubBox.y + entryRect(layout, 0, entries.size, 0f, dangerLast, paged).y) / 2f - HiddenTitleHeight / 2f
            Box(
                Modifier.offset(SubBox.x.dp, titleY.dp).size(SubBox.width.dp, HiddenTitleHeight.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = listTitle, color = TextPrimary.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1)
            }
        }

        val scrollable = when (layout) {
            MenuLayout.Grid -> entries.size > GridRowsShown * 2
            MenuLayout.Settings -> entries.size > SettingsRowsShown
            else -> layout.scrolls && entries.size > FolderRowsShown
        }
        if (scrollable) {
            val trackHeight = if (layout == MenuLayout.Folders) 125f else HiddenTrackHeight
            val trackCenter = if (layout == MenuLayout.Settings || layout == MenuLayout.Grid) PagedCenterY else SubCenterY
            val fraction = if (layout == MenuLayout.Grid) {
                (selected.coerceIn(0, entries.lastIndex) / 2) / ((entries.size + 1) / 2 - 1).toFloat()
            } else {
                selected.coerceIn(0, entries.lastIndex) / (entries.size - 1).toFloat()
            }
            ScrollSlider(1585f, trackCenter - trackHeight / 2f, trackHeight, fraction, onSeek = onSeekList)
        }
    }
}

@Composable
private fun MenuPill(
    title: String,
    active: Boolean,
    rect: UiRect,
    fontSize: TextUnit,
    textColor: Color = TextPrimary,
    dot: Boolean? = null,
    dotInset: Dp = 30.dp,
    textPadding: Dp = 40.dp,
    alpha: Float = 1f,
) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .offset(rect.x.dp, rect.y.dp)
            .size(rect.width.dp, rect.height.dp)
            // After the offset and size, so the faded layer is the pill itself (a layer placed before them would be cut to its own bounds at the origin).
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(if (active) MenuPillActive else MenuPillIdle)
            .pillOutline(outlineDepthFor(rect.height)) { UnfocusedOutline },
        contentAlignment = Alignment.Center
    ) {
        if (dot != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = dotInset)
                    .size(20.dp)
                    .background(if (dot) IndicatorOn else DotDim, CircleShape)
            )
        }
        Text(
            text = title,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (dot != null) Modifier.padding(start = 56.dp, end = 20.dp) else Modifier.padding(horizontal = textPadding)
        )
    }
}

@Composable
private fun PlusPill(active: Boolean, rect: UiRect, alpha: Float = 1f) {
    Box(
        modifier = Modifier
            .offset(rect.x.dp, rect.y.dp)
            .size(rect.width.dp, rect.height.dp)
            // After the offset and size, so the faded layer is the pill itself (a layer placed before them would be cut to its own bounds at the origin).
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(if (active) MenuPillActive else MenuPillIdle)
            .pillOutline(outlineDepthFor(rect.height)) { UnfocusedOutline },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size((minOf(rect.width, rect.height) * 0.36f).dp)) {
            val stroke = size.width * 0.19f
            drawLine(TextPrimary, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke, StrokeCap.Round)
            drawLine(TextPrimary, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), stroke, StrokeCap.Round)
        }
    }
}

// Darker than the panel, so the box stands out as its own section.
private val SubBoxFill = Color(0xCC0A0A0A)

private val DangerRed = Color(0xFFFF3A3A)

// The volume card's bar: the same look as the on-screen volume / brightness bar (an outlined track with a white fill inside it).
private const val BarHeight = 38f
private const val BarInset = 5f
private val BarTrackFill = Color(0x66000000)

/** The volume card: "Volume: 35%" over a thick bar (as in the volume overlay). Touch: tap or drag the bar to set the level. */
@Composable
private fun SliderCard(title: String, value: Int, active: Boolean, rect: UiRect, onSeek: (Int) -> Unit) {
    val shape = RoundedCornerShape(60.dp)
    val barWidth = rect.width - 86f
    Box(
        modifier = Modifier
            .offset(rect.x.dp, rect.y.dp)
            .size(rect.width.dp, rect.height.dp)
            .clip(shape)
            .background(if (active) MenuPillActive else MenuPillIdle)
            .pillOutline(outlineDepthFor(rect.height), corner = 60.dp) { UnfocusedOutline }
    ) {
        Text(
            text = "$title: $value%",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp)
        )
        val innerHeight = BarHeight - 2 * BarInset
        val innerWidth = barWidth - 2 * BarInset
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        Box(
            modifier = Modifier
                .offset(43.dp, 70.dp)
                .size(barWidth.dp, BarHeight.dp)
                .clip(CircleShape)
                .background(BarTrackFill)
                .backgroundOutline(depth = 10.dp)
                .pointerInput(barWidth) {
                    detectTapGestures { onSeek(((it.x / density - BarInset) / innerWidth * 100f).roundToInt().coerceIn(0, 100)) }
                }
                .pointerInput(barWidth) {
                    detectHorizontalDragGestures { change, _ ->
                        onSeek(((change.position.x / density - BarInset) / innerWidth * 100f).roundToInt().coerceIn(0, 100))
                    }
                }
                .padding(BarInset.dp)
        ) {
            if (value > 0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((value / 100f).coerceAtLeast(innerHeight / innerWidth))
                        .background(Color.White, CircleShape)
                )
            }
        }
    }
}
