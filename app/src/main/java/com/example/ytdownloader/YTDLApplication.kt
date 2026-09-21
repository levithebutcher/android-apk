package com.example.ytdownloader

import android.app.Application
import android.util.Log
import com.example.ytdownloader.service.YtdlpUpdateWorker
import com.example.ytdownloader.util.CacheCleanupHelper
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class YTDLApplication : Application() {

    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this

        initializeLibraries()
        scheduleBackgroundMaintenance()
    }

    private fun initializeLibraries() {
        applicationScope.launch {
            try {
                Log.i(TAG, "Initializing YoutubeDL and FFmpeg native binaries...")
                YoutubeDL.getInstance().init(this@YTDLApplication)
                FFmpeg.getInstance().init(this@YTDLApplication)
                _isInitialized.value = true
                _initFailed.value = false
                Log.i(TAG, "YoutubeDL and FFmpeg successfully initialized.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize YoutubeDL/FFmpeg: ${e.message}", e)
                _initFailed.value = true
                _initErrorMessage.value = e.message ?: "Failed to unpack native libraries"
            }
        }
    }

    private fun scheduleBackgroundMaintenance() {
        applicationScope.launch {
            // Clean up any stale temp files older than 24 hours
            CacheCleanupHelper.cleanStaleCache(this@YTDLApplication)

            // Enqueue daily yt-dlp binary self-update check
            YtdlpUpdateWorker.schedulePeriodicUpdate(this@YTDLApplication)
        }
    }

    companion object {
        private const val TAG = "YTDLApplication"

        lateinit var instance: YTDLApplication
            private set

        private val _isInitialized = MutableStateFlow(false)
        val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

        private val _initFailed = MutableStateFlow(false)
        val initFailed: StateFlow<Boolean> = _initFailed.asStateFlow()

        private val _initErrorMessage = MutableStateFlow<String?>(null)
        val initErrorMessage: StateFlow<String?> = _initErrorMessage.asStateFlow()
    }
}
