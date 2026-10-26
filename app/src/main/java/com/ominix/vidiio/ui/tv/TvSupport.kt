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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * True when the app is running on a television (Android TV, Google TV, Fire TV), driven by a
 * remote. Gates behaviour that only makes sense there: the auto-hiding side menu, no
 * auto-advancing hero, no entrance animations, stronger focus highlight.
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
 * Makes remote-driven scrolling predictable.
 *
 * By default, moving focus to a partly off-screen item scrolls the list by whatever brings it
 * into view, with a spring - so the amount (and the settle) varies and the page appears to
 * wobble as you move around. This spec instead:
 *  - does not scroll at all while the focused item is fully on screen, so moving along a
 *    visible row never moves the page; and
 *  - when the item is partly off-screen, scrolls so its leading edge sits at one fixed spot,
 *    with a fixed-duration ease and no overshoot - every move looks the same.
 *
 * It only ever scrolls in the direction the item is overflowing, so it can't ask for motion
 * a list can't give, and an item bigger than the viewport is left alone once lined up.
 * Fractions are of the scroll container's size, so it suits vertical lists and horizontal
 * rows alike.
 */
@OptIn(ExperimentalFoundationApi::class)
private object TvBringIntoViewSpec : BringIntoViewSpec {
    private const val ANCHOR_FRACTION = 0.10f

    override val scrollAnimationSpec: AnimationSpec<Float> =
        tween(durationMillis = 240, easing = FastOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leading = offset
        val trailing = offset + size

        // Fully visible: leave the page where it is.
        if (leading >= 0f && trailing <= containerSize) return 0f

        val distance = leading - containerSize * ANCHOR_FRACTION
        val overflowsStart = leading < 0f
        val overflowsEnd = trailing > containerSize
        return when {
            overflowsStart && distance < 0f -> distance
            overflowsEnd && distance > 0f -> distance
            else -> 0f
        }
    }
}

/**
 * On a TV: steadier remote scrolling, and a much stronger "you are here" highlight on every
 * Material control (buttons, icon buttons, tabs, list rows) - the stock focus overlay is a
 * faint tint that is easy to lose from across a room. Cards and tiles draw their own ring
 * (see tvClickable). Elsewhere this does nothing.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProvideTvScrolling(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides TvBringIntoViewSpec,
        LocalRippleConfiguration provides RippleConfiguration(
            color = Color.White,
            rippleAlpha = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0.45f, hoveredAlpha = 0.08f, pressedAlpha = 0.3f)
        ),
        content = content
    )
}
