package com.minimaui.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** The vertical slider beside a long list: a track with an outline, and a thumb that slides along it. */
const val SliderTrackWidth = 24f
private const val SliderBorder = 3f
private const val SliderThumbWidth = 12f
private const val SliderGap = 6f
private const val SliderThumbHeight = 44f

/** How far the touch area reaches past each side of the track, so a finger can find it. */
private const val SliderTouchReach = 22f
private const val SliderReachY = 8f

private val SliderTrack = Color(0xFF3A3A3A)

/**
 * The scroll slider of a list: [fraction] (0..1) is where the thumb sits. It is [SliderTrackWidth] wide with
 * the thumb centred in it and an even gap all round. With [onSeek] the track can also be dragged (or tapped) with a finger;
 * it gets the fraction under the finger.
 */
@Composable
fun ScrollSlider(
    x: Float,
    top: Float,
    height: Float,
    fraction: Float,
    modifier: Modifier = Modifier,
    thumbHeight: Float = SliderThumbHeight,
    onSeek: ((Float) -> Unit)? = null,
) {
    val density = LocalDensity.current.density
    // The touch area is the whole box (a finger needs more than the track), the track and thumb sit inside it.
    val touch = if (onSeek == null) Modifier else Modifier.pointerInput(height, thumbHeight) {
        val room = (height - thumbHeight - 2 * SliderGap) * density
        fun seek(y: Float) = onSeek(((y - (SliderReachY + SliderGap + thumbHeight / 2f) * density) / room).coerceIn(0f, 1f))
        detectVerticalDragGestures(
            onDragStart = { seek(it.y) },
            onVerticalDrag = { change, _ ->
                change.consume()
                seek(change.position.y)
            },
        )
    }
    Box(
        modifier = modifier
            .offset((x - SliderTouchReach).dp, (top - SliderReachY).dp)
            .size((SliderTrackWidth + 2 * SliderTouchReach).dp, (height + 2 * SliderReachY).dp)
            .then(touch)
    ) {
        Box(
            Modifier
                .offset(SliderTouchReach.dp, SliderReachY.dp)
                .size(SliderTrackWidth.dp, height.dp)
                .border(SliderBorder.dp, SliderTrack, CircleShape)
        )
        Box(
            Modifier
                .offset(
                    (SliderTouchReach + (SliderTrackWidth - SliderThumbWidth) / 2f).dp,
                    (SliderReachY + SliderGap + fraction.coerceIn(0f, 1f) * (height - thumbHeight - 2 * SliderGap)).dp
                )
                .size(SliderThumbWidth.dp, thumbHeight.dp)
                .background(Color.White, CircleShape)
        )
    }
}
