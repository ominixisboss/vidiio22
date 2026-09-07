package com.example.vidiio.ui.player.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun VolumeHud(
    volume: Float,
    isMuted: Boolean,
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f)
    ) {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when {
                    isMuted || volume == 0f -> Icons.Rounded.VolumeOff
                    volume < 0.5f -> Icons.Rounded.VolumeDown
                    else -> Icons.Rounded.VolumeUp
                }
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isMuted) "Muted" else "${(volume * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun BrightnessHud(
    brightness: Float,
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f)
    ) {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when {
                    brightness < 0.34f -> Icons.Rounded.BrightnessLow
                    brightness < 0.67f -> Icons.Rounded.BrightnessMedium
                    else -> Icons.Rounded.BrightnessHigh
                }
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${(brightness * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun AudioHud(
    text: String,
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                .padding(horizontal = 22.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Audiotrack, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = text,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SkipButton(
    label: String,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        onClick = onSkip,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.15f),
        contentColor = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        modifier = Modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.FastForward, 
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label, 
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(1.dp, 20.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Close, 
                    contentDescription = "Dismiss", 
                    modifier = Modifier.size(18.dp),
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun AutoNextOverlay(
    nextEpisodeName: String,
    secondsLeft: Int,
    onCancel: () -> Unit,
    onPlayNow: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Next episode starts in",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = secondsLeft.toString(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                nextEpisodeName,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = onPlayNow) {
                    Text("Play Now")
                }
            }
        }
    }
}
