package com.ominix.vidiio.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ominix.vidiio.data.model.Episode
import com.ominix.vidiio.ui.components.tvFocusRing

/**
 * "Up next" card shown when an episode ends: what is coming, how long until it starts, and two
 * buttons. Play now takes focus, so a single OK press skips the wait; Back also cancels.
 */
@Composable
fun UpNextOverlay(
    next: Episode,
    secondsLeft: Int,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = modifier
            .width(320.dp)
            .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text("Up next", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(2.dp))
        Text(
            "S${next.seasonNumber} · E${next.episodeNumber}",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.8f)
        )
        Text(
            next.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Playing in ${secondsLeft}s",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onPlayNow,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.focusRequester(focus).tvFocusRing(RoundedCornerShape(20.dp))
            ) { Text("Play now", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier.tvFocusRing(RoundedCornerShape(20.dp))
            ) { Text("Cancel") }
        }
    }
}
