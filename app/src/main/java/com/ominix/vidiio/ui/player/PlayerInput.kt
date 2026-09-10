package com.ominix.vidiio.ui.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * D-pad and media-key handling for Android TV remotes.
 *
 * Split out of PlayerScreen's modifier chain: it was ~35 lines of `when` inline in the
 * middle of the layout, which made both the key map and the layout harder to read.
 */
@UnstableApi
fun Modifier.playerRemoteControls(
    viewModel: PlayerViewModel,
    controlsVisible: Boolean,
    onShowControls: () -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
            viewModel.togglePlayPause()
            onShowControls(); true
        }

        Key.DirectionLeft, Key.MediaRewind -> {
            viewModel.seekBy(-SEEK_STEP_MS)
            onShowControls(); true
        }

        Key.DirectionRight, Key.MediaFastForward -> {
            viewModel.seekBy(SEEK_STEP_MS)
            onShowControls(); true
        }

        Key.DirectionUp, Key.DirectionDown -> {
            // Reveal the transport so the remote can move onto its buttons.
            if (!controlsVisible) {
                onShowControls(); true
            } else {
                false
            }
        }

        Key.MediaPlay -> { viewModel.play(); onShowControls(); true }
        Key.MediaPause, Key.MediaStop -> { viewModel.pause(); onShowControls(); true }
        else -> false
    }
}

/**
 * Touch gestures: vertical drag for brightness (left half) and volume (right half),
 * single tap to toggle controls, double tap to seek.
 */
@UnstableApi
fun Modifier.playerTouchGestures(
    viewModel: PlayerViewModel,
    deviceControls: PlayerDeviceControls,
    scope: CoroutineScope,
    onToggleControls: () -> Unit,
): Modifier = this
    .pointerInput(Unit) {
        var dragSide = DragSide.NONE
        detectVerticalDragGestures(
            onDragStart = { offset ->
                dragSide = if (offset.x < size.width / 2) DragSide.BRIGHTNESS else DragSide.VOLUME
            },
            onVerticalDrag = { _, dragAmount ->
                // Drag up = increase. A full swipe over ~65% of the screen covers the whole
                // range, and we accumulate in a float so tiny drags still register.
                val deltaFraction = -dragAmount / (size.height * DRAG_RANGE_FRACTION)
                when (dragSide) {
                    DragSide.VOLUME -> deviceControls.adjustVolume(deltaFraction)
                    DragSide.BRIGHTNESS -> deviceControls.adjustBrightness(deltaFraction)
                    DragSide.NONE -> Unit
                }
            },
            onDragEnd = {
                scope.launch {
                    delay(HUD_LINGER_MS)
                    deviceControls.hideHuds()
                }
            }
        )
    }
    .pointerInput(Unit) {
        detectTapGestures(
            onTap = { onToggleControls() },
            onDoubleTap = { offset ->
                if (offset.x < size.width / 2) {
                    viewModel.seekBy(-SEEK_STEP_MS)
                } else {
                    viewModel.seekBy(SEEK_STEP_MS)
                }
            }
        )
    }

private enum class DragSide { NONE, BRIGHTNESS, VOLUME }

private const val SEEK_STEP_MS = 10_000L
private const val DRAG_RANGE_FRACTION = 0.65f
private const val HUD_LINGER_MS = 1200L
