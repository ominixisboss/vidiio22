package com.example.vidiio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vidiio.data.model.DownloadTask
import com.example.vidiio.data.repository.DownloadRepository
import com.example.vidiio.download.DownloadManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val repository: DownloadRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {

    val downloads: StateFlow<List<DownloadTask>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pauseDownload(id: Long) {
        downloadManager.pause(id)
    }

    fun resumeDownload(id: Long) {
        downloadManager.resume(id)
    }

    fun cancelDownload(id: Long) {
        downloadManager.cancel(id)
    }

    fun deleteDownload(id: Long) {
        downloadManager.delete(id)
    }
}
