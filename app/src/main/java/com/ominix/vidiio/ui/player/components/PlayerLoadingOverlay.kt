package com.ominix.vidiio.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ominix.vidiio.torrent.TorrentStatus

/**
 * The scrim shown while buffering or while a torrent is finding peers.
 *
 * For a magnet with a live [TorrentStatus] this reports buffer percentage, seeders, peers
 * and rate, because a torrent can legitimately sit here for 10-30 seconds and a bare
 * spinner makes that look like a hang.
 */
@Composable
fun PlayerLoadingOverlay(
    isMagnet: Boolean,
    status: TorrentStatus?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        if (isMagnet && status != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    progress = { (status.bufferProgress / 100f).coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = status.statusMessage,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                if (status.statusMessage.contains("Buffering") || status.statusMessage.contains("Peers")) {
                    Text(
                        text = "Buffer: ${status.bufferProgress.toInt()}%",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${status.numSeeders} seeders • ${status.numPeers} peers",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = String.format("%.1f KB/s", status.downloadRate),
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun PlaybackErrorDialog(
    message: String,
    onRetry: () -> Unit,
    onChooseAnotherSource: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Error") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onRetry) { Text("Retry") }
        },
        dismissButton = {
            TextButton(onClick = onChooseAnotherSource) { Text("Choose Another Source") }
        }
    )
}
