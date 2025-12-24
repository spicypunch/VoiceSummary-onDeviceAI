package kr.jm.voicesummary.domain.repository

import kotlinx.coroutines.flow.Flow
import kr.jm.voicesummary.domain.model.Recording

interface RecordingRepository {
    fun getAllRecordings(): Flow<List<Recording>>
    suspend fun getRecording(filePath: String): Recording?
    suspend fun saveRecording(recording: Recording)
    suspend fun updateTranscription(filePath: String, transcription: String)
    suspend fun deleteRecording(filePath: String)
    suspend fun syncFiles()
}
