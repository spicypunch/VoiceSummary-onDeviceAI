package kr.jm.voicesummary.di

import android.content.Context
import androidx.room.Room
import kr.jm.voicesummary.data.audio.AudioRepositoryImpl
import kr.jm.voicesummary.data.billing.BillingRepositoryImpl
import kr.jm.voicesummary.data.local.AppDatabase
import kr.jm.voicesummary.data.repository.RecordingRepositoryImpl
import kr.jm.voicesummary.data.stt.SttRepositoryImpl
import kr.jm.voicesummary.domain.repository.AudioRepository
import kr.jm.voicesummary.domain.repository.BillingRepository
import kr.jm.voicesummary.domain.repository.RecordingRepository
import kr.jm.voicesummary.domain.repository.SttRepository

class AppContainer(context: Context) {

    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "voicesummary.db"
    ).fallbackToDestructiveMigration().build()

    val recordingRepository: RecordingRepository = RecordingRepositoryImpl(
        database.recordingDao(),
        context
    )

    val audioRepository: AudioRepository = AudioRepositoryImpl(context)

    val sttRepository: SttRepository = SttRepositoryImpl(context)

    val billingRepository: BillingRepository = BillingRepositoryImpl(context)
}
