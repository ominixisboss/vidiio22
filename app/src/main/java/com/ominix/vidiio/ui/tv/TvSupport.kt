package com.ominix.vidiio.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer

/**
 * True when the app is running on a television (Android TV, Google TV, Fire TV), driven by a
 * remote. Gates behaviour that only makes sense there: the auto-hiding side menu, no
 * auto-advancing hero, no entrance animations.
 */
val LocalIsTv = staticCompositionLocalOf { false }

fun isTvDevice(context: Context): Boolean {
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    val pm = context.packageManager
    return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature("amazon.hardware.fire_tv")
}

/**
 * Makes remote-driven scrolling deterministic.
 *
 * By default, moving focus to an item that is partly off-screen makes the list scroll by
 * whatever amount brings it into view, with a spring - so the amount (and the settle) varies
 * with the item's size and position, and the whole page appears to wobble as you move around.
 * This spec instead:
 *  - does not move at all while the focused item is already comfortably on screen, so moving
 *    along a visible row never scrolls the page; and
 *  - when it has to scroll, always puts the item's leading edge at the same fixed spot, with a
 *    short fixed-duration ease and no overshoot - so every move looks identical.
 *
 * Fractions are of the scroll container's size, so it behaves the same for vertical lists
 * (rows) and horizontal lists (cards).
 */
@OptIn(ExperimentalFoundationApi::class)
private object TvBringIntoViewSpec : BringIntoViewSpec {
    private const val ANCHOR_FRACTION = 0.10f
    private const val SAFE_MARGIN_FRACTION = 0.05f

    override val scrollAnimationSpec: AnimationSpec<Float> =
        tween(durationMillis = 260, easing = FastOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = containerSize * SAFE_MARGIN_FRACTION
        val leading = offset
        val trailing = offset + size

        // Comfortably visible: leave the page where it is.
        if (leading >= margin && trailing <= containerSize - margin) return 0f

        // Bigger than the viewport (e.g. a hero): just line its leading edge up.
        val target = containerSize * ANCHOR_FRACTION
        return leading - target
    }
}

/** Applies [TvBringIntoViewSpec] to every scrollable inside [content] when running on a TV. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvideTvScrolling(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvBringIntoViewSpec, content = content)
    } else {
        content()
    }
}

/**
 * For horizontal rows: when focus leaves a row and later comes back (up/down through the
 * page), it returns to the card you were last on instead of jumping to the first one.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.tvRow(): Modifier = focusRestorer()
