package kr.jm.voicesummary.presentation.list

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Transcribe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.jm.voicesummary.core.audio.PlaybackState
import kr.jm.voicesummary.domain.model.Recording
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun RecordingListScreen(
    uiState: RecordingListUiState,
    onPlayClick: (Recording) -> Unit,
    onLongClick: (Recording) -> Unit,
    onSeek: (Int) -> Unit,
    onTranscribeClick: (Recording) -> Unit,
    onSummarizeClick: (Recording) -> Unit,
    onExpandToggle: (String) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteCancel: () -> Unit,
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
                TextButton(onClick = onDeleteCancel) {
                    Text("취소")
                }
            }
        )
    }

    if (uiState.recordings.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "녹음 파일이 없습니다",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
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
                    isSummarizing = uiState.summarizingFilePath == recording.filePath,
                    isLlmAvailable = uiState.isLlmAvailable,
                    onPlayClick = { onPlayClick(recording) },
                    onLongClick = { onLongClick(recording) },
                    onSeek = onSeek,
                    onTranscribeClick = { onTranscribeClick(recording) },
                    onSummarizeClick = { onSummarizeClick(recording) },
                    onExpandToggle = { onExpandToggle(recording.filePath) }
                )
            }
        }
    }
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
    isSummarizing: Boolean,
    isLlmAvailable: Boolean,
    onPlayClick: () -> Unit,
    onLongClick: () -> Unit,
    onSeek: (Int) -> Unit,
    onTranscribeClick: () -> Unit,
    onSummarizeClick: () -> Unit,
    onExpandToggle: () -> Unit
) {
    val hasTranscription = !recording.transcription.isNullOrBlank()
    val hasSummary = !recording.summary.isNullOrBlank()
    val hasContent = hasTranscription || hasSummary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
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
                        text = formatFileName(recording.fileName),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatFileSize(recording.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onPlayClick) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "일시정지" else "재생",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onTranscribeClick,
                        enabled = !isTranscribing
                    ) {
                        if (isTranscribing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Transcribe,
                                contentDescription = "음성 인식",
                                tint = if (hasTranscription) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            )
                        }
                    }
                    if (hasTranscription && isLlmAvailable) {
                        IconButton(
                            onClick = onSummarizeClick,
                            enabled = !isSummarizing
                        ) {
                            if (isSummarizing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "요약",
                                    tint = if (hasSummary) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    }
                                )
                            }
                        }
                    }
                    if (hasContent) {
                        IconButton(onClick = onExpandToggle) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess
                                else Icons.Default.ExpandMore,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPosition.toLong()),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatDuration(duration.toLong()),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded && hasContent,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    if (hasSummary) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "AI 요약",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = recording.summary ?: "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (hasTranscription) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "음성 인식 결과",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = recording.transcription ?: "",
                            style = MaterialTheme.typography.bodyMedium
                        )
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
    } catch (e: Exception) {
        fileName
    }
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
