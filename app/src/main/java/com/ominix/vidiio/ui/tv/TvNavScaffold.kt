package com.ominix.vidiio.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemColors
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TvNavItem(
    val selected: Boolean,
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Side menu for a remote-driven TV.
 *
 * It stays out of the way: the page always uses the full screen and is never resized or
 * pushed around (that reflow was part of the screen "shaking" when the menu came and went).
 * The menu slides over the left edge only when it is wanted, and goes away on its own:
 *
 *  - It is shown briefly at launch so you know it is there.
 *  - It hides as soon as focus is in the page, i.e. as soon as you start browsing or scrolling.
 *  - Pressing Left when you are already at the left edge of the page brings it back, with
 *    focus on the current section.
 *  - Choosing a section hands focus to the new page and hides it again.
 *
 * [railEnabled] is false on full-screen routes (player, splash), where it never shows.
 */
@Composable
fun TvNavScaffold(
    items: List<TvNavItem>,
    railEnabled: Boolean,
    railColors: NavigationRailItemColors,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var railOpen by remember { mutableStateOf(true) }
    var railHasFocus by remember { mutableStateOf(false) }
    var firstHide by remember { mutableStateOf(true) }
    val selectedItemFocus = remember { FocusRequester() }
    var focusRailOnOpen by remember { mutableStateOf(false) }

    // Hide once focus has been out of the rail for a moment. The first time, wait a little
    // longer so the menu is actually seen at launch.
    LaunchedEffect(railHasFocus, railOpen) {
        if (railOpen && !railHasFocus && !focusRailOnOpen) {
            delay(if (firstHide) 2500 else 250)
            firstHide = false
            railOpen = false
        }
    }

    // Bring focus onto the rail once it has actually been composed.
    LaunchedEffect(railOpen, focusRailOnOpen) {
        if (railOpen && focusRailOnOpen) {
            delay(60)
            runCatching { selectedItemFocus.requestFocus() }
            focusRailOnOpen = false
        }
    }

    // Back from the menu returns to the page rather than leaving the app.
    BackHandler(enabled = railEnabled && railOpen && railHasFocus) {
        focusManager.moveFocus(FocusDirection.Right)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                // Only sees Left when nothing in the page used it. We move focus ourselves so
                // we can tell whether there was anywhere to go - and if there wasn't, that's
                // the cue to open the menu.
                if (railEnabled && event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    if (railHasFocus) return@onKeyEvent true
                    val moved = focusManager.moveFocus(FocusDirection.Left)
                    if (!moved) {
                        railOpen = true
                        focusRailOnOpen = true
                    }
                    true
                } else {
                    false
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) { content() }

        AnimatedVisibility(
            visible = railEnabled && railOpen,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                modifier = Modifier
                    .fillMaxHeight()
                    .onFocusChanged { railHasFocus = it.hasFocus }
                    .focusGroup()
            ) {
                items.forEach { item ->
                    NavigationRailItem(
                        selected = item.selected,
                        onClick = {
                            item.onClick()
                            // Let the new page appear, hand it focus, then tuck the menu away.
                            scope.launch {
                                delay(450)
                                focusManager.moveFocus(FocusDirection.Right)
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontWeight = FontWeight.Bold) },
                        colors = railColors,
                        modifier = if (item.selected) Modifier.focusRequester(selectedItemFocus) else Modifier
                    )
                }
            }
        }
    }
}
