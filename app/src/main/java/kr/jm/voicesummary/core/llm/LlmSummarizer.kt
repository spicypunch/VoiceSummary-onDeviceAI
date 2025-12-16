package kr.jm.voicesummary.core.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class LlmSummarizer(private val context: Context) {

    companion object {
        private const val TAG = "LlmSummarizer"
        private const val MODEL_NAME = "gemma-2b-it-gpu-int4.bin"
    }

    private var llmInference: LlmInference? = null

    private val _state = MutableStateFlow(LlmState.NOT_INITIALIZED)
    val state: StateFlow<LlmState> = _state

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (llmInference != null) {
            return@withContext true
        }

        _state.value = LlmState.LOADING
        _progress.value = "LLM 모델 로딩 중..."

        val modelFile = getModelFile()

        // assets에서 모델 복사 (최초 1회)
        if (!modelFile.exists()) {
            _progress.value = "AI 요약 엔진 준비 중..."
            try {
                copyModelFromAssets(modelFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy model from assets", e)
                _state.value = LlmState.MODEL_NOT_FOUND
                _progress.value = "모델 파일이 없습니다"
                return@withContext false
            }
        }

        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .setTopK(40)
                .setTemperature(0.8f)
                .setRandomSeed(101)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)

            _state.value = LlmState.READY
            _progress.value = "준비 완료"
            Log.i(TAG, "LLM initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LLM", e)
            _state.value = LlmState.ERROR
            _progress.value = "초기화 실패: ${e.message}"
            false
        }
    }

    suspend fun summarize(text: String): Result<String> = withContext(Dispatchers.IO) {
        if (llmInference == null) {
            return@withContext Result.failure(IllegalStateException("LLM not initialized"))
        }

        if (text.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Empty text"))
        }

        _state.value = LlmState.SUMMARIZING
        _progress.value = "요약 중..."

        try {
            val prompt = buildPrompt(text)
            val response = llmInference!!.generateResponse(prompt)

            _state.value = LlmState.READY
            _progress.value = "완료"

            val summary = extractSummary(response)
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Summarization failed", e)
            _state.value = LlmState.ERROR
            _progress.value = "요약 실패: ${e.message}"
            Result.failure(e)
        }
    }

    private fun buildPrompt(text: String): String {
        return """다음 텍스트를 한국어로 간결하게 요약해주세요. 핵심 내용만 3-5문장으로 정리해주세요.

텍스트:
$text

요약:"""
    }

    private fun extractSummary(response: String): String {
        return response.trim()
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

    fun getModelFile(): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return File(modelsDir, MODEL_NAME)
    }

    fun isModelDownloaded(): Boolean {
        // assets에 있으면 항상 사용 가능
        return try {
            context.assets.open(MODEL_NAME).close()
            true
        } catch (e: Exception) {
            getModelFile().exists()
        }
    }

    fun release() {
        llmInference?.close()
        llmInference = null
        _state.value = LlmState.NOT_INITIALIZED
    }
}

enum class LlmState {
    NOT_INITIALIZED,
    LOADING,
    READY,
    SUMMARIZING,
    MODEL_NOT_FOUND,
    ERROR
}
