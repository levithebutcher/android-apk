package com.example.ytdownloader.util

import java.util.Locale

object FormatUtils {

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0:00"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
        }
    }

    fun formatFileSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return "—"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "~%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "~%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "~%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(120)
    }
}
