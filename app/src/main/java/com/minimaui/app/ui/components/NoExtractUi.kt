package com.minimaui.app.ui.components

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Text fields inside ask the keyboard not to take over the screen. In landscape the keyboard otherwise switches
 * to a full-screen "extract" mode that hides MinimaUI behind a giant text box.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoExtractUi(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            nextHandler.startInputMethod(object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                    val connection = request.createInputConnection(outAttributes)
                    outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                    return connection
                }
            })
        },
        content = content
    )
}
