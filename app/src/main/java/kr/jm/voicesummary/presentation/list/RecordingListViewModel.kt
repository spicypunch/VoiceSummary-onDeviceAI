package kr.jm.voicesummary.presentation.list

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.jm.voicesummary.core.audio.AudioPlayer
import kr.jm.voicesummary.core.llm.LlmSummarizer
import kr.jm.voicesummary.core.whisper.WhisperTranscriber
import kr.jm.voicesummary.domain.model.Recording
import kr.jm.voicesummary.domain.repository.RecordingRepository
import kr.jm.voicesummary.service.RecordingService
import java.io.File

class RecordingListViewModel(
    private val repository: RecordingRepository,
    private val audioPlayer: AudioPlayer,
    private val whisperTranscriber: WhisperTranscriber,
    private val llmSummarizer: LlmSummarizer,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingListUiState())
    val uiState: StateFlow<RecordingListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.syncFiles()
        }
        viewModelScope.launch {
            repository.getAllRecordings().collect { recordings ->
                _uiState.update { it.copy(recordings = recordings) }
            }
        }
        viewModelScope.launch {
            audioPlayer.playbackState.collect { state ->
                _uiState.update { it.copy(playbackState = state) }
            }
        }
        viewModelScope.launch {
            audioPlayer.currentFile.collect { file ->
                _uiState.update { it.copy(currentPlayingFilePath = file?.absolutePath) }
            }
        }
        viewModelScope.launch {
            audioPlayer.currentPosition.collect { position ->
                _uiState.update { it.copy(currentPosition = position) }
            }
        }
        viewModelScope.launch {
            audioPlayer.duration.collect { duration ->
                _uiState.update { it.copy(duration = duration) }
            }
        }
        viewModelScope.launch {
            whisperTranscriber.state.collect { state ->
                val isTranscribing = state == kr.jm.voicesummary.core.whisper.TranscriberState.TRANSCRIBING
                if (!isTranscribing && _uiState.value.transcribingFilePath != null) {
                    _uiState.update { it.copy(transcribingFilePath = null) }
                }
            }
        }
        viewModelScope.launch {
            llmSummarizer.state.collect { state ->
                val isSummarizing = state == kr.jm.voicesummary.core.llm.LlmState.SUMMARIZING
                if (!isSummarizing && _uiState.value.summarizingFilePath != null) {
                    _uiState.update { it.copy(summarizingFilePath = null) }
                }
            }
        }
        _uiState.update { it.copy(isLlmAvailable = llmSummarizer.isModelDownloaded()) }
    }

    fun onPlayClick(recording: Recording) {
        audioPlayer.play(File(recording.filePath))
    }

    fun onSeek(position: Int) {
        audioPlayer.seekTo(position)
    }

    fun onLongClick(recording: Recording) {
        _uiState.update { it.copy(deleteTarget = recording) }
    }

    fun onDeleteCancel() {
        _uiState.update { it.copy(deleteTarget = null) }
    }

    fun onDeleteConfirm() {
        val recording = _uiState.value.deleteTarget ?: return
        viewModelScope.launch {
            audioPlayer.stop()
            repository.deleteRecording(recording.filePath)
            _uiState.update { it.copy(deleteTarget = null) }
        }
    }

    fun onTranscribeClick(recording: Recording) {
        if (_uiState.value.transcribingFilePath != null) return

        _uiState.update { it.copy(transcribingFilePath = recording.filePath) }
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_TRANSCRIBE
            putExtra(RecordingService.EXTRA_FILE_PATH, recording.filePath)
        }
        context.startForegroundService(intent)
    }

    fun onSummarizeClick(recording: Recording) {
        if (_uiState.value.summarizingFilePath != null) return
        if (recording.transcription.isNullOrBlank()) return

        _uiState.update { it.copy(summarizingFilePath = recording.filePath) }
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_SUMMARIZE
            putExtra(RecordingService.EXTRA_FILE_PATH, recording.filePath)
        }
        context.startForegroundService(intent)
    }

    fun onExpandToggle(filePath: String) {
        _uiState.update { state ->
            val newExpanded = if (filePath in state.expandedItems) {
                state.expandedItems - filePath
            } else {
                state.expandedItems + filePath
            }
            state.copy(expandedItems = newExpanded)
        }
    }

    class Factory(
        private val repository: RecordingRepository,
        private val audioPlayer: AudioPlayer,
        private val whisperTranscriber: WhisperTranscriber,
        private val llmSummarizer: LlmSummarizer,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecordingListViewModel(repository, audioPlayer, whisperTranscriber, llmSummarizer, context) as T
        }
    }
}
