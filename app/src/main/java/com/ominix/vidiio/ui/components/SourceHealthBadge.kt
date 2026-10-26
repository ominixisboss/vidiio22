package com.ominix.vidiio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ominix.vidiio.data.repository.SourceHealth
import com.ominix.vidiio.data.repository.SourceStatus

private val WorkingGreen = Color(0xFF4CAF50)
private val DeadRed = Color(0xFFE57373)

/** One-line "does this source work?" indicator, shared by the details list and the player. */
@Composable
fun SourceHealthBadge(health: SourceHealth?, modifier: Modifier = Modifier) {
    val status = health?.status ?: SourceStatus.CHECKING
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (status) {
            SourceStatus.WORKING -> {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Working", tint = WorkingGreen, modifier = Modifier.size(14.dp))
                Text(
                    text = health?.latencyMs?.let { "${it} ms" } ?: health?.note ?: "Working",
                    color = WorkingGreen,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            SourceStatus.CHECKING -> {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                Text(
                    text = "Checking",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            SourceStatus.UNKNOWN -> {
                Icon(Icons.Rounded.HelpOutline, contentDescription = "Unverified", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    text = health?.note ?: "Unverified",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            SourceStatus.DEAD -> {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = "Not working", tint = DeadRed, modifier = Modifier.size(14.dp))
                Text(
                    text = health?.note ?: "Not working",
                    color = DeadRed,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
