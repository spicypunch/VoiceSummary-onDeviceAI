package kr.jm.voicesummary.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.jm.voicesummary.MainActivity
import kr.jm.voicesummary.R
import kr.jm.voicesummary.VoiceSummaryApp
import kr.jm.voicesummary.core.audio.RecordingState
import kr.jm.voicesummary.core.llm.LlmDownloadState
import kr.jm.voicesummary.core.llm.LlmModel
import kr.jm.voicesummary.core.stt.DownloadState
import kr.jm.voicesummary.core.stt.SttModel
import kr.jm.voicesummary.domain.model.Recording
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_RECORDING = "START_RECORDING"
        const val ACTION_STOP_RECORDING = "STOP_RECORDING"
        const val ACTION_TRANSCRIBE = "TRANSCRIBE"
        const val ACTION_SUMMARIZE = "SUMMARIZE"
        const val ACTION_DOWNLOAD_STT_MODEL = "DOWNLOAD_STT_MODEL"
        const val ACTION_DOWNLOAD_LLM_MODEL = "DOWNLOAD_LLM_MODEL"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_MODEL_NAME = "model_name"
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val app by lazy { application as VoiceSummaryApp }

    private var currentRecordingFile: File? = null

    private val _serviceState = MutableStateFlow(ServiceState())
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeRecordingState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_TRANSCRIBE -> {
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
                if (filePath != null) transcribe(filePath)
            }
            ACTION_SUMMARIZE -> {
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
                if (filePath != null) summarize(filePath)
            }
            ACTION_DOWNLOAD_STT_MODEL -> {
                val modelName = intent.getStringExtra(EXTRA_MODEL_NAME)
                if (modelName != null) downloadSttModel(modelName)
            }
            ACTION_DOWNLOAD_LLM_MODEL -> {
                val modelName = intent.getStringExtra(EXTRA_MODEL_NAME)
                if (modelName != null) downloadLlmModel(modelName)
            }
        }
        return START_STICKY
    }

    private fun observeRecordingState() {
        serviceScope.launch {
            app.audioRecorder.recordingState.collect { state ->
                _serviceState.value = _serviceState.value.copy(recordingState = state)
                updateNotification()
            }
        }
        serviceScope.launch {
            app.audioRecorder.recordingDuration.collect { duration ->
                _serviceState.value = _serviceState.value.copy(recordingDuration = duration)
            }
        }
    }

    fun startRecording() {
        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date()) + ".wav"
        val recordingDir = getExternalFilesDir(null) ?: filesDir
        currentRecordingFile = File(recordingDir, fileName)

        startForegroundWithType(useMicrophone = true)

        serviceScope.launch {
            app.audioRecorder.startRecording(currentRecordingFile!!)
        }
    }

    fun stopRecording() {
        app.audioRecorder.stopRecording()
        currentRecordingFile?.let { file ->
            if (file.exists()) {
                serviceScope.launch {
                    val recording = Recording(
                        filePath = file.absolutePath,
                        fileName = file.name,
                        createdAt = System.currentTimeMillis(),
                        fileSize = file.length()
                    )
                    app.recordingRepository.saveRecording(recording)
                }
            }
        }
        stopForegroundIfIdle()
    }

    fun transcribe(filePath: String) {
        if (_serviceState.value.transcribingFilePath != null) return

        // 모델이 없으면 STT 불가
        if (!app.sttModelDownloader.isCurrentModelDownloaded()) {
            return
        }

        _serviceState.value = _serviceState.value.copy(transcribingFilePath = filePath)
        startForegroundWithType()
        updateNotification("텍스트 변환 중...")

        serviceScope.launch {
            if (app.sttTranscriber.initialize()) {
                app.sttTranscriber.transcribe(File(filePath))
                    .onSuccess { text ->
                        app.recordingRepository.updateTranscription(filePath, text)
                    }
            }
            
            _serviceState.value = _serviceState.value.copy(transcribingFilePath = null)
            stopForegroundIfIdle()
        }
    }

    fun summarize(filePath: String) {
        if (_serviceState.value.summarizingFilePath != null) return

        serviceScope.launch {
            val recording = app.recordingRepository.getRecording(filePath)
            if (recording?.transcription.isNullOrBlank()) return@launch

            _serviceState.value = _serviceState.value.copy(summarizingFilePath = filePath)
            startForegroundWithType()
            updateNotification("AI 요약 중...")

            if (app.llmSummarizer.initialize()) {
                app.llmSummarizer.summarize(recording!!.transcription!!)
                    .onSuccess { summary ->
                        app.recordingRepository.updateSummary(filePath, summary)
                    }
            }
            _serviceState.value = _serviceState.value.copy(summarizingFilePath = null)
            stopForegroundIfIdle()
        }
    }

    fun downloadSttModel(modelName: String) {
        val model = try {
            SttModel.valueOf(modelName)
        } catch (e: Exception) {
            return
        }

        if (_serviceState.value.downloadingSttModel != null) return

        _serviceState.value = _serviceState.value.copy(downloadingSttModel = model)
        startForegroundWithType()

        serviceScope.launch {
            launch {
                app.sttModelDownloader.downloadState.collect { state ->
                    when (state) {
                        is DownloadState.Downloading -> {
                            updateNotification("STT 모델 다운로드 중... ${state.progress}%")
                        }
                        is DownloadState.Extracting -> {
                            updateNotification("압축 해제 중...")
                        }
                        is DownloadState.Completed -> {
                            updateNotification("다운로드 완료")
                        }
                        is DownloadState.Error -> {
                            updateNotification("다운로드 실패")
                        }
                        else -> {}
                    }
                }
            }

            app.sttModelDownloader.downloadModel(model)
            
            _serviceState.value = _serviceState.value.copy(downloadingSttModel = null)
            stopForegroundIfIdle()
        }
    }

    fun downloadLlmModel(modelName: String) {
        val model = try {
            LlmModel.valueOf(modelName)
        } catch (e: Exception) {
            return
        }

        if (_serviceState.value.downloadingLlmModel != null) return

        _serviceState.value = _serviceState.value.copy(downloadingLlmModel = model)
        startForegroundWithType()

        serviceScope.launch {
            launch {
                app.llmModelDownloader.downloadState.collect { state ->
                    when (state) {
                        is LlmDownloadState.Downloading -> {
                            updateNotification("LLM 모델 다운로드 중... ${state.progress}%")
                        }
                        is LlmDownloadState.Completed -> {
                            updateNotification("다운로드 완료")
                        }
                        is LlmDownloadState.Error -> {
                            updateNotification("다운로드 실패")
                        }
                        else -> {}
                    }
                }
            }

            app.llmModelDownloader.downloadModel(model)
            
            _serviceState.value = _serviceState.value.copy(downloadingLlmModel = null)
            stopForegroundIfIdle()
        }
    }

    private fun startForegroundWithType(useMicrophone: Boolean = false) {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (useMicrophone) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundIfIdle() {
        val state = _serviceState.value
        if (state.recordingState == RecordingState.IDLE &&
            state.transcribingFilePath == null &&
            state.summarizingFilePath == null &&
            state.downloadingSttModel == null &&
            state.downloadingLlmModel == null
        ) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "녹음 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "녹음 및 AI 처리 상태를 표시합니다"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String? = null): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val state = _serviceState.value
        val text = contentText ?: when {
            state.recordingState == RecordingState.RECORDING -> "녹음 중..."
            state.transcribingFilePath != null -> "텍스트 변환 중..."
            state.summarizingFilePath != null -> "AI 요약 중..."
            state.downloadingSttModel != null -> "STT 모델 다운로드 중..."
            state.downloadingLlmModel != null -> "LLM 모델 다운로드 중..."
            else -> "대기 중"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoiceSummary")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String? = null) {
        val notification = createNotification(contentText)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

data class ServiceState(
    val recordingState: RecordingState = RecordingState.IDLE,
    val recordingDuration: Long = 0L,
    val transcribingFilePath: String? = null,
    val summarizingFilePath: String? = null,
    val downloadingSttModel: SttModel? = null,
    val downloadingLlmModel: LlmModel? = null
)
