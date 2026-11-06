package com.minimaui.app.ui.menu

import com.minimaui.app.ui.components.outlineDepthFor
import com.minimaui.app.ui.components.backgroundOutline
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimaui.app.ui.components.ControlHint
import com.minimaui.app.ui.components.ControlHintBar
import com.minimaui.app.ui.components.GlowRect
import com.minimaui.app.ui.components.NoExtractUi
import com.minimaui.app.ui.components.SelectionGlow
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.MenuPanelFill
import com.minimaui.app.ui.components.FocusRing
import com.minimaui.app.ui.theme.MenuPillIdle
import com.minimaui.app.ui.theme.TextPrimary
import com.minimaui.app.ui.theme.TileSurfaceFocused

private val EditHints = listOf(ControlHint("A", "Save"), ControlHint("B", "Cancel"))

/**
 * Title editor. Sits in the upper half of the screen so the on-screen keyboard (which overlays the
 * bottom half, the window never resizes) doesn't cover it.
 */
@Composable
fun TitleEditDialog(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    heading: String = "Edit Game Title",
    searchStyle: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }

    val panelShape = RoundedCornerShape(Dimens.PanelCorner)
    val fieldShape = RoundedCornerShape(percent = 50)

    if (searchStyle) {
        SearchStyleEntry(heading, value, onValueChange, onSave, focusRequester, modifier)
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset(410.dp, 24.dp)
                .size(1100.dp, 340.dp)
                .clip(panelShape)
                .background(MenuPanelFill)
                .backgroundOutline(depth = 24.dp, corner = Dimens.PanelCorner)
        ) {
            Text(
                text = heading,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                modifier = Modifier.offset(100.dp, 46.dp)
            )
            SelectionGlow(GlowRect(100f, 120f, 900f, 106f))
            Box(
                modifier = Modifier.offset(100.dp, 120.dp).size(900.dp, 106.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(fieldShape)
                        .background(TileSurfaceFocused),
                    contentAlignment = Alignment.Center
                ) {
                    NoExtractUi { BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(TextPrimary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onSave() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 44.dp)
                            .focusRequester(focusRequester)
                    ) }
                }
            }
            FocusRing(GlowRect(100f, 120f, 900f, 106f), fadeDepth = outlineDepthFor(106f))
            ControlHintBar(
                hints = EditHints,
                pill = false,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 100.dp, bottom = 34.dp)
            )
        }
    }
}

/** The one-field entry in the search menu's layout: a heading, the field, and Enter / Back hints. */
@Composable
private fun SearchStyleEntry(
    heading: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSave: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier,
) {
    val panelShape = RoundedCornerShape(Dimens.PanelCorner)
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset(502.dp, 94.dp)
                .size(PanelWidth.dp, 272.dp)
                .clip(panelShape)
                .background(MenuPanelFill)
                .backgroundOutline(depth = 24.dp, corner = Dimens.PanelCorner)
        ) {
            SelectionGlow(GlowRect(FieldX, FieldY, FieldWidth, FieldHeight))
            Text(
                text = heading,
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
                    .background(MenuPillIdle),
                contentAlignment = Alignment.Center
            ) {
                NoExtractUi { BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 33.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(TextPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 44.dp)
                        .focusRequester(focusRequester)
                ) }
            }
            FocusRing(GlowRect(FieldX, FieldY, FieldWidth, FieldHeight), fadeDepth = outlineDepthFor(FieldHeight))
            ControlHintBar(
                hints = if (heading == "New Folder") listOf(ControlHint("A", "Create"), ControlHint("B", "Cancel")) else EnterBackHints,
                pill = false,
                fontSize = 24.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 72.dp, bottom = 30.dp)
            )
        }
    }
}

private val EnterBackHints = listOf(ControlHint("A", "Enter"), ControlHint("B", "Cancel"))
