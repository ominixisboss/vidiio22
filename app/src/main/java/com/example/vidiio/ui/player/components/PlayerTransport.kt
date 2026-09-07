package com.example.vidiio.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerTransport(
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    bufferedPosition: Long,
    isSubtitlesActive: Boolean,
    isAudioActive: Boolean,
    isSpeedActive: Boolean,
    isAspectActive: Boolean,
    isFullscreen: Boolean,
    playbackSpeed: Float,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onToggleAudio: () -> Unit,
    onToggleSpeed: () -> Unit,
    onToggleAspect: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.8f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .navigationBarsPadding()
    ) {
        // Seek Bar - drag locally, commit on release so polling can't yank the thumb back.
        var isScrubbing by remember { mutableStateOf(false) }
        var scrubValue by remember { mutableFloatStateOf(0f) }
        var settleUntil by remember { mutableLongStateOf(0L) }

        val playbackFraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val bufferedFraction = if (duration > 0) (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val sliderValue = when {
            isScrubbing -> scrubValue
            System.currentTimeMillis() < settleUntil -> scrubValue
            else -> playbackFraction
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            // Buffered range behind the active track.
            LinearProgressIndicator(
                progress = { bufferedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 10.dp)
                    .height(4.dp),
                color = Color.White.copy(alpha = 0.35f),
                trackColor = Color.White.copy(alpha = 0.15f),
            )
            Slider(
                value = sliderValue,
                onValueChange = {
                    isScrubbing = true
                    scrubValue = it
                },
                onValueChangeFinished = {
                    onSeek((scrubValue * duration).toLong())
                    isScrubbing = false
                    settleUntil = System.currentTimeMillis() + 700
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTime((sliderValue * duration).toLong())} / ${formatTime(duration)}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                TransportIconButton(Icons.Rounded.Replay10, "Rewind", onClick = onRewind)
                
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(36.dp)
                    )
                }

                TransportIconButton(Icons.Rounded.Forward10, "Forward", onClick = onForward)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Secondary controls
                TransportToggleIcon(
                    icon = Icons.Rounded.Subtitles,
                    isActive = isSubtitlesActive,
                    onClick = onToggleSubtitles
                )
                TransportToggleIcon(
                    icon = Icons.Rounded.Audiotrack,
                    isActive = isAudioActive,
                    onClick = onToggleAudio
                )
                
                TextButton(onClick = onToggleSpeed) {
                    Text(
                        text = "${playbackSpeed}x",
                        color = if (isSpeedActive) MaterialTheme.colorScheme.primary else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                TransportToggleIcon(
                    icon = Icons.Rounded.AspectRatio,
                    isActive = isAspectActive,
                    onClick = onToggleAspect
                )

                TransportIconButton(
                    if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    "Fullscreen",
                    onClick = onToggleFullscreen
                )
            }
        }
    }
}

@Composable
private fun TransportIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
    }
}

@Composable
private fun TransportToggleIcon(
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.primary else Color.White
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
