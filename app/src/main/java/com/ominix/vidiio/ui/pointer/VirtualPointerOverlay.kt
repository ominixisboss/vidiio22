package com.ominix.vidiio.ui.pointer

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.max
import kotlin.math.min

/**
 * A D-pad-driven cursor, for the "Mouse Toggle" setting.
 *
 * Focus-based traversal (the usual TV navigation) assumes a layout built for it - a clean
 * order between focusable rows. Dense screens (Settings' switches and dropdowns, grids with
 * uneven row lengths) don't always give the platform a sane order to infer, and users land on
 * the wrong control. This is the fallback: arrow keys move a dot around the screen, and OK
 * taps whatever is under it by replaying a real touch event, so ordinary `clickable` targets
 * need no changes to be reachable.
 *
 * Left off the Player (which has its own D-pad transport handling) and Search (whose text
 * field needs the D-pad for the on-screen keyboard).
 */
@Composable
fun VirtualPointerOverlay(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    val view = LocalView.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var position by remember { mutableStateOf<Offset?>(null) }
    var pulse by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (pulse) 0.8f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pointerPress"
    )

    fun dispatchTap(at: Offset) {
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, at.x, at.y, 0)
        val up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, at.x, at.y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            view.dispatchTouchEvent(down)
            view.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                containerSize = size
                if (position == null) {
                    position = Offset(size.width / 2f, size.height / 2f)
                }
            }
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .onPreviewKeyEvent { event ->
                val current = position ?: return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent event.key in DIRECTION_KEYS || event.key in TAP_KEYS
                }
                when (event.key) {
                    in DIRECTION_KEYS -> {
                        // Accelerate with the OS's own key-repeat rather than running a timer.
                        val repeat = event.nativeKeyEvent.repeatCount
                        val step = (BASE_STEP_PX + repeat * ACCEL_STEP_PX).coerceAtMost(MAX_STEP_PX)
                        position = move(current, event.key, step, containerSize)
                        true
                    }
                    in TAP_KEYS -> {
                        pulse = true
                        dispatchTap(current)
                        true
                    }
                    else -> false
                }
            }
    ) {
        content()

        position?.let { pos ->
            val density = androidx.compose.ui.platform.LocalDensity.current
            Icon(
                imageVector = Icons.Rounded.NearMe,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .zIndex(Float.MAX_VALUE)
                    .offset(
                        x = with(density) { pos.x.toDp() } - CURSOR_TIP_OFFSET_DP,
                        y = with(density) { pos.y.toDp() } - CURSOR_TIP_OFFSET_DP
                    )
                    .size(28.dp)
                    .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                    .rotate(-45f)
                    .shadow(4.dp)
            )
        }
    }

    // The press animation only needs to run once per tap; reset right after it starts.
    if (pulse) {
        androidx.compose.runtime.LaunchedEffect(position) {
            kotlinx.coroutines.delay(120)
            pulse = false
        }
    }
}

private fun move(from: Offset, key: Key, step: Float, bounds: IntSize): Offset {
    if (bounds.width == 0 || bounds.height == 0) return from
    val (dx, dy) = when (key) {
        Key.DirectionUp -> 0f to -step
        Key.DirectionDown -> 0f to step
        Key.DirectionLeft -> -step to 0f
        Key.DirectionRight -> step to 0f
        else -> 0f to 0f
    }
    return Offset(
        x = max(0f, min(bounds.width.toFloat(), from.x + dx)),
        y = max(0f, min(bounds.height.toFloat(), from.y + dy))
    )
}

private val DIRECTION_KEYS = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)
private val TAP_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

private const val BASE_STEP_PX = 26f
private const val ACCEL_STEP_PX = 3.5f
private const val MAX_STEP_PX = 90f
private val CURSOR_TIP_OFFSET_DP = 6.dp
