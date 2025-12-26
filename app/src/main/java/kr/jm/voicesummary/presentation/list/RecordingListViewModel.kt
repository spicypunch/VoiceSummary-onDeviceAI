package kr.jm.voicesummary.presentation.list

import android.app.Activity
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
import kr.jm.voicesummary.domain.model.Recording
import kr.jm.voicesummary.domain.model.SttModel
import kr.jm.voicesummary.domain.repository.AudioRepository
import kr.jm.voicesummary.domain.repository.BillingRepository
import kr.jm.voicesummary.domain.repository.RecordingRepository
import kr.jm.voicesummary.domain.repository.SttDownloadState
import kr.jm.voicesummary.domain.repository.SttRepository
import kr.jm.voicesummary.domain.repository.SttState
import kr.jm.voicesummary.service.RecordingService
import java.io.File

class RecordingListViewModel(
    private val recordingRepository: RecordingRepository,
    private val audioRepository: AudioRepository,
    private val sttRepository: SttRepository,
    private val billingRepository: BillingRepository,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingListUiState())
    val uiState: StateFlow<RecordingListUiState> = _uiState.asStateFlow()

    init {
        initRecordingObservers()
        initSttObservers()
        initBillingObservers()
    }

    private fun initRecordingObservers() {
        viewModelScope.launch { recordingRepository.syncFiles() }
        viewModelScope.launch {
            recordingRepository.getAllRecordings().collect { recordings ->
                _uiState.update { it.copy(recordings = recordings) }
            }
        }
        viewModelScope.launch {
            audioRepository.playbackState.collect { state ->
                _uiState.update { it.copy(playbackState = state) }
            }
        }
        viewModelScope.launch {
            audioRepository.currentPlayingFile.collect { file ->
                _uiState.update { it.copy(currentPlayingFilePath = file?.absolutePath) }
            }
        }
        viewModelScope.launch {
            audioRepository.currentPosition.collect { position ->
                _uiState.update { it.copy(currentPosition = position) }
            }
        }
        viewModelScope.launch {
            audioRepository.duration.collect { duration ->
                _uiState.update { it.copy(duration = duration) }
            }
        }
    }

    private fun initSttObservers() {
        viewModelScope.launch {
            sttRepository.transcriptionState.collect { state ->
                when (state) {
                    SttState.READY, SttState.ERROR -> {
                        _uiState.update { it.copy(transcribingFilePath = null) }
                    }
                    else -> {}
                }
            }
        }
        viewModelScope.launch {
            sttRepository.downloadState.collect { state ->
                _uiState.update {
                    it.copy(
                        sttDownloadState = state,
                        isSttModelDownloaded = state is SttDownloadState.Completed || sttRepository.isCurrentModelDownloaded(),
                        downloadedSttModels = sttRepository.getDownloadedModels().toSet()
                    )
                }
            }
        }
        viewModelScope.launch {
            sttRepository.selectedModel.collect { model ->
                _uiState.update {
                    it.copy(
                        selectedSttModel = model,
                        isSttModelDownloaded = sttRepository.isModelDownloaded(model),
                        downloadedSttModels = sttRepository.getDownloadedModels().toSet()
                    )
                }
            }
        }
        _uiState.update {
            it.copy(
                isSttModelDownloaded = sttRepository.isCurrentModelDownloaded(),
                selectedSttModel = sttRepository.getSelectedModel(),
                downloadedSttModels = sttRepository.getDownloadedModels().toSet()
            )
        }
    }

    private fun initBillingObservers() {
        billingRepository.startConnection()
        viewModelScope.launch {
            billingRepository.isPremium.collect { isPremium ->
                _uiState.update { it.copy(isPremium = isPremium) }
            }
        }
    }

    fun onPlayClick(recording: Recording) {
        audioRepository.play(File(recording.filePath))
    }

    fun onSeek(position: Int) {
        audioRepository.seekTo(position)
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
            audioRepository.stopPlayback()
            recordingRepository.deleteRecording(recording.filePath)
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
        if (!model.isFree && !_uiState.value.isPremium) {
            _uiState.update { it.copy(showPremiumDialog = true, showSttModelSelector = false) }
            return
        }

        sttRepository.setSelectedModel(model)
        _uiState.update {
            it.copy(
                showSttModelSelector = false,
                selectedSttModel = model,
                isSttModelDownloaded = sttRepository.isModelDownloaded(model)
            )
        }
        sttRepository.releaseTranscriber()
    }

    fun onShowPremiumDialog() {
        _uiState.update { it.copy(showPremiumDialog = true) }
    }

    fun onDismissPremiumDialog() {
        _uiState.update { it.copy(showPremiumDialog = false) }
    }

    fun onPurchasePremium(activityProvider: () -> Activity) {
        billingRepository.launchPurchaseFlow(activityProvider)
        _uiState.update { it.copy(showPremiumDialog = false) }
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

    override fun onCleared() {
        super.onCleared()
        billingRepository.endConnection()
    }

    class Factory(
        private val recordingRepository: RecordingRepository,
        private val audioRepository: AudioRepository,
        private val sttRepository: SttRepository,
        private val billingRepository: BillingRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecordingListViewModel(recordingRepository, audioRepository, sttRepository, billingRepository, context) as T
        }
    }
}
