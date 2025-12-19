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
import kr.jm.voicesummary.core.llm.LlmDownloadState
import kr.jm.voicesummary.core.llm.LlmModel
import kr.jm.voicesummary.core.llm.LlmModelDownloader
import kr.jm.voicesummary.core.llm.LlmSummarizer
import kr.jm.voicesummary.core.stt.DownloadState
import kr.jm.voicesummary.core.stt.SherpaModelDownloader
import kr.jm.voicesummary.core.stt.SherpaTranscriber
import kr.jm.voicesummary.core.stt.SttModel
import kr.jm.voicesummary.domain.model.Recording
import kr.jm.voicesummary.domain.repository.RecordingRepository
import kr.jm.voicesummary.service.RecordingService
import java.io.File

class RecordingListViewModel(
    private val repository: RecordingRepository,
    private val audioPlayer: AudioPlayer,
    private val sttTranscriber: SherpaTranscriber,
    private val sttModelDownloader: SherpaModelDownloader,
    private val llmSummarizer: LlmSummarizer,
    private val llmModelDownloader: LlmModelDownloader,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingListUiState())
    val uiState: StateFlow<RecordingListUiState> = _uiState.asStateFlow()

    init {
        initRecordingObservers()
        initSttObservers()
        initLlmObservers()
    }

    private fun initRecordingObservers() {
        viewModelScope.launch { repository.syncFiles() }
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
    }

    private fun initSttObservers() {
        viewModelScope.launch {
            sttTranscriber.state.collect { state ->
                when (state) {
                    kr.jm.voicesummary.core.stt.SttState.READY,
                    kr.jm.voicesummary.core.stt.SttState.ERROR -> {
                        _uiState.update { it.copy(transcribingFilePath = null) }
                    }
                    else -> {}
                }
            }
        }
        viewModelScope.launch {
            sttModelDownloader.downloadState.collect { state ->
                _uiState.update {
                    it.copy(
                        sttDownloadState = state,
                        isSttModelDownloaded = state is DownloadState.Completed || sttModelDownloader.isCurrentModelDownloaded(),
                        downloadedSttModels = sttModelDownloader.getDownloadedModels().toSet()
                    )
                }
            }
        }
        viewModelScope.launch {
            sttModelDownloader.selectedModel.collect { model ->
                _uiState.update {
                    it.copy(
                        selectedSttModel = model,
                        isSttModelDownloaded = sttModelDownloader.isModelDownloaded(model),
                        downloadedSttModels = sttModelDownloader.getDownloadedModels().toSet()
                    )
                }
            }
        }
        _uiState.update {
            it.copy(
                isSttModelDownloaded = sttModelDownloader.isCurrentModelDownloaded(),
                selectedSttModel = sttModelDownloader.getSelectedModel(),
                downloadedSttModels = sttModelDownloader.getDownloadedModels().toSet()
            )
        }
    }

    private fun initLlmObservers() {
        viewModelScope.launch {
            llmSummarizer.state.collect { state ->
                when (state) {
                    kr.jm.voicesummary.core.llm.LlmState.READY,
                    kr.jm.voicesummary.core.llm.LlmState.ERROR -> {
                        _uiState.update { it.copy(summarizingFilePath = null) }
                    }
                    else -> {}
                }
            }
        }
        viewModelScope.launch {
            llmModelDownloader.downloadState.collect { state ->
                _uiState.update {
                    it.copy(
                        llmDownloadState = state,
                        isLlmModelDownloaded = state is LlmDownloadState.Completed || llmModelDownloader.isCurrentModelDownloaded(),
                        downloadedLlmModels = llmModelDownloader.getDownloadedModels().toSet()
                    )
                }
            }
        }
        viewModelScope.launch {
            llmModelDownloader.selectedModel.collect { model ->
                _uiState.update {
                    it.copy(
                        selectedLlmModel = model,
                        isLlmModelDownloaded = llmModelDownloader.isModelDownloaded(model),
                        downloadedLlmModels = llmModelDownloader.getDownloadedModels().toSet()
                    )
                }
            }
        }
        _uiState.update {
            it.copy(
                isLlmModelDownloaded = llmModelDownloader.isCurrentModelDownloaded(),
                selectedLlmModel = llmModelDownloader.getSelectedModel(),
                downloadedLlmModels = llmModelDownloader.getDownloadedModels().toSet()
            )
        }
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
        if (!_uiState.value.isSttModelDownloaded) return

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
        if (!_uiState.value.isLlmModelDownloaded) return

        _uiState.update { it.copy(summarizingFilePath = recording.filePath) }
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_SUMMARIZE
            putExtra(RecordingService.EXTRA_FILE_PATH, recording.filePath)
        }
        context.startForegroundService(intent)
    }

    // STT Model
    fun onDownloadSttModel(model: SttModel) {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_DOWNLOAD_STT_MODEL
            putExtra(RecordingService.EXTRA_MODEL_NAME, model.name)
        }
        context.startForegroundService(intent)
    }

    fun onShowSttModelSelector() {
        _uiState.update { it.copy(showSttModelSelector = true) }
    }

    fun onDismissSttModelSelector() {
        _uiState.update { it.copy(showSttModelSelector = false) }
    }

    fun onSelectSttModel(model: SttModel) {
        sttModelDownloader.setSelectedModel(model)
        _uiState.update {
            it.copy(
                showSttModelSelector = false,
                selectedSttModel = model,
                isSttModelDownloaded = sttModelDownloader.isModelDownloaded(model)
            )
        }
        sttTranscriber.release()
    }

    // LLM Model
    fun onDownloadLlmModel(model: LlmModel) {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_DOWNLOAD_LLM_MODEL
            putExtra(RecordingService.EXTRA_MODEL_NAME, model.name)
        }
        context.startForegroundService(intent)
    }

    fun onShowLlmModelSelector() {
        _uiState.update { it.copy(showLlmModelSelector = true) }
    }

    fun onDismissLlmModelSelector() {
        _uiState.update { it.copy(showLlmModelSelector = false) }
    }

    fun onSelectLlmModel(model: LlmModel) {
        llmModelDownloader.setSelectedModel(model)
        _uiState.update {
            it.copy(
                showLlmModelSelector = false,
                selectedLlmModel = model,
                isLlmModelDownloaded = llmModelDownloader.isModelDownloaded(model)
            )
        }
        llmSummarizer.release()
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
        private val sttTranscriber: SherpaTranscriber,
        private val sttModelDownloader: SherpaModelDownloader,
        private val llmSummarizer: LlmSummarizer,
        private val llmModelDownloader: LlmModelDownloader,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecordingListViewModel(
                repository, audioPlayer, sttTranscriber, sttModelDownloader,
                llmSummarizer, llmModelDownloader, context
            ) as T
        }
    }
}
