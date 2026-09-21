package com.example.ytdownloader.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class DestinationDirectory {
    DOWNLOADS_FOLDER,
    MEDIA_COLLECTIONS
}

object MediaStoreHelper {

    private const val TAG = "MediaStoreHelper"
    private const val DEFAULT_SUBFOLDER = "YouTube"

    /**
     * Copies a local temporary file into Android's public storage using Scoped Storage (MediaStore).
     *
     * @param context Application context
     * @param sourceFile Temporary file in app's cache directory
     * @param fileName Desired public file name (including extension)
     * @param mimeType MIME type (e.g., "video/mp4", "audio/mpeg")
     * @param isAudio Whether this media is audio-only
     * @param destination Destination strategy (defaults to DOWNLOADS_FOLDER)
     * @return Content Uri of the newly saved public file, or null on failure
     */
    fun saveMediaToPublicStorage(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String,
        isAudio: Boolean,
        destination: DestinationDirectory = DestinationDirectory.DOWNLOADS_FOLDER
    ): Uri? {
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file does not exist: ${sourceFile.absolutePath}")
            return null
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStoreQ(context, sourceFile, fileName, mimeType, isAudio, destination)
        } else {
            saveViaLegacyStorage(context, sourceFile, fileName, mimeType, isAudio, destination)
        }
    }

    private fun saveViaMediaStoreQ(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String,
        isAudio: Boolean,
        destination: DestinationDirectory
    ): Uri? {
        val resolver = context.contentResolver

        val (contentUri, relativePath) = when (destination) {
            DestinationDirectory.DOWNLOADS_FOLDER -> {
                Pair(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${Environment.DIRECTORY_DOWNLOADS}/$DEFAULT_SUBFOLDER"
                )
            }
            DestinationDirectory.MEDIA_COLLECTIONS -> {
                if (isAudio) {
                    Pair(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        "${Environment.DIRECTORY_MUSIC}/$DEFAULT_SUBFOLDER"
                    )
                } else {
                    Pair(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        "${Environment.DIRECTORY_MOVIES}/$DEFAULT_SUBFOLDER"
                    )
                }
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val itemUri = resolver.insert(contentUri, values) ?: run {
            Log.e(TAG, "Failed to insert MediaStore record for $fileName")
            return null
        }

        return try {
            resolver.openOutputStream(itemUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IllegalStateException("Failed to open output stream for $itemUri")

            // Finish pending status so media becomes visible to system and other apps
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)

            Log.i(TAG, "Successfully exported to MediaStore: $itemUri")
            itemUri
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to MediaStore: ${e.message}", e)
            resolver.delete(itemUri, null, null)
            null
        }
    }

    private fun saveViaLegacyStorage(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String,
        isAudio: Boolean,
        destination: DestinationDirectory
    ): Uri? {
        val baseDir = when (destination) {
            DestinationDirectory.DOWNLOADS_FOLDER -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
            DestinationDirectory.MEDIA_COLLECTIONS -> {
                if (isAudio) {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                } else {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                }
            }
        }

        val targetDir = File(baseDir, DEFAULT_SUBFOLDER)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val targetFile = File(targetDir, fileName)
        return try {
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Trigger system media scan so the file appears in media queries
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(mimeType),
                null
            )

            Uri.fromFile(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to legacy storage: ${e.message}", e)
            null
        }
    }
}
