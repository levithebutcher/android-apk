package com.example.ytdownloader.service

import android.net.Uri
import com.example.ytdownloader.data.model.DownloadHistoryEntry
import com.example.ytdownloader.data.model.DownloadProgress
import com.example.ytdownloader.data.model.DownloadStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface DownloadEvent {
    data class Progress(
        val downloadId: String,
        val progress: Float,
        val speed: String,
        val eta: String,
        val statusText: String,
        val status: DownloadStatus
    ) : DownloadEvent

    data class Completed(
        val downloadId: String,
        val outputUri: Uri?,
        val entry: DownloadHistoryEntry
    ) : DownloadEvent

    data class Failed(
        val downloadId: String,
        val errorMessage: String,
        val entry: DownloadHistoryEntry
    ) : DownloadEvent
}

object DownloadEventBus {

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    private val _currentProgress = MutableStateFlow<DownloadProgress?>(null)
    val currentProgress: StateFlow<DownloadProgress?> = _currentProgress.asStateFlow()

    private val _activeDownloadId = MutableStateFlow<String?>(null)
    val activeDownloadId: StateFlow<String?> = _activeDownloadId.asStateFlow()

    fun emitProgress(
        downloadId: String,
        progress: Float,
        speed: String,
        eta: String,
        statusText: String,
        status: DownloadStatus = DownloadStatus.DOWNLOADING
    ) {
        val prog = DownloadProgress(
            progress = progress,
            speed = speed,
            eta = eta,
            statusText = statusText,
            status = status
        )
        _currentProgress.value = prog
        _activeDownloadId.value = downloadId
        _events.tryEmit(
            DownloadEvent.Progress(
                downloadId = downloadId,
                progress = progress,
                speed = speed,
                eta = eta,
                statusText = statusText,
                status = status
            )
        )
    }

    fun emitCompleted(downloadId: String, outputUri: Uri?, entry: DownloadHistoryEntry) {
        _currentProgress.value = DownloadProgress(
            progress = 100f,
            statusText = "Completed",
            status = DownloadStatus.COMPLETED
        )
        _activeDownloadId.value = null
        _events.tryEmit(DownloadEvent.Completed(downloadId, outputUri, entry))
    }

    fun emitFailed(downloadId: String, errorMessage: String, entry: DownloadHistoryEntry) {
        _currentProgress.value = DownloadProgress(
            progress = 0f,
            statusText = errorMessage,
            status = DownloadStatus.FAILED
        )
        _activeDownloadId.value = null
        _events.tryEmit(DownloadEvent.Failed(downloadId, errorMessage, entry))
    }

    fun clearActive() {
        _currentProgress.value = null
        _activeDownloadId.value = null
    }
}
