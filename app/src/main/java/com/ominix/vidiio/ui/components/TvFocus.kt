package com.ominix.vidiio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.dp

/**
 * Clickable that also works with a D-pad / TV remote: it's focusable, shows a bright ring
 * while focused (so you can see what the remote is on), and treats DPAD_CENTER / Enter as a
 * click. Use in place of `Modifier.clickable` on browseable cards and tiles.
 *
 * The focus state is shown only by drawing - a ring inset within the item's own bounds and a
 * faint lift in brightness. It never changes size, position or layout. It used to also scale
 * the item up, which made every card in a row grow, overlap its neighbours and spill outside
 * the list's bounds as focus moved: that, plus the scroll that followed, was the "shaking".
 *
 * [focusScale] is accepted so existing call sites keep compiling, and is ignored.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    focusScale: Float = 1f,
    enabled: Boolean = true,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val ring by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(120),
        label = "tvRing"
    )
    val dim by animateFloatAsState(
        targetValue = if (pressed) 0.18f else 0f,
        animationSpec = tween(80),
        label = "tvPress"
    )
    return this
        .clip(shape)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        .drawWithContent {
            drawContent()
            if (ring > 0f) {
                // Lift the focused item slightly, then draw the ring inside its bounds.
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, Color.White.copy(alpha = 0.08f * ring))
                val stroke = RING_WIDTH.toPx()
                inset(stroke / 2f) {
                    val inner = shape.createOutline(Size(size.width, size.height), layoutDirection, this)
                    drawOutline(inner, Color.White.copy(alpha = ring), style = Stroke(width = stroke))
                }
            }
            if (dim > 0f) {
                drawOutline(shape.createOutline(size, layoutDirection, this), Color.Black.copy(alpha = dim))
            }
        }
}

private val RING_WIDTH = 3.dp
