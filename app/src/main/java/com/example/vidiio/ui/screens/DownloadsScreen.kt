package com.example.vidiio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vidiio.data.model.DownloadStatus
import com.example.vidiio.data.model.DownloadTask
import com.example.vidiio.data.model.Movie
import com.example.vidiio.data.model.MovieType
import com.example.vidiio.ui.components.GlassCard
import com.example.vidiio.ui.components.RiveAnimation
import com.example.vidiio.ui.viewmodel.DownloadsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onPlayLocal: (Movie, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloads by viewModel.downloads.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    RiveAnimation(
                        url = "https://public.rive.app/community/runtime-files/2042-4040-avatar-pack-use-case.riv",
                        modifier = Modifier.size(200.dp),
                        fallback = {
                            Icon(
                                Icons.Rounded.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(100.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No downloads yet",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Downloads will appear here once you start saving content for offline viewing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloads) { task ->
                    DownloadItem(
                        task = task,
                        onPause = { viewModel.pauseDownload(task.id) },
                        onResume = { viewModel.resumeDownload(task.id) },
                        onCancel = { viewModel.cancelDownload(task.id) },
                        onDelete = { viewModel.deleteDownload(task.id) },
                        onPlay = { 
                            task.filePath?.let { path ->
                                // Create a dummy movie object for the player
                                val movie = Movie(
                                    id = "local_${task.id}",
                                    title = task.title,
                                    posterUrl = "",
                                    type = MovieType.MOVIE,
                                    source = "local"
                                )
                                onPlayLocal(movie, path)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadItem(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        alpha = 0.1f,
        borderAlpha = 0.15f
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = task.status.name,
                        color = when(task.status) {
                            DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
                            DownloadStatus.FAILED -> Color(0xFFF44336)
                            DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 12.sp
                    )
                }
                
                if (task.status == DownloadStatus.COMPLETED) {
                    Row {
                        IconButton(onClick = onPlay) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        }
                    }
                } else {
                    Row {
                        if (task.status == DownloadStatus.DOWNLOADING) {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Rounded.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        } else if (task.status == DownloadStatus.PAUSED) {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            
            if (task.status != DownloadStatus.COMPLETED && task.status != DownloadStatus.FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { task.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${task.progress.toInt()}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    if (task.totalSize > 0) {
                        Text(
                            text = "${formatSize(task.downloadedSize)} / ${formatSize(task.totalSize)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
