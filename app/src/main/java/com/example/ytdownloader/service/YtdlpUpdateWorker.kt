package com.example.ytdownloader.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ytdownloader.data.config.ExtractorConfigManager
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class YtdlpUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting periodic yt-dlp update check...")
            val updateStatus = YoutubeDL.getInstance().updateYoutubeDL(applicationContext)
            Log.i(TAG, "yt-dlp update check completed: $updateStatus")

            // Also pull latest dynamic extractor-args config
            ExtractorConfigManager.syncRemoteConfig(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update yt-dlp binary: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "YtdlpUpdateWorker"
        private const val UNIQUE_WORK_NAME = "YtdlpDailyUpdateWork"

        /**
         * Enqueues a daily background update check when connected to a network.
         */
        fun schedulePeriodicUpdate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<YtdlpUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        /**
         * Runs an immediate manual update check on-demand, updating both yt-dlp and extractor args.
         */
        suspend fun runManualUpdate(context: Context): kotlin.Result<String> = withContext(Dispatchers.IO) {
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(context)
                val configSyncResult = ExtractorConfigManager.syncRemoteConfig(context)
                val statusMessage = buildString {
                    append(status?.name ?: "yt-dlp binary updated")
                    if (configSyncResult.isSuccess) {
                        append(" & Extractor rules updated")
                    }
                }
                kotlin.Result.success(statusMessage)
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }
}
