package kr.jm.voicesummary.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RecordingEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
}
