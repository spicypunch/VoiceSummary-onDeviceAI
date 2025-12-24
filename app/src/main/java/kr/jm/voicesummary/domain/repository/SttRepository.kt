package kr.jm.voicesummary.domain.repository

import kotlinx.coroutines.flow.StateFlow
import kr.jm.voicesummary.domain.model.SttModel
import java.io.File

interface SttRepository {
    val downloadState: StateFlow<SttDownloadState>
    val selectedModel: StateFlow<SttModel>
    val transcriptionState: StateFlow<SttState>

    fun getSelectedModel(): SttModel
    fun setSelectedModel(model: SttModel)
    fun isModelDownloaded(model: SttModel): Boolean
    fun isCurrentModelDownloaded(): Boolean
    fun getDownloadedModels(): List<SttModel>
    
    suspend fun downloadModel(model: SttModel): Boolean
    suspend fun initializeTranscriber(): Boolean
    suspend fun transcribe(audioFile: File): Result<String>
    fun releaseTranscriber()
}

sealed class SttDownloadState {
    data object Idle : SttDownloadState()
    data class Downloading(val progress: Int) : SttDownloadState()
    data object Extracting : SttDownloadState()
    data object Completed : SttDownloadState()
    data class Error(val message: String) : SttDownloadState()
}

enum class SttState {
    NOT_INITIALIZED,
    LOADING,
    READY,
    TRANSCRIBING,
    MODEL_NOT_FOUND,
    ERROR
}
