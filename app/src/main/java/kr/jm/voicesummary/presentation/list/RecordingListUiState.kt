package kr.jm.voicesummary.presentation.list

import kr.jm.voicesummary.domain.model.Recording
import kr.jm.voicesummary.domain.model.SttModel
import kr.jm.voicesummary.domain.repository.PlaybackState
import kr.jm.voicesummary.domain.repository.SttDownloadState

data class RecordingListUiState(
    val recordings: List<Recording> = emptyList(),
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val currentPlayingFilePath: String? = null,
    val currentPosition: Int = 0,
    val duration: Int = 0,
    val transcribingFilePath: String? = null,
    val deleteTarget: Recording? = null,
    val expandedItems: Set<String> = emptySet(),
    val isSttModelDownloaded: Boolean = false,
    val sttDownloadState: SttDownloadState = SttDownloadState.Idle,
    val selectedSttModel: SttModel = SttModel.WHISPER_TINY,
    val showSttModelSelector: Boolean = false,
    val downloadedSttModels: Set<SttModel> = emptySet(),
    val isPremium: Boolean = false,
    val showPremiumDialog: Boolean = false
)
