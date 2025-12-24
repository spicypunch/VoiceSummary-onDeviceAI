package kr.jm.voicesummary.presentation.recording

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
import kr.jm.voicesummary.domain.repository.AudioRepository
import kr.jm.voicesummary.domain.repository.RecordingState
import kr.jm.voicesummary.service.RecordingService

class RecordingViewModel(
    private val audioRepository: AudioRepository,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            audioRepository.recordingState.collect { state ->
                _uiState.update { it.copy(recordingState = state) }
            }
        }
        viewModelScope.launch {
            audioRepository.recordingDuration.collect { duration ->
                _uiState.update { it.copy(recordingDuration = duration) }
            }
        }
    }

    fun hasPermission(): Boolean = audioRepository.hasRecordPermission()

    fun onRecordClick() {
        if (_uiState.value.recordingState == RecordingState.RECORDING) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START_RECORDING
        }
        context.startForegroundService(intent)
    }

    private fun stopRecording() {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP_RECORDING
        }
        context.startService(intent)
    }

    class Factory(
        private val audioRepository: AudioRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecordingViewModel(audioRepository, context) as T
        }
    }
}
