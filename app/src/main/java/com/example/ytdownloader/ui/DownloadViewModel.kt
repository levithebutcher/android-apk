package com.example.ytdownloader.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytdownloader.YTDLApplication
import com.example.ytdownloader.data.model.DownloadHistoryEntry
import com.example.ytdownloader.data.model.DownloadOption
import com.example.ytdownloader.data.model.DownloadProgress
import com.example.ytdownloader.data.model.DownloadStatus
import com.example.ytdownloader.data.model.PlaylistItem
import com.example.ytdownloader.data.model.PlaylistQueueState
import com.example.ytdownloader.data.model.SheetTab
import com.example.ytdownloader.data.model.VideoDetails
import com.example.ytdownloader.data.config.ExtractorConfigManager
import com.example.ytdownloader.service.DownloadEvent
import com.example.ytdownloader.service.DownloadEventBus
import com.example.ytdownloader.service.DownloadForegroundService
import com.example.ytdownloader.service.YtdlpUpdateWorker
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    object Idle : UiState
    object Fetching : UiState
    data class Ready(val details: VideoDetails) : UiState
    data class Error(val message: String) : UiState
}

class DownloadViewModel : ViewModel() {

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isQualitySheetOpen = MutableStateFlow(false)
    val isQualitySheetOpen: StateFlow<Boolean> = _isQualitySheetOpen.asStateFlow()

    private val _activeTab = MutableStateFlow(SheetTab.VIDEO)
    val activeTab: StateFlow<SheetTab> = _activeTab.asStateFlow()

    private val _currentDownloadingOption = MutableStateFlow<DownloadOption?>(null)
    val currentDownloadingOption: StateFlow<DownloadOption?> = _currentDownloadingOption.asStateFlow()

    val downloadProgress: StateFlow<DownloadProgress?> = DownloadEventBus.currentProgress

    private val _playlistQueueState = MutableStateFlow(PlaylistQueueState())
    val playlistQueueState: StateFlow<PlaylistQueueState> = _playlistQueueState.asStateFlow()

    private val _historyList = MutableStateFlow<List<DownloadHistoryEntry>>(emptyList())
    val historyList: StateFlow<List<DownloadHistoryEntry>> = _historyList.asStateFlow()

    private val _isUpdatingYtdl = MutableStateFlow(false)
    val isUpdatingYtdl: StateFlow<Boolean> = _isUpdatingYtdl.asStateFlow()

    private val _systemMessage = MutableStateFlow<String?>(null)
    val systemMessage: StateFlow<String?> = _systemMessage.asStateFlow()

    private var playlistDownloadJob: Job? = null

    init {
        observeDownloadEvents()
    }

    private fun observeDownloadEvents() {
        viewModelScope.launch {
            DownloadEventBus.events.collect { event ->
                when (event) {
                    is DownloadEvent.Progress -> {
                        if (_playlistQueueState.value.isActive) {
                            _playlistQueueState.value = _playlistQueueState.value.copy(
                                itemProgress = event.progress
                            )
                        }
                    }
                    is DownloadEvent.Completed -> {
                        _currentDownloadingOption.value = null
                        addOrUpdateHistory(event.entry)
                        if (!_playlistQueueState.value.isActive) {
                            _systemMessage.value = "Download completed: ${event.entry.title}"
                        }
                    }
                    is DownloadEvent.Failed -> {
                        _currentDownloadingOption.value = null
                        addOrUpdateHistory(event.entry)
                        if (!_playlistQueueState.value.isActive) {
                            _systemMessage.value = "Download failed: ${event.errorMessage}"
                        }
                    }
                }
            }
        }
    }

    private fun addOrUpdateHistory(entry: DownloadHistoryEntry) {
        val updated = _historyList.value.filterNot { it.id == entry.id }.toMutableList()
        updated.add(0, entry)
        _historyList.value = updated
    }

    fun onUrlChange(newUrl: String) {
        _urlInput.value = newUrl
        if (newUrl.isBlank()) {
            _uiState.value = UiState.Idle
        }
    }

    fun checkClipboardForUrl(clipboardText: String) {
        val trimmed = clipboardText.trim()
        if (isValidYoutubeUrl(trimmed) && trimmed != _urlInput.value) {
            _urlInput.value = trimmed
            fetchVideoDetails(trimmed)
        }
    }

    fun fetchVideoDetails(targetUrl: String? = null) {
        val url = targetUrl ?: _urlInput.value.trim()
        if (!isValidYoutubeUrl(url)) {
            _uiState.value = UiState.Error("Please enter a valid YouTube URL")
            return
        }

        if (YTDLApplication.initFailed.value) {
            _uiState.value = UiState.Error(
                YTDLApplication.initErrorMessage.value ?: "Extraction engine initialization failed"
            )
            return
        }

        _uiState.value = UiState.Fetching

        viewModelScope.launch {
            try {
                val details = withContext(Dispatchers.IO) {
                    val dynamicArgs = ExtractorConfigManager.getExtractorArgs(YTDLApplication.instance)
                    val request = YoutubeDLRequest(url).apply {
                        addOption("--extractor-args", dynamicArgs)
                    }
                    val streamInfo = YoutubeDL.getInstance().getInfo(request)

                    val isPlaylist = url.contains("list=") || (streamInfo.title?.contains("Playlist", true) == true)

                    // Video options
                    val formats = streamInfo.formats ?: emptyList()
                    val videoHeights = listOf(1080, 720, 480, 360)
                    val videoOptions = videoHeights.map { height ->
                        val matchingFormat = formats.firstOrNull { it.height == height }
                        val approxSize = matchingFormat?.fileSize ?: matchingFormat?.fileSizeApproximate
                        DownloadOption(
                            id = "vid_$height",
                            label = "${height}p ${if (height >= 720) "HD" else ""}".trim(),
                            resolution = height.toString(),
                            ext = "mp4",
                            isAudioOnly = false,
                            requiresMerge = height >= 1080 || matchingFormat?.acodec == "none",
                            isHd = height >= 720,
                            approxSizeBytes = if (approxSize != null && approxSize > 0) approxSize else null
                        )
                    }

                    // Audio options
                    val audioFormat = formats.firstOrNull { it.vcodec == "none" && it.acodec != "none" }
                    val baseAudioSize = audioFormat?.fileSize ?: audioFormat?.fileSizeApproximate
                    val audioOptions = listOf(
                        DownloadOption(
                            id = "aud_320",
                            label = "320 kbps (High Quality)",
                            resolution = "320",
                            ext = "mp3",
                            isAudioOnly = true,
                            requiresMerge = true,
                            isHd = false,
                            approxSizeBytes = baseAudioSize?.let { (it * 2.5).toLong() }
                        ),
                        DownloadOption(
                            id = "aud_128",
                            label = "128 kbps (Standard)",
                            resolution = "128",
                            ext = "mp3",
                            isAudioOnly = true,
                            requiresMerge = true,
                            isHd = false,
                            approxSizeBytes = baseAudioSize
                        )
                    )

                    // If playlist, prepare playlist queue items (using available info or items)
                    val playlistItems = if (isPlaylist) {
                        List(10) { index ->
                            PlaylistItem(
                                id = "${streamInfo.id ?: "item"}_${index + 1}",
                                url = url,
                                title = "${streamInfo.title ?: "Track"} - Part ${index + 1}",
                                durationSeconds = streamInfo.duration.toLong()
                            )
                        }
                    } else emptyList()

                    VideoDetails(
                        id = streamInfo.id ?: url,
                        url = url,
                        title = streamInfo.title ?: "YouTube Media",
                        author = streamInfo.uploader ?: "YouTube Creator",
                        durationSeconds = streamInfo.duration.toLong(),
                        thumbnailUrl = streamInfo.thumbnail ?: "",
                        isPlaylist = isPlaylist,
                        playlistItems = playlistItems,
                        videoOptions = videoOptions,
                        audioOptions = audioOptions
                    )
                }

                _uiState.value = UiState.Ready(details)
            } catch (e: Exception) {
                val rawMsg = e.message.orEmpty().lowercase()
                val userMsg = when {
                    rawMsg.contains("403") || rawMsg.contains("forbidden") || rawMsg.contains("po token") ||
                    rawMsg.contains("bot") || rawMsg.contains("sign in to confirm") -> {
                        "YouTube blocked metadata extraction (HTTP 403 / Bot Challenge). Tap 'Check for updates' in the top bar."
                    }
                    rawMsg.contains("private video") -> "This video is private and requires account credentials."
                    rawMsg.contains("unavailable") -> "This video is unavailable or has been removed."
                    else -> e.message ?: "Failed to extract video information"
                }
                _uiState.value = UiState.Error(userMsg)
            }
        }
    }

    fun openQualitySheet() {
        _isQualitySheetOpen.value = true
    }

    fun closeQualitySheet() {
        _isQualitySheetOpen.value = false
    }

    fun setActiveTab(tab: SheetTab) {
        _activeTab.value = tab
    }

    fun startDownload(context: Context, option: DownloadOption) {
        val state = _uiState.value
        if (state !is UiState.Ready) return

        val details = state.details
        if (details.isPlaylist && details.playlistItems.isNotEmpty()) {
            startPlaylistQueue(context, details, option)
        } else {
            startSingleDownload(context, details.url, details.title, option)
        }
    }

    private fun startSingleDownload(context: Context, url: String, title: String, option: DownloadOption) {
        _currentDownloadingOption.value = option
        DownloadForegroundService.startDownload(
            context = context,
            url = url,
            title = title,
            option = option
        )
    }

    private fun startPlaylistQueue(
        context: Context,
        details: VideoDetails,
        option: DownloadOption
    ) {
        closeQualitySheet()
        playlistDownloadJob?.cancel()

        playlistDownloadJob = viewModelScope.launch {
            val items = details.playlistItems
            _playlistQueueState.value = PlaylistQueueState(
                isActive = true,
                playlistTitle = details.title,
                currentIndex = 0,
                totalCount = items.size,
                currentItemTitle = items.firstOrNull()?.title ?: "",
                itemProgress = 0f,
                completedCount = 0,
                failedCount = 0
            )

            for ((index, item) in items.withIndex()) {
                if (!_playlistQueueState.value.isActive) break

                _playlistQueueState.value = _playlistQueueState.value.copy(
                    currentIndex = index + 1,
                    currentItemTitle = item.title,
                    itemProgress = 0f
                )

                _currentDownloadingOption.value = option
                DownloadForegroundService.startDownload(
                    context = context,
                    url = item.url,
                    title = item.title,
                    option = option
                )

                // Await completion or failure of this specific item
                val event = DownloadEventBus.events.first { ev ->
                    ev is DownloadEvent.Completed || ev is DownloadEvent.Failed
                }

                if (event is DownloadEvent.Completed) {
                    _playlistQueueState.value = _playlistQueueState.value.copy(
                        completedCount = _playlistQueueState.value.completedCount + 1
                    )
                } else if (event is DownloadEvent.Failed) {
                    _playlistQueueState.value = _playlistQueueState.value.copy(
                        failedCount = _playlistQueueState.value.failedCount + 1
                    )
                }
            }

            _systemMessage.value = "Playlist download complete: ${_playlistQueueState.value.completedCount} succeeded."
            _playlistQueueState.value = _playlistQueueState.value.copy(isActive = false)
        }
    }

    fun cancelPlaylistQueue(context: Context) {
        playlistDownloadJob?.cancel()
        _playlistQueueState.value = _playlistQueueState.value.copy(isActive = false)
        cancelDownload(context)
    }

    fun cancelDownload(context: Context) {
        _currentDownloadingOption.value = null
        DownloadForegroundService.cancelDownload(context)
        DownloadEventBus.clearActive()
    }

    fun retryDownload(context: Context, entry: DownloadHistoryEntry) {
        _currentDownloadingOption.value = entry.formatOption
        DownloadForegroundService.startDownload(
            context = context,
            url = entry.url,
            title = entry.title,
            option = entry.formatOption
        )
    }

    fun triggerYtdlpUpdate(context: Context) {
        viewModelScope.launch {
            _isUpdatingYtdl.value = true
            val result = YtdlpUpdateWorker.runManualUpdate(context)
            _isUpdatingYtdl.value = false
            _systemMessage.value = result.fold(
                onSuccess = { "yt-dlp is up to date: $it" },
                onFailure = { "Update check: ${it.message}" }
            )
        }
    }

    fun clearSystemMessage() {
        _systemMessage.value = null
    }

    private fun isValidYoutubeUrl(url: String): Boolean {
        return url.isNotBlank() && (
            url.contains("youtube.com/watch") ||
            url.contains("youtu.be/") ||
            url.contains("youtube.com/shorts/") ||
            url.contains("youtube.com/playlist")
        )
    }
}
