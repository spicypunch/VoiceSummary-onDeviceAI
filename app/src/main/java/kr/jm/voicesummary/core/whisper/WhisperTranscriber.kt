package kr.jm.voicesummary.core.whisper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhisperTranscriber(private val context: Context) {

    companion object {
        private const val TAG = "WhisperTranscriber"
        private const val MODEL_NAME = "ggml-base.bin"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_DURATION_SEC = 300 // 5분씩 처리
        private const val SAMPLES_PER_CHUNK = SAMPLE_RATE * CHUNK_DURATION_SEC
    }

    private val whisperLib = WhisperLib()
    private var contextPtr: Long = 0

    private val _state = MutableStateFlow(TranscriberState.NOT_INITIALIZED)
    val state: StateFlow<TranscriberState> = _state

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (contextPtr != 0L) {
            return@withContext true
        }

        _state.value = TranscriberState.LOADING
        _progress.value = "모델 로딩 중..."

        val modelFile = getModelFile()
        
        // assets에서 모델 복사 (최초 1회)
        if (!modelFile.exists()) {
            _progress.value = "AI 엔진 준비 중..."
            try {
                copyModelFromAssets(modelFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy model from assets", e)
                _state.value = TranscriberState.ERROR
                _progress.value = "모델 복사 실패: ${e.message}"
                return@withContext false
            }
        }

        try {
            contextPtr = whisperLib.initContext(modelFile.absolutePath)
            if (contextPtr == 0L) {
                _state.value = TranscriberState.ERROR
                _progress.value = "모델 로딩 실패"
                return@withContext false
            }

            _state.value = TranscriberState.READY
            _progress.value = "준비 완료"
            Log.i(TAG, "Whisper initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Whisper", e)
            _state.value = TranscriberState.ERROR
            _progress.value = "초기화 실패: ${e.message}"
            false
        }
    }
    
    private fun copyModelFromAssets(destFile: File) {
        destFile.parentFile?.mkdirs()
        context.assets.open(MODEL_NAME).use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Log.i(TAG, "Model copied to: ${destFile.absolutePath}")
    }

    suspend fun transcribe(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        if (contextPtr == 0L) {
            return@withContext Result.failure(IllegalStateException("Whisper not initialized"))
        }

        _state.value = TranscriberState.TRANSCRIBING

        try {
            val totalSamples = getAudioSampleCount(audioFile)
            val totalChunks = (totalSamples + SAMPLES_PER_CHUNK - 1) / SAMPLES_PER_CHUNK
            Log.i(TAG, "Total samples: $totalSamples, chunks: $totalChunks")

            val resultBuilder = StringBuilder()

            for (chunkIndex in 0 until totalChunks) {
                val startSample = chunkIndex * SAMPLES_PER_CHUNK
                _progress.value = "텍스트 변환 중... (${chunkIndex + 1}/$totalChunks)"

                val audioData = loadWavFileChunk(audioFile, startSample, SAMPLES_PER_CHUNK)
                Log.i(TAG, "Processing chunk ${chunkIndex + 1}/$totalChunks, samples: ${audioData.size}")

                val chunkResult = whisperLib.transcribe(contextPtr, audioData, "ko")
                if (chunkResult.isNotBlank()) {
                    if (resultBuilder.isNotEmpty()) resultBuilder.append(" ")
                    resultBuilder.append(chunkResult.trim())
                }
            }

            _state.value = TranscriberState.READY
            _progress.value = "완료"

            Result.success(resultBuilder.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            _state.value = TranscriberState.ERROR
            _progress.value = "인식 실패: ${e.message}"
            Result.failure(e)
        }
    }

    private fun getAudioSampleCount(file: File): Int {
        val fileSize = file.length()
        val audioBytes = fileSize - 44 // WAV 헤더 제외
        return (audioBytes / 2).toInt() // 16bit = 2 bytes per sample
    }

    private fun loadWavFileChunk(file: File, startSample: Int, maxSamples: Int): FloatArray {
        val headerSize = 44
        val bytesPerSample = 2
        val startByte = headerSize + (startSample * bytesPerSample)

        RandomAccessFile(file, "r").use { raf ->
            raf.seek(startByte.toLong())

            val availableBytes = (raf.length() - startByte).toInt()
            val bytesToRead = minOf(maxSamples * bytesPerSample, availableBytes)
            val samplesToRead = bytesToRead / bytesPerSample

            val buffer = ByteArray(samplesToRead * bytesPerSample)
            raf.readFully(buffer)

            val shortBuffer = ByteBuffer.wrap(buffer)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()

            val floatArray = FloatArray(samplesToRead)
            for (i in 0 until samplesToRead) {
                floatArray[i] = shortBuffer.get(i) / 32768.0f
            }

            return floatArray
        }
    }

    fun getModelFile(): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return File(modelsDir, MODEL_NAME)
    }

    fun isModelDownloaded(): Boolean {
        return getModelFile().exists()
    }

    fun release() {
        if (contextPtr != 0L) {
            whisperLib.freeContext(contextPtr)
            contextPtr = 0
            _state.value = TranscriberState.NOT_INITIALIZED
        }
    }
}

enum class TranscriberState {
    NOT_INITIALIZED,
    LOADING,
    READY,
    TRANSCRIBING,
    MODEL_NOT_FOUND,
    ERROR
}
