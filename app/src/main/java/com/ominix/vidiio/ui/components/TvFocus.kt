package com.ominix.vidiio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Clickable that also works with a D-pad / TV remote: it's focusable, grows and shows a
 * bright ring while focused (so you can see what the remote is on), and treats
 * DPAD_CENTER / Enter as a click. Use in place of `Modifier.clickable` on browseable
 * cards and tiles.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    focusScale: Float = 1.06f,
    enabled: Boolean = true,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            focused -> focusScale
            pressed -> 0.96f
            else -> 1f
        },
        label = "tvScale"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
        .clip(shape)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}
