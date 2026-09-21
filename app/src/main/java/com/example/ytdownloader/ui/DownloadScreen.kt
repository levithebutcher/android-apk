package com.example.ytdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ytdownloader.YTDLApplication
import com.example.ytdownloader.data.model.PlaylistQueueState
import com.example.ytdownloader.data.model.VideoDetails
import com.example.ytdownloader.ui.theme.RedPrimary
import com.example.ytdownloader.ui.theme.SuccessGreen
import com.example.ytdownloader.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    onRequestBatteryOptimization: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    val urlInput by viewModel.urlInput.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isQualitySheetOpen by viewModel.isQualitySheetOpen.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val currentDownloadingOption by viewModel.currentDownloadingOption.collectAsState()
    val currentProgress by viewModel.downloadProgress.collectAsState()
    val playlistQueueState by viewModel.playlistQueueState.collectAsState()
    val historyList by viewModel.historyList.collectAsState()
    val isUpdatingYtdl by viewModel.isUpdatingYtdl.collectAsState()
    val systemMessage by viewModel.systemMessage.collectAsState()

    val initFailed by YTDLApplication.initFailed.collectAsState()
    val initErrorMessage by YTDLApplication.initErrorMessage.collectAsState()

    var isHistorySheetOpen by remember { mutableStateOf(false) }

    val qualitySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(systemMessage) {
        systemMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSystemMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(RedPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "YT Downloader",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Update check
                    IconButton(
                        onClick = { viewModel.triggerYtdlpUpdate(context) },
                        enabled = !isUpdatingYtdl
                    ) {
                        if (isUpdatingYtdl) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = RedPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Check for extractor updates",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Battery troubleshooting option (Non-intrusive)
                    IconButton(onClick = onRequestBatteryOptimization) {
                        Icon(
                            imageVector = Icons.Default.BatteryAlert,
                            contentDescription = "Background Battery Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // History
                    IconButton(onClick = { isHistorySheetOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Download History",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Engine Initialization Error Alert
            if (initFailed) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = initErrorMessage ?: "Extraction engine initialization failed.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // URL Input Field with Paste & Auto-Fetch
            OutlinedTextField(
                value = urlInput,
                onValueChange = { viewModel.onUrlChange(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste YouTube or playlist link...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (urlInput.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onUrlChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text.orEmpty()
                                if (clip.isNotBlank()) {
                                    viewModel.checkClipboardForUrl(clip)
                                    keyboardController?.hide()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste from Clipboard",
                                tint = RedPrimary
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        viewModel.fetchVideoDetails()
                    }
                )
            )

            // Dedicated Batch Playlist Queue Card
            if (playlistQueueState.isActive) {
                PlaylistQueueCard(
                    queueState = playlistQueueState,
                    onCancel = { viewModel.cancelPlaylistQueue(context) }
                )
            }

            // Content Area based on State
            when (val state = uiState) {
                is UiState.Idle -> {
                    if (!playlistQueueState.isActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Enter a video/playlist link or tap paste to start",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                is UiState.Fetching -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = RedPrimary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Extracting video metadata & challenges...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                is UiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.fetchVideoDetails() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
                is UiState.Ready -> {
                    ResolvedVideoCard(
                        details = state.details,
                        onDownloadClick = { viewModel.openQualitySheet() }
                    )
                }
            }
        }
    }

    // Quality Bottom Sheet (Single Video or Playlist Preset)
    if (isQualitySheetOpen && uiState is UiState.Ready) {
        val details = (uiState as UiState.Ready).details
        QualityBottomSheet(
            sheetState = qualitySheetState,
            videoDetails = details,
            activeTab = activeTab,
            activeDownloadingOption = currentDownloadingOption,
            currentProgress = currentProgress,
            onTabSelected = { viewModel.setActiveTab(it) },
            onOptionSelected = { option ->
                viewModel.startDownload(context, option)
            },
            onCancelDownload = { viewModel.cancelDownload(context) },
            onDismiss = { viewModel.closeQualitySheet() }
        )
    }

    // History Sheet
    if (isHistorySheetOpen) {
        DownloadHistorySheet(
            sheetState = historySheetState,
            historyList = historyList,
            onRetry = { entry ->
                viewModel.retryDownload(context, entry)
                isHistorySheetOpen = false
                viewModel.openQualitySheet()
            },
            onDismiss = { isHistorySheetOpen = false }
        )
    }
}

@Composable
fun PlaylistQueueCard(
    queueState: PlaylistQueueState,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        tint = RedPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Download Queue: ${queueState.currentIndex} / ${queueState.totalCount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel Queue")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Overall Progress Bar
            val overallFraction = if (queueState.totalCount > 0) {
                (queueState.completedCount + queueState.failedCount).toFloat() / queueState.totalCount
            } else 0f
            LinearProgressIndicator(
                progress = { overallFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = RedPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Current: ${queueState.currentItemTitle}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Counters: Completed / Failed / Remaining
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Completed: ${queueState.completedCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessGreen,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Failed: ${queueState.failedCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (queueState.failedCount > 0) RedPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Remaining: ${queueState.remainingCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun ResolvedVideoCard(
    details: VideoDetails,
    onDownloadClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Thumbnail with Duration Tag
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(details.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = details.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (details.durationSeconds > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = FormatUtils.formatDuration(details.durationSeconds),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Video Title & Author
            Text(
                text = details.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = details.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Download Button
            Button(
                onClick = onDownloadClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (details.isPlaylist) "Download All (${details.playlistItems.size} items)" else "Download",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
