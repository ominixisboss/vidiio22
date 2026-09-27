package com.ominix.vidiio.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Right-hand panel for the player's pickers (subtitles, audio, sources, ...).
 *
 * This replaces ModalBottomSheet, which lives in its own dialog window: on a TV remote focus
 * never entered it, so after opening a menu the D-pad kept driving the player underneath.
 * The panel is part of the player's own tree, takes focus the moment it opens, and Back
 * (handled by PlayerScreen) closes it. Tapping the dimmed area still dismisses it on touch.
 */
@Composable
fun PlayerSidePanel(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focus = remember { FocusRequester() }

    // Lazy list items are composed a frame after the panel; wait a beat before grabbing focus.
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(440.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .focusRequester(focus)
                .focusGroup()
        ) {
            Column(content = content)
        }
    }
}
