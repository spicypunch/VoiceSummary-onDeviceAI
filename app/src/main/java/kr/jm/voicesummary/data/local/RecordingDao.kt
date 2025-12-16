package kr.jm.voicesummary.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE filePath = :filePath")
    suspend fun getRecording(filePath: String): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity)

    @Query("UPDATE recordings SET transcription = :transcription WHERE filePath = :filePath")
    suspend fun updateTranscription(filePath: String, transcription: String)

    @Query("UPDATE recordings SET summary = :summary WHERE filePath = :filePath")
    suspend fun updateSummary(filePath: String, summary: String)

    @Query("DELETE FROM recordings WHERE filePath = :filePath")
    suspend fun delete(filePath: String)

    @Query("SELECT filePath FROM recordings")
    suspend fun getAllFilePaths(): List<String>
}
