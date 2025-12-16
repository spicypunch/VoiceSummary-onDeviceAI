package kr.jm.voicesummary.presentation.recording

import kr.jm.voicesummary.core.audio.RecordingState

data class RecordingUiState(
    val recordingState: RecordingState = RecordingState.IDLE,
    val recordingDuration: Long = 0L
)
