package com.example.ytdownloader.util

import android.content.Context
import android.util.Log
import java.io.File

object CacheCleanupHelper {

    private const val TAG = "CacheCleanupHelper"
    private const val STALE_THRESHOLD_MILLIS = 24 * 60 * 60 * 1000L // 24 hours

    /**
     * Scans and cleans up stale download temp files (.part, .ytdl, merged files) in the cache directory.
     */
    fun cleanStaleCache(context: Context) {
        try {
            val cacheDir = context.cacheDir ?: return
            val downloadTempDir = File(cacheDir, "ytdl_temp")
            val now = System.currentTimeMillis()

            var deletedCount = 0
            var reclaimedBytes = 0L

            if (downloadTempDir.exists() && downloadTempDir.isDirectory) {
                downloadTempDir.listFiles()?.forEach { file ->
                    val age = now - file.lastModified()
                    if (age > STALE_THRESHOLD_MILLIS) {
                        val size = file.length()
                        if (file.delete()) {
                            deletedCount++
                            reclaimedBytes += size
                        }
                    }
                }
            }

            if (deletedCount > 0) {
                Log.i(
                    TAG,
                    "Cleaned up $deletedCount stale temp files, reclaimed ${reclaimedBytes / (1024 * 1024)} MB"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error while cleaning stale cache: ${e.message}")
        }
    }
}
