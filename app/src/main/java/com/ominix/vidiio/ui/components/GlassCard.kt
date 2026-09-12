package com.ominix.vidiio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A standardized Glassmorphism card for the Vidiio app.
 * Follows the requirement of 24.dp corner radius and 0.5.dp white border,
 * with TV focus support when clickable.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    alpha: Float = 0.05f,
    borderAlpha: Float = 0.12f,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .then(
            if (onClick != null) {
                Modifier.tvClickable(onClick = onClick, shape = shape, focusScale = 1.02f)
            } else Modifier
        )

    Surface(
        modifier = cardModifier,
        shape = shape,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = borderAlpha)),
        content = content
    )
}
