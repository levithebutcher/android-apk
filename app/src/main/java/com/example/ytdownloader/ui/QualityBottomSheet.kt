package com.example.ytdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytdownloader.data.model.DownloadOption
import com.example.ytdownloader.data.model.DownloadProgress
import com.example.ytdownloader.data.model.SheetTab
import com.example.ytdownloader.data.model.VideoDetails
import com.example.ytdownloader.ui.theme.RedPrimary
import com.example.ytdownloader.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityBottomSheet(
    sheetState: SheetState,
    videoDetails: VideoDetails,
    activeTab: SheetTab,
    activeDownloadingOption: DownloadOption?,
    currentProgress: DownloadProgress?,
    onTabSelected: (SheetTab) -> Unit,
    onOptionSelected: (DownloadOption) -> Unit,
    onCancelDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (videoDetails.isPlaylist) "Select Quality for Playlist" else "Download Media",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Two-Segment TabRow: Video | Audio
            TabRow(
                selectedTabIndex = if (activeTab == SheetTab.VIDEO) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = RedPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = activeTab == SheetTab.VIDEO,
                    onClick = { onTabSelected(SheetTab.VIDEO) },
                    text = {
                        Text(
                            text = "Video",
                            fontWeight = if (activeTab == SheetTab.VIDEO) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = activeTab == SheetTab.AUDIO,
                    onClick = { onTabSelected(SheetTab.AUDIO) },
                    text = {
                        Text(
                            text = "Audio (MP3)",
                            fontWeight = if (activeTab == SheetTab.AUDIO) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Options List
            val currentOptions = if (activeTab == SheetTab.VIDEO) {
                videoDetails.videoOptions
            } else {
                videoDetails.audioOptions
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(currentOptions, key = { it.id }) { option ->
                    val isCurrentOptionActive = activeDownloadingOption?.id == option.id

                    QualityRow(
                        option = option,
                        isActive = isCurrentOptionActive,
                        progress = if (isCurrentOptionActive) currentProgress else null,
                        onClick = {
                            if (!isCurrentOptionActive) {
                                onOptionSelected(option)
                            }
                        },
                        onCancel = onCancelDownload
                    )
                }
            }
        }
    }
}

@Composable
fun QualityRow(
    option: DownloadOption,
    isActive: Boolean,
    progress: DownloadProgress?,
    onClick: () -> Unit,
    onCancel: () -> Unit
) {
    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .clickable(enabled = !isActive, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Leading Media Icon + Title & Badges
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (option.isAudioOnly) Color(0xFF2E7D32).copy(alpha = 0.15f)
                        else RedPrimary.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (option.isAudioOnly) Icons.Default.Audiotrack else Icons.Default.Movie,
                    contentDescription = null,
                    tint = if (option.isAudioOnly) Color(0xFF2E7D32) else RedPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (option.isHd) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(RedPrimary)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "HD",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = "${option.ext.uppercase()} • ${FormatUtils.formatFileSize(option.approxSizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Trailing Content: Either Progress or Download Trigger
        if (isActive && progress != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${progress.progress.toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RedPrimary
                    )
                    if (progress.speed.isNotBlank()) {
                        Text(
                            text = progress.speed,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress.progress / 100f },
                        modifier = Modifier.size(32.dp),
                        color = RedPrimary,
                        strokeWidth = 3.dp
                    )
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download ${option.label}",
                    tint = RedPrimary
                )
            }
        }
    }
}
