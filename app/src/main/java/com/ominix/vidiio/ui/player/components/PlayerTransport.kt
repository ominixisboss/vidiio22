package com.ominix.vidiio.ui.player.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ominix.vidiio.ui.components.tvClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
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
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .navigationBarsPadding()
    ) {
        // Seek Bar - drag locally, commit on release so polling can't yank the thumb back.
        var isScrubbing by remember { mutableStateOf(false) }
        var scrubValue by remember { mutableFloatStateOf(0f) }
        var settleUntil by remember { mutableLongStateOf(0L) }
        var isSliderFocused by remember { mutableStateOf(false) }

        val playbackFraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val bufferedFraction = if (duration > 0) (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val sliderValue = when {
            isScrubbing -> scrubValue
            System.currentTimeMillis() < settleUntil -> scrubValue
            else -> playbackFraction
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .onFocusChanged { isSliderFocused = it.isFocused }
                .then(
                    if (isSliderFocused) {
                        Modifier
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    } else Modifier
                )
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && isSliderFocused) {
                        val stepFraction = if (duration > 0) 10_000f / duration else 0.01f
                        when (event.key) {
                            Key.DirectionLeft -> {
                                val newFraction = (sliderValue - stepFraction).coerceIn(0f, 1f)
                                scrubValue = newFraction
                                isScrubbing = true
                                onSeek((newFraction * duration).toLong())
                                isScrubbing = false
                                settleUntil = System.currentTimeMillis() + 700
                                true
                            }
                            Key.DirectionRight -> {
                                val newFraction = (sliderValue + stepFraction).coerceIn(0f, 1f)
                                scrubValue = newFraction
                                isScrubbing = true
                                onSeek((newFraction * duration).toLong())
                                isScrubbing = false
                                settleUntil = System.currentTimeMillis() + 700
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
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

            // Continuous single focus row for all transport & secondary control buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TransportIconButton(Icons.Rounded.Replay10, "Rewind", onClick = onRewind)
                
                Box(
                    modifier = Modifier.tvClickable(
                        onClick = onPlayPause,
                        shape = CircleShape,
                        focusScale = 1.12f
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                TransportIconButton(Icons.Rounded.Forward10, "Forward", onClick = onForward)

                Spacer(modifier = Modifier.width(8.dp))

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
                
                Box(
                    modifier = Modifier.tvClickable(
                        onClick = onToggleSpeed,
                        shape = CircleShape,
                        focusScale = 1.12f
                    )
                ) {
                    TextButton(onClick = onToggleSpeed) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = if (isSpeedActive) MaterialTheme.colorScheme.primary else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
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
    Box(
        modifier = Modifier.tvClickable(
            onClick = onClick,
            shape = CircleShape,
            focusScale = 1.15f
        )
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White)
        }
    }
}

@Composable
private fun TransportToggleIcon(
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.tvClickable(
            onClick = onClick,
            shape = CircleShape,
            focusScale = 1.15f
        )
    ) {
        IconButton(onClick = onClick) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else Color.White
            )
        }
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
