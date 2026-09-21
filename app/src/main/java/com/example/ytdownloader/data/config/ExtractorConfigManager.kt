package com.example.ytdownloader.data.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages dynamic extractor arguments for yt-dlp.
 * Prevents client combination changes (e.g., android, web, mweb, ios)
 * from requiring full app releases by pulling updates over-the-air.
 */
object ExtractorConfigManager {

    private const val TAG = "ExtractorConfigManager"
    private const val PREFS_NAME = "ytdl_extractor_config"
    private const val KEY_EXTRACTOR_ARGS = "extractor_args"
    private const val KEY_REMOTE_CONFIG_URL = "remote_config_url"

    // Default robust client combo
    const val DEFAULT_EXTRACTOR_ARGS = "youtube:player_client=android,web"

    // Optional remote config URL (e.g. GitHub raw JSON or project endpoint)
    // Sample JSON schema: { "youtube_extractor_args": "youtube:player_client=android,web" }
    private const val DEFAULT_REMOTE_CONFIG_URL =
        "https://raw.githubusercontent.com/yt-dlp/yt-dlp/master/configurations/android_defaults.json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getExtractorArgs(context: Context): String {
        return getPrefs(context).getString(KEY_EXTRACTOR_ARGS, DEFAULT_EXTRACTOR_ARGS)
            ?: DEFAULT_EXTRACTOR_ARGS
    }

    fun setExtractorArgs(context: Context, args: String) {
        getPrefs(context).edit().putString(KEY_EXTRACTOR_ARGS, args).apply()
        Log.i(TAG, "Updated extractor args to: $args")
    }

    fun getRemoteConfigUrl(context: Context): String {
        return getPrefs(context).getString(KEY_REMOTE_CONFIG_URL, DEFAULT_REMOTE_CONFIG_URL)
            ?: DEFAULT_REMOTE_CONFIG_URL
    }

    fun setRemoteConfigUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_REMOTE_CONFIG_URL, url).apply()
    }

    /**
     * Attempts to fetch the latest extractor configuration over the network.
     * Called by YtdlpUpdateWorker during scheduled background checks or manual updates.
     */
    suspend fun syncRemoteConfig(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val configUrl = getRemoteConfigUrl(context)
        try {
            val url = URL(configUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)
                if (json.has("youtube_extractor_args")) {
                    val newArgs = json.getString("youtube_extractor_args")
                    setExtractorArgs(context, newArgs)
                    return@withContext Result.success("Synced remote extractor args: $newArgs")
                }
            }
            Result.success("Using current extractor configuration")
        } catch (e: Exception) {
            Log.w(TAG, "Could not sync remote config (network or endpoint unavailable): ${e.message}")
            Result.failure(e)
        }
    }
}
