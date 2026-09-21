package com.example.ytdownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.ytdownloader.MainActivity
import com.example.ytdownloader.data.config.ExtractorConfigManager
import com.example.ytdownloader.data.model.DownloadHistoryEntry
import com.example.ytdownloader.data.model.DownloadOption
import com.example.ytdownloader.data.model.DownloadStatus
import com.example.ytdownloader.util.DestinationDirectory
import com.example.ytdownloader.util.FormatUtils
import com.example.ytdownloader.util.MediaStoreHelper
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: NotificationManager

    private var currentProcessId: String? = null
    private var isCancelled = false
    private var currentDownloadId: String? = null
    private var currentHistoryEntry: DownloadHistoryEntry? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "YouTube Video"
                val optionId = intent.getStringExtra(EXTRA_OPTION_ID) ?: ""
                val resolution = intent.getStringExtra(EXTRA_RESOLUTION) ?: "720"
                val ext = intent.getStringExtra(EXTRA_EXT) ?: "mp4"
                val isAudioOnly = intent.getBooleanExtra(EXTRA_IS_AUDIO, false)
                val label = intent.getStringExtra(EXTRA_LABEL) ?: resolution
                val approxSize = intent.getLongExtra(EXTRA_APPROX_SIZE, -1L)

                val downloadOption = DownloadOption(
                    id = optionId,
                    label = label,
                    resolution = resolution,
                    ext = ext,
                    isAudioOnly = isAudioOnly,
                    requiresMerge = !isAudioOnly || ext == "mp3",
                    isHd = resolution.toIntOrNull()?.let { it >= 720 } ?: false,
                    approxSizeBytes = if (approxSize > 0) approxSize else null
                )

                startDownloadTask(url, title, downloadOption)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelCurrentDownload()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Android 15 (API 35) strict foreground service timeout handling.
     * dataSync foreground services are limited to 6 hours total per 24 hours while in background.
     * The service must immediately clean up and stop itself when this callback is invoked.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Android 15 onTimeout triggered for dataSync foreground service.")
        cancelCurrentDownload()
        currentDownloadId?.let { id ->
            currentHistoryEntry?.let { entry ->
                val timedOutEntry = entry.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = "Android 15 foreground timeout: 6-hour daily background execution quota reached."
                )
                DownloadEventBus.emitFailed(id, timedOutEntry.errorMessage ?: "Timeout", timedOutEntry)
            }
        }
        showFailureNotification(
            currentHistoryEntry?.title ?: "Download",
            "Stopped: Android 15 dataSync timeout limit reached."
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startDownloadTask(url: String, title: String, option: DownloadOption) {
        val downloadId = UUID.randomUUID().toString()
        val processId = "ytdl_proc_${System.currentTimeMillis()}"
        currentProcessId = processId
        currentDownloadId = downloadId
        isCancelled = false

        val initialNotification = buildProgressNotification(
            title = title,
            contentText = "Preparing download...",
            progress = 0,
            isIndeterminate = true
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        serviceScope.launch {
            val historyEntry = DownloadHistoryEntry(
                id = downloadId,
                videoId = url,
                url = url,
                title = title,
                formatOption = option,
                status = DownloadStatus.DOWNLOADING
            )
            currentHistoryEntry = historyEntry

            val tempDir = File(cacheDir, "ytdl_temp").apply { if (!exists()) mkdirs() }
            val sanitizedName = FormatUtils.sanitizeFilename(title)
            val outputTemplate = "${tempDir.absolutePath}/$sanitizedName.%(ext)s"

            // Pull dynamic extractor args (OTA configurable to adapt to YouTube client policy changes)
            val extractorArgs = ExtractorConfigManager.getExtractorArgs(applicationContext)

            val request = YoutubeDLRequest(url).apply {
                addOption("-o", outputTemplate)
                addOption("--no-mtime")
                addOption("--no-playlist")

                // Dynamic player client combinations (avoiding hardcoded strings)
                addOption("--extractor-args", extractorArgs)

                if (option.isAudioOnly) {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    if (option.resolution == "320") {
                        addOption("--audio-quality", "320k")
                    } else {
                        addOption("--audio-quality", "128k")
                    }
                } else {
                    val heightLimit = option.resolution.toIntOrNull() ?: 1080
                    addOption(
                        "-f",
                        "bestvideo[height<=$heightLimit]+bestaudio/best[height<=$heightLimit]/best"
                    )
                    addOption("--merge-output-format", "mp4")
                }
            }

            try {
                DownloadEventBus.emitProgress(
                    downloadId = downloadId,
                    progress = 0f,
                    speed = "",
                    eta = "",
                    statusText = "Downloading..."
                )

                YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
                    if (isCancelled) return@execute

                    val etaString = if (etaInSeconds > 0) "${etaInSeconds}s" else ""
                    val speedString = extractSpeed(line)
                    val statusText = if (progress >= 99f && option.requiresMerge) "Merging tracks..." else "Downloading"

                    DownloadEventBus.emitProgress(
                        downloadId = downloadId,
                        progress = progress,
                        speed = speedString,
                        eta = etaString,
                        statusText = statusText,
                        status = if (statusText.contains("Merging")) DownloadStatus.MERGING else DownloadStatus.DOWNLOADING
                    )

                    val updatedNotification = buildProgressNotification(
                        title = title,
                        contentText = "$statusText ${progress.toInt()}% $speedString $etaString".trim(),
                        progress = progress.toInt(),
                        isIndeterminate = false
                    )
                    notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                }

                if (isCancelled) {
                    handleCancellation(downloadId, historyEntry)
                    return@launch
                }

                // Locate the output file in temp directory
                val expectedExt = if (option.isAudioOnly) "mp3" else "mp4"
                val expectedMime = if (option.isAudioOnly) "audio/mpeg" else "video/mp4"
                val finalFileName = "$sanitizedName.$expectedExt"

                val downloadedFile = tempDir.listFiles()?.firstOrNull { file ->
                    file.name.startsWith(sanitizedName) && !file.name.endsWith(".part") && !file.name.endsWith(".ytdl")
                } ?: File(tempDir, finalFileName)

                if (!downloadedFile.exists() || downloadedFile.length() == 0L) {
                    throw IllegalStateException("Downloaded output file not found on disk")
                }

                // Export to public storage via Scoped Storage (MediaStore)
                val targetUri = MediaStoreHelper.saveMediaToPublicStorage(
                    context = applicationContext,
                    sourceFile = downloadedFile,
                    fileName = finalFileName,
                    mimeType = expectedMime,
                    isAudio = option.isAudioOnly,
                    destination = DestinationDirectory.DOWNLOADS_FOLDER
                )

                // Clean up local temp file after successful export
                downloadedFile.delete()

                val completedEntry = historyEntry.copy(
                    status = DownloadStatus.COMPLETED,
                    filePath = targetUri?.toString()
                )

                DownloadEventBus.emitCompleted(downloadId, targetUri, completedEntry)
                showCompletionNotification(title, targetUri)

            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                val userFriendlyReason = parseUserFriendlyError(e.message ?: "")

                val failedEntry = historyEntry.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = userFriendlyReason
                )
                DownloadEventBus.emitFailed(
                    downloadId = downloadId,
                    errorMessage = userFriendlyReason,
                    entry = failedEntry
                )
                showFailureNotification(title, userFriendlyReason)
            } finally {
                currentProcessId = null
                currentDownloadId = null
                currentHistoryEntry = null
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun cancelCurrentDownload() {
        isCancelled = true
        currentProcessId?.let { procId ->
            try {
                YoutubeDL.getInstance().destroyProcessById(procId)
            } catch (e: Exception) {
                Log.w(TAG, "Error killing process: ${e.message}")
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleCancellation(downloadId: String, entry: DownloadHistoryEntry) {
        val cancelledEntry = entry.copy(status = DownloadStatus.CANCELLED)
        DownloadEventBus.emitFailed(downloadId, "Cancelled by user", cancelledEntry)
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun extractSpeed(logLine: String?): String {
        if (logLine == null) return ""
        val match = Regex("""at\s+([0-9.]+[a-zA-Z]+/s)""").find(logLine)
        return match?.groups?.get(1)?.value ?: ""
    }

    /**
     * Translates technical scraper/HTTP exceptions into actionable user-facing messages.
     * Specifically identifies PO-Token, HTTP 403, bot-verification, and private media hurdles.
     */
    private fun parseUserFriendlyError(rawMessage: String): String {
        val lower = rawMessage.lowercase()
        return when {
            lower.contains("403") || lower.contains("forbidden") || lower.contains("po token") ||
            lower.contains("bot") || lower.contains("sign in to confirm") || lower.contains("visitor_data") -> {
                "YouTube blocked this format or requested bot verification (HTTP 403) — try a different quality or audio-only."
            }
            lower.contains("private video") || lower.contains("sign in if you've been granted access") -> {
                "This video is private or requires YouTube account sign-in."
            }
            lower.contains("unavailable") || lower.contains("removed by the uploader") -> {
                "This video is unavailable or has been removed."
            }
            lower.contains("extractorerror") || lower.contains("unable to extract") -> {
                "Extractor error — tap 'Check for updates' in the top bar to pull updated extraction rules."
            }
            lower.contains("no space left") || lower.contains("enospc") -> {
                "Insufficient device storage space."
            }
            else -> {
                rawMessage.lines().firstOrNull { it.isNotBlank() }?.take(100) ?: "Download failed"
            }
        }
    }

    private fun buildProgressNotification(
        title: String,
        contentText: String,
        progress: Int,
        isIndeterminate: Boolean
    ): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, isIndeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun showCompletionNotification(title: String, fileUri: Uri?) {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, if (title.endsWith(".mp3")) "audio/*" else "video/*")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingOpen)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showFailureNotification(title: String, reason: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$title: $reason")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for YouTube video downloads and audio extractions"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DownloadService"
        const val CHANNEL_ID = "ytdl_download_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_DOWNLOAD = "com.example.ytdownloader.action.START"
        const val ACTION_CANCEL_DOWNLOAD = "com.example.ytdownloader.action.CANCEL"

        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_OPTION_ID = "extra_option_id"
        const val EXTRA_RESOLUTION = "extra_resolution"
        const val EXTRA_EXT = "extra_ext"
        const val EXTRA_IS_AUDIO = "extra_is_audio"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_APPROX_SIZE = "extra_approx_size"

        fun startDownload(
            context: Context,
            url: String,
            title: String,
            option: DownloadOption
        ) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_OPTION_ID, option.id)
                putExtra(EXTRA_RESOLUTION, option.resolution)
                putExtra(EXTRA_EXT, option.ext)
                putExtra(EXTRA_IS_AUDIO, option.isAudioOnly)
                putExtra(EXTRA_LABEL, option.label)
                putExtra(EXTRA_APPROX_SIZE, option.approxSizeBytes ?: -1L)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelDownload(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
            }
            context.startService(intent)
        }
    }
}
