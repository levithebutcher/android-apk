package com.example.ytdownloader.data.model

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    MERGING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class SheetTab {
    VIDEO,
    AUDIO
}

data class DownloadOption(
    val id: String,
    val label: String,
    val resolution: String,
    val ext: String,
    val isAudioOnly: Boolean,
    val requiresMerge: Boolean,
    val isHd: Boolean = false,
    val approxSizeBytes: Long? = null,
    val formatId: String? = null
)

data class PlaylistItem(
    val id: String,
    val url: String,
    val title: String,
    val durationSeconds: Long = 0L
)

data class VideoDetails(
    val id: String,
    val url: String,
    val title: String,
    val author: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val isPlaylist: Boolean = false,
    val playlistItems: List<PlaylistItem> = emptyList(),
    val videoOptions: List<DownloadOption> = emptyList(),
    val audioOptions: List<DownloadOption> = emptyList()
)

data class PlaylistQueueState(
    val isActive: Boolean = false,
    val playlistTitle: String = "",
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val currentItemTitle: String = "",
    val itemProgress: Float = 0f,
    val completedCount: Int = 0,
    val failedCount: Int = 0
) {
    val remainingCount: Int
        get() = (totalCount - completedCount - failedCount).coerceAtLeast(0)
}

data class DownloadProgress(
    val progress: Float = 0f,
    val speed: String = "",
    val eta: String = "",
    val statusText: String = "",
    val status: DownloadStatus = DownloadStatus.DOWNLOADING
)

data class DownloadHistoryEntry(
    val id: String,
    val videoId: String,
    val url: String,
    val title: String,
    val formatOption: DownloadOption,
    val filePath: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null
)
