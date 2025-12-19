package kr.jm.voicesummary.core.stt

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SherpaTranscriber(
    private val context: Context,
    private val modelDownloader: SherpaModelDownloader
) {

    companion object {
        private const val TAG = "SherpaTranscriber"
        private const val SAMPLE_RATE = 16000
    }

    private var recognizer: OfflineRecognizer? = null
    private var loadedModel: SttModel? = null

    private val _state = MutableStateFlow(SttState.NOT_INITIALIZED)
    val state: StateFlow<SttState> = _state

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        val selectedModel = modelDownloader.getSelectedModel()
        
        // 이미 같은 모델이 로드되어 있으면 스킵
        if (recognizer != null && loadedModel == selectedModel) {
            return@withContext true
        }

        // 다른 모델이면 기존 것 해제
        if (recognizer != null) {
            recognizer?.release()
            recognizer = null
        }

        _state.value = SttState.LOADING
        _progress.value = "모델 로딩 중..."

        if (!modelDownloader.isModelDownloaded(selectedModel)) {
            _state.value = SttState.MODEL_NOT_FOUND
            _progress.value = "모델 파일이 없습니다. 다운로드가 필요합니다."
            return@withContext false
        }

        try {
            val modelDir = modelDownloader.getModelDir(selectedModel)
            
            val whisperConfig = OfflineWhisperModelConfig(
                encoder = File(modelDir, selectedModel.encoderFile).absolutePath,
                decoder = File(modelDir, selectedModel.decoderFile).absolutePath,
                language = "ko",
                task = "transcribe"
            )

            val modelConfig = OfflineModelConfig(
                whisper = whisperConfig,
                tokens = File(modelDir, selectedModel.tokensFile).absolutePath,
                numThreads = 4,
                provider = "nnapi"
            )

            val config = OfflineRecognizerConfig(
                featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = modelConfig
            )

            recognizer = OfflineRecognizer(null, config)
            loadedModel = selectedModel

            _state.value = SttState.READY
            _progress.value = "준비 완료"
            Log.i(TAG, "Sherpa-ONNX initialized with ${selectedModel.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Sherpa-ONNX", e)
            _state.value = SttState.ERROR
            _progress.value = "초기화 실패: ${e.message}"
            false
        }
    }

    suspend fun transcribe(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        val rec = recognizer
        if (rec == null) {
            return@withContext Result.failure(IllegalStateException("Recognizer not initialized"))
        }

        _state.value = SttState.TRANSCRIBING
        _progress.value = "텍스트 변환 중..."

        try {
            val audioData = loadWavFile(audioFile)
            Log.i(TAG, "Processing ${audioData.size} samples")

            val stream = rec.createStream()
            stream.acceptWaveform(audioData, SAMPLE_RATE)
            rec.decode(stream)
            
            val result = rec.getResult(stream).text
            stream.release()

            _state.value = SttState.READY
            _progress.value = "완료"

            Log.i(TAG, "Transcription complete: ${result.length} chars")
            Result.success(result.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            _state.value = SttState.ERROR
            _progress.value = "인식 실패: ${e.message}"
            Result.failure(e)
        }
    }

    private fun loadWavFile(file: File): FloatArray {
        val headerSize = 44
        val bytesPerSample = 2

        RandomAccessFile(file, "r").use { raf ->
            raf.seek(headerSize.toLong())

            val audioBytes = (raf.length() - headerSize).toInt()
            val numSamples = audioBytes / bytesPerSample

            val buffer = ByteArray(audioBytes)
            raf.readFully(buffer)

            val shortBuffer = ByteBuffer.wrap(buffer)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()

            val floatArray = FloatArray(numSamples)
            for (i in 0 until numSamples) {
                floatArray[i] = shortBuffer.get(i) / 32768.0f
            }

            return floatArray
        }
    }

    fun release() {
        recognizer?.release()
        recognizer = null
        loadedModel = null
        _state.value = SttState.NOT_INITIALIZED
    }
}

enum class SttState {
    NOT_INITIALIZED,
    LOADING,
    READY,
    TRANSCRIBING,
    MODEL_NOT_FOUND,
    ERROR
}
