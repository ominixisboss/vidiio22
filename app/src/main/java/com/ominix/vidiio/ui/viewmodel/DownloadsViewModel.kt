package com.ominix.vidiio.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ominix.vidiio.data.model.DownloadStatus
import com.ominix.vidiio.data.model.DownloadTask
import com.ominix.vidiio.data.repository.DownloadRepository
import com.ominix.vidiio.download.DownloadExporter
import com.ominix.vidiio.download.DownloadManager
import com.ominix.vidiio.download.ExportResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val repository: DownloadRepository,
    private val downloadManager: DownloadManager,
    private val appContext: Context
) : ViewModel() {

    val downloads: StateFlow<List<DownloadTask>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Set by the screen when it needs the caller to request legacy storage permission first. */
    private var pendingLegacyExport: Long? = null

    fun pauseDownload(id: Long) = downloadManager.pause(id)
    fun resumeDownload(id: Long) = downloadManager.resume(id)
    fun cancelDownload(id: Long) = downloadManager.cancel(id)
    fun deleteDownload(id: Long) = downloadManager.delete(id)

    fun exportDownload(id: Long, hasLegacyStoragePermission: Boolean = false) {
        viewModelScope.launch {
            val task = repository.getDownloadById(id) ?: return@launch
            if (task.status != DownloadStatus.COMPLETED) {
                _events.send("Download isn't finished yet")
                return@launch
            }
            _events.send("Exporting “${task.title}”…")
            when (val result = DownloadExporter.export(appContext, task, hasLegacyStoragePermission)) {
                is ExportResult.Success -> _events.send("Saved to ${result.location}")
                is ExportResult.Error -> _events.send("Export failed: ${result.message}")
                ExportResult.NeedsPermission -> {
                    pendingLegacyExport = id
                    _events.send("__NEEDS_STORAGE_PERMISSION__")
                }
            }
        }
    }

    /** Called after the screen obtains legacy storage permission. */
    fun retryPendingExport() {
        val id = pendingLegacyExport ?: return
        pendingLegacyExport = null
        exportDownload(id, hasLegacyStoragePermission = true)
    }
}
