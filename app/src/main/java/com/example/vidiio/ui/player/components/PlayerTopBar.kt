package com.example.vidiio.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerTopBar(
    title: String,
    subtitle: String?,
    quality: String?,
    isDownloading: Boolean,
    isEpisodesActive: Boolean,
    onBack: () -> Unit,
    onDownload: (() -> Unit)?,
    onToggleEpisodes: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.8f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (quality != null) {
                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = IconButtonDefaults.outlinedIconButtonBorder(true)
                ) {
                    Text(
                        text = quality.uppercase(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            if (onDownload != null) {
                IconButton(
                    onClick = onDownload,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isDownloading) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                        contentColor = if (isDownloading) MaterialTheme.colorScheme.primary else Color.White
                    )
                ) {
                    Icon(
                        if (isDownloading) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                        contentDescription = "Download"
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            if (onToggleEpisodes != null) {
                IconButton(
                    onClick = onToggleEpisodes,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isEpisodesActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                        contentColor = if (isEpisodesActive) MaterialTheme.colorScheme.primary else Color.White
                    )
                ) {
                    Icon(Icons.Rounded.PlaylistPlay, contentDescription = "Episodes")
                }
            }
        }
    }
}
