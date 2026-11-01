package com.minimaui.app.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Finger-scrolling for a list that keeps one row selected: every [stepDp] the finger travels turns into one step, and
 * [onStep] gets +1 when the finger moves up (on to later rows) or -1 when it moves down.
 */
fun Modifier.verticalDragSteps(stepDp: Float, onStep: (Int) -> Unit): Modifier = composed {
    val step by rememberUpdatedState(onStep)
    pointerInput(stepDp) {
        var carried = 0f
        detectVerticalDragGestures(
            onDragStart = { carried = 0f },
            onDragEnd = { carried = 0f },
            onDragCancel = { carried = 0f },
        ) { change, dy ->
            change.consume()
            carried -= dy
            val size = stepDp * density
            while (carried >= size) {
                step(1)
                carried -= size
            }
            while (carried <= -size) {
                step(-1)
                carried += size
            }
        }
    }
}
