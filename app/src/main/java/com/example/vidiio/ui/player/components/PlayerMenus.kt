package com.example.vidiio.ui.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vidiio.data.model.subtitles.SubdlSubtitle
import com.example.vidiio.ui.player.AudioTrackInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleMenu(
    subtitles: List<SubdlSubtitle>,
    selectedUrl: String?,
    offsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    onSubtitleSelect: (SubdlSubtitle) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Subtitles", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sync Offset
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sync Offset: ${offsetMs}ms", modifier = Modifier.weight(1f))
                IconButton(onClick = { onOffsetChange(offsetMs - 100) }) {
                    Icon(Icons.Rounded.Remove, contentDescription = "-100ms")
                }
                IconButton(onClick = { onOffsetChange(offsetMs + 100) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "+100ms")
                }
                TextButton(onClick = { onOffsetChange(0L) }) {
                    Text("Reset")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                item {
                    ListItem(
                        headlineContent = { Text("None") },
                        leadingContent = { RadioButton(selected = selectedUrl == null, onClick = null) },
                        modifier = Modifier.clickable { onDisable(); onDismiss() }
                    )
                }
                items(subtitles) { sub ->
                    ListItem(
                        headlineContent = { Text(sub.releaseName ?: sub.language) },
                        supportingContent = { Text(sub.language) },
                        leadingContent = { RadioButton(selected = sub.url == selectedUrl, onClick = null) },
                        modifier = Modifier.clickable { onSubtitleSelect(sub); onDismiss() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioMenu(
    tracks: List<AudioTrackInfo>,
    selectedTrack: AudioTrackInfo?,
    onTrackSelect: (AudioTrackInfo) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Audio Tracks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(tracks) { track ->
                    ListItem(
                        headlineContent = { Text(track.name) },
                        supportingContent = { Text("${track.format.sampleMimeType} • ${track.format.channelCount}ch") },
                        leadingContent = { RadioButton(selected = track == selectedTrack, onClick = null) },
                        modifier = Modifier.clickable { onTrackSelect(track); onDismiss() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedMenu(
    currentSpeed: Float,
    onSpeedSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Playback Speed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(speeds) { speed ->
                    ListItem(
                        headlineContent = { Text("${speed}x") },
                        leadingContent = { RadioButton(selected = speed == currentSpeed, onClick = null) },
                        modifier = Modifier.clickable { onSpeedSelect(speed); onDismiss() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectMenu(
    currentMode: Int,
    onModeSelect: (Int) -> Unit,
    avoidCutout: Boolean,
    onToggleAvoidCutout: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    // Media3 AspectRatioFrameLayout resize modes
    val modes = listOf(
        0 to "Fit",
        3 to "Zoom",
        4 to "Fill",
        1 to "Fixed Width",
        2 to "Fixed Height"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            item {
                Text(
                    "Aspect Ratio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(modes) { (mode, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { RadioButton(selected = mode == currentMode, onClick = null) },
                    modifier = Modifier.clickable { onModeSelect(mode); onDismiss() }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                ListItem(
                    headlineContent = { Text("Fill, keep camera clear") },
                    supportingContent = { Text("Fills the screen but stops short of the front-camera hole") },
                    trailingContent = {
                        Switch(checked = avoidCutout, onCheckedChange = onToggleAvoidCutout)
                    },
                    modifier = Modifier.clickable { onToggleAvoidCutout(!avoidCutout) }
                )
            }
        }
    }
}
