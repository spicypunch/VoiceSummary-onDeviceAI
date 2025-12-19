package kr.jm.voicesummary

import android.app.Application
import androidx.room.Room
import kr.jm.voicesummary.core.audio.AudioPlayer
import kr.jm.voicesummary.core.audio.AudioRecorder
import kr.jm.voicesummary.core.llm.LlmSummarizer
import kr.jm.voicesummary.core.stt.SherpaModelDownloader
import kr.jm.voicesummary.core.stt.SherpaTranscriber
import kr.jm.voicesummary.data.local.AppDatabase
import kr.jm.voicesummary.data.repository.RecordingRepositoryImpl
import kr.jm.voicesummary.domain.repository.RecordingRepository

class VoiceSummaryApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var recordingRepository: RecordingRepository
        private set

    lateinit var audioRecorder: AudioRecorder
        private set

    lateinit var audioPlayer: AudioPlayer
        private set

    lateinit var sttTranscriber: SherpaTranscriber
        private set

    lateinit var sttModelDownloader: SherpaModelDownloader
        private set

    lateinit var llmSummarizer: LlmSummarizer
        private set

    override fun onCreate() {
        super.onCreate()

        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "voicesummary.db"
        ).build()

        recordingRepository = RecordingRepositoryImpl(
            database.recordingDao(),
            this
        )

        audioRecorder = AudioRecorder(this)
        audioPlayer = AudioPlayer()
        sttModelDownloader = SherpaModelDownloader(this)
        sttTranscriber = SherpaTranscriber(this, sttModelDownloader)
        llmSummarizer = LlmSummarizer(this)
    }
}
