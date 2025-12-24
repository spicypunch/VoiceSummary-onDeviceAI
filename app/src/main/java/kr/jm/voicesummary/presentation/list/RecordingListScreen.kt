package kr.jm.voicesummary.presentation.list

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Transcribe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.jm.voicesummary.domain.model.Recording
import kr.jm.voicesummary.domain.model.SttModel
import kr.jm.voicesummary.domain.repository.PlaybackState
import kr.jm.voicesummary.domain.repository.SttDownloadState
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun RecordingListScreen(
    uiState: RecordingListUiState,
    onPlayClick: (Recording) -> Unit,
    onLongClick: (Recording) -> Unit,
    onSeek: (Int) -> Unit,
    onTranscribeClick: (Recording) -> Unit,
    onExpandToggle: (String) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteCancel: () -> Unit,
    onDownloadSttModel: (SttModel) -> Unit,
    onShowSttModelSelector: () -> Unit,
    onDismissSttModelSelector: () -> Unit,
    onSelectSttModel: (SttModel) -> Unit,
    modifier: Modifier = Modifier
) {
    uiState.deleteTarget?.let { recording ->
        AlertDialog(
            onDismissRequest = onDeleteCancel,
            title = { Text("녹음 삭제") },
            text = { Text("이 녹음을 삭제하시겠습니까?\n${formatFileName(recording.fileName)}") },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteCancel) { Text("취소") }
            }
        )
    }

    if (uiState.showSttModelSelector) {
        SttModelSelectorDialog(
            selectedModel = uiState.selectedSttModel,
            downloadedModels = uiState.downloadedSttModels,
            onSelectModel = { onSelectSttModel(it); onDismissSttModelSelector() },
            onDismiss = onDismissSttModelSelector
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        SttModelBanner(
            selectedModel = uiState.selectedSttModel,
            isModelDownloaded = uiState.isSttModelDownloaded,
            downloadState = uiState.sttDownloadState,
            onDownloadClick = { onDownloadSttModel(uiState.selectedSttModel) },
            onChangeModelClick = onShowSttModelSelector
        )

        if (uiState.recordings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("녹음 파일이 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.recordings, key = { it.filePath }) { recording ->
                    RecordingItem(
                        recording = recording,
                        isPlaying = uiState.currentPlayingFilePath == recording.filePath &&
                                uiState.playbackState == PlaybackState.PLAYING,
                        isSelected = uiState.currentPlayingFilePath == recording.filePath,
                        currentPosition = if (uiState.currentPlayingFilePath == recording.filePath)
                            uiState.currentPosition else 0,
                        duration = if (uiState.currentPlayingFilePath == recording.filePath)
                            uiState.duration else 0,
                        isExpanded = recording.filePath in uiState.expandedItems,
                        isTranscribing = uiState.transcribingFilePath == recording.filePath,
                        isSttAvailable = uiState.isSttModelDownloaded,
                        onPlayClick = { onPlayClick(recording) },
                        onLongClick = { onLongClick(recording) },
                        onSeek = onSeek,
                        onTranscribeClick = { onTranscribeClick(recording) },
                        onExpandToggle = { onExpandToggle(recording.filePath) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SttModelBanner(
    selectedModel: SttModel,
    isModelDownloaded: Boolean,
    downloadState: SttDownloadState,
    onDownloadClick: () -> Unit,
    onChangeModelClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isModelDownloaded) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("STT 모델: ${selectedModel.displayName}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${selectedModel.description} (${selectedModel.sizeDescription})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                TextButton(onClick = onChangeModelClick) { Text("변경") }
            }

            if (!isModelDownloaded) {
                Spacer(modifier = Modifier.height(12.dp))
                when (downloadState) {
                    is SttDownloadState.Idle -> {
                        Button(onClick = onDownloadClick) { Text("다운로드") }
                    }
                    is SttDownloadState.Downloading -> {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            if (downloadState.progress >= 0) {
                                LinearProgressIndicator(progress = { downloadState.progress / 100f }, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${downloadState.progress}%", style = MaterialTheme.typography.bodySmall)
                            } else {
                                LinearProgressIndicator(modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("다운로드 중...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    is SttDownloadState.Extracting -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("압축 해제 중...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is SttDownloadState.Completed -> {
                        Text("다운로드 완료", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    is SttDownloadState.Error -> {
                        Column {
                            Text("다운로드 실패: ${downloadState.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onDownloadClick) { Text("다시 시도") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SttModelSelectorDialog(
    selectedModel: SttModel,
    downloadedModels: Set<SttModel>,
    onSelectModel: (SttModel) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("STT 모델 선택") },
        text = {
            Column {
                SttModel.entries.forEach { model ->
                    val isDownloaded = model in downloadedModels
                    val isSelected = model == selectedModel
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .combinedClickable(onClick = { onSelectModel(model) }),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isDownloaded) {
                                        Text("다운됨", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(model.sizeDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                            Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingItem(
    recording: Recording,
    isPlaying: Boolean,
    isSelected: Boolean,
    currentPosition: Int,
    duration: Int,
    isExpanded: Boolean,
    isTranscribing: Boolean,
    isSttAvailable: Boolean,
    onPlayClick: () -> Unit,
    onLongClick: () -> Unit,
    onSeek: (Int) -> Unit,
    onTranscribeClick: () -> Unit,
    onExpandToggle: () -> Unit
) {
    val hasTranscription = !recording.transcription.isNullOrBlank()

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { }, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        formatFileName(recording.fileName),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        formatFileSize(recording.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onPlayClick) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "일시정지" else "재생",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onTranscribeClick, enabled = !isTranscribing && isSttAvailable) {
                        if (isTranscribing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "음성 인식",
                                tint = if (!isSttAvailable) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                else if (hasTranscription) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    if (hasTranscription) {
                        IconButton(onClick = onExpandToggle) {
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "닫기" else "펼치기",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isSelected && duration > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(currentPosition.toLong()), style = MaterialTheme.typography.bodySmall)
                    Text(formatDuration(duration.toLong()), style = MaterialTheme.typography.bodySmall)
                }
            }

            AnimatedVisibility(visible = isExpanded && hasTranscription, enter = expandVertically(), exit = shrinkVertically()) {
                val clipboardManager = LocalClipboardManager.current
                val context = LocalContext.current

                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("음성 인식 결과", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(recording.transcription ?: ""))
                                Toast.makeText(context, "텍스트가 복사되었습니다", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, "복사", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(recording.transcription ?: "", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun formatFileName(fileName: String): String {
    return try {
        val nameWithoutExt = fileName.removeSuffix(".wav")
        val inputFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(nameWithoutExt)
        date?.let { outputFormat.format(it) } ?: fileName
    } catch (e: Exception) { fileName }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000 / 60) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
