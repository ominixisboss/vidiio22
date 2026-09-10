package com.ominix.vidiio.ui.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ominix.vidiio.torrent.TorrentFileInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentFileSheet(
    onDismiss: () -> Unit,
    files: List<TorrentFileInfo>,
    onFileSelect: (TorrentFileInfo) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Select File", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn {
                items(files) { file ->
                    ListItem(
                        headlineContent = { Text(file.name) },
                        supportingContent = { Text("%.2f MB".format(file.size / (1024.0 * 1024.0))) },
                        leadingContent = { Icon(Icons.Rounded.Description, contentDescription = null) },
                        modifier = Modifier.clickable { onFileSelect(file) }
                    )
                }
            }
        }
    }
}
