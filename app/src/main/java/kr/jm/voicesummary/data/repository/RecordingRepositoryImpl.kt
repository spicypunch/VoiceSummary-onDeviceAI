package kr.jm.voicesummary.data.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.jm.voicesummary.data.local.RecordingDao
import kr.jm.voicesummary.data.local.RecordingEntity
import kr.jm.voicesummary.domain.model.Recording
import kr.jm.voicesummary.domain.repository.RecordingRepository
import java.io.File

class RecordingRepositoryImpl(
    private val recordingDao: RecordingDao,
    private val context: Context
) : RecordingRepository {

    override fun getAllRecordings(): Flow<List<Recording>> {
        return recordingDao.getAllRecordings().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getRecording(filePath: String): Recording? {
        return recordingDao.getRecording(filePath)?.toDomain()
    }

    override suspend fun saveRecording(recording: Recording) {
        recordingDao.insert(RecordingEntity.fromDomain(recording))
    }

    override suspend fun updateTranscription(filePath: String, transcription: String) {
        recordingDao.updateTranscription(filePath, transcription)
    }

    override suspend fun deleteRecording(filePath: String) {
        File(filePath).delete()
        recordingDao.delete(filePath)
    }

    override suspend fun syncFiles() {
        val recordingDir = context.getExternalFilesDir(null) ?: context.filesDir
        val files = recordingDir.listFiles { file ->
            file.isFile && file.extension == "wav"
        } ?: return

        val existingPaths = recordingDao.getAllFilePaths().toSet()

        files.forEach { file ->
            if (file.absolutePath !in existingPaths) {
                val recording = Recording(
                    filePath = file.absolutePath,
                    fileName = file.name,
                    createdAt = file.lastModified(),
                    fileSize = file.length()
                )
                recordingDao.insert(RecordingEntity.fromDomain(recording))
            }
        }
    }
}
