package kr.jm.voicesummary.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kr.jm.voicesummary.domain.model.Recording

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey
    val filePath: String,
    val fileName: String,
    val createdAt: Long,
    val fileSize: Long,
    val transcription: String? = null
) {
    fun toDomain() = Recording(
        filePath = filePath,
        fileName = fileName,
        createdAt = createdAt,
        fileSize = fileSize,
        transcription = transcription
    )

    companion object {
        fun fromDomain(recording: Recording) = RecordingEntity(
            filePath = recording.filePath,
            fileName = recording.fileName,
            createdAt = recording.createdAt,
            fileSize = recording.fileSize,
            transcription = recording.transcription
        )
    }
}
