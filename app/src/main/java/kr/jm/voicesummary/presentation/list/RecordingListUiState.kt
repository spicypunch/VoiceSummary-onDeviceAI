package kr.jm.voicesummary.presentation.list

import kr.jm.voicesummary.core.audio.PlaybackState
import kr.jm.voicesummary.domain.model.Recording

data class RecordingListUiState(
    val recordings: List<Recording> = emptyList(),
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val currentPlayingFilePath: String? = null,
    val currentPosition: Int = 0,
    val duration: Int = 0,
    val transcribingFilePath: String? = null,
    val summarizingFilePath: String? = null,
    val deleteTarget: Recording? = null,
    val expandedItems: Set<String> = emptySet(),
    val isLlmAvailable: Boolean = false
)
