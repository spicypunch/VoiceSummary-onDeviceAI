package kr.jm.voicesummary.presentation.list

import kr.jm.voicesummary.core.audio.PlaybackState
import kr.jm.voicesummary.core.llm.LlmDownloadState
import kr.jm.voicesummary.core.llm.LlmModel
import kr.jm.voicesummary.core.stt.DownloadState
import kr.jm.voicesummary.core.stt.SttModel
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
    // STT
    val isSttModelDownloaded: Boolean = false,
    val sttDownloadState: DownloadState = DownloadState.Idle,
    val selectedSttModel: SttModel = SttModel.WHISPER_BASE,
    val showSttModelSelector: Boolean = false,
    val downloadedSttModels: Set<SttModel> = emptySet(),
    // LLM
    val isLlmModelDownloaded: Boolean = false,
    val llmDownloadState: LlmDownloadState = LlmDownloadState.Idle,
    val selectedLlmModel: LlmModel = LlmModel.GEMMA_2B_GPU,
    val showLlmModelSelector: Boolean = false,
    val downloadedLlmModels: Set<LlmModel> = emptySet()
)
