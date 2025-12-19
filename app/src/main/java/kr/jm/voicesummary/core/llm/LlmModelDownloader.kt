package kr.jm.voicesummary.core.llm

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class LlmModelDownloader(private val context: Context) {

    companion object {
        private const val TAG = "LlmModelDownloader"
        private const val MODELS_DIR = "llm-models"
        private const val BUFFER_SIZE = 8192
        private const val PREFS_NAME = "llm_prefs"
        private const val KEY_SELECTED_MODEL = "selected_model"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _downloadState = MutableStateFlow<LlmDownloadState>(LlmDownloadState.Idle)
    val downloadState: StateFlow<LlmDownloadState> = _downloadState

    private val _selectedModel = MutableStateFlow(getSelectedModel())
    val selectedModel: StateFlow<LlmModel> = _selectedModel

    fun getSelectedModel(): LlmModel {
        val modelName = prefs.getString(KEY_SELECTED_MODEL, LlmModel.GEMMA_2B_GPU.name)
        return try {
            LlmModel.valueOf(modelName!!)
        } catch (e: Exception) {
            LlmModel.GEMMA_2B_GPU
        }
    }

    fun setSelectedModel(model: LlmModel) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model.name).apply()
        _selectedModel.value = model
        _downloadState.value = LlmDownloadState.Idle
    }

    suspend fun downloadModel(model: LlmModel): Boolean = withContext(Dispatchers.IO) {
        if (isModelDownloaded(model)) {
            Log.i(TAG, "Model ${model.name} already downloaded")
            setSelectedModel(model)
            _downloadState.value = LlmDownloadState.Completed
            return@withContext true
        }

        // assets에서 복사 시도
        if (copyFromAssets(model)) {
            return@withContext true
        }

        // assets에 없으면 URL 다운로드 시도
        try {
            _downloadState.value = LlmDownloadState.Downloading(0)

            val modelFile = getModelFile(model)
            modelFile.parentFile?.mkdirs()

            val url = URL(model.downloadUrl)
            var connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.instanceFollowRedirects = true

            // Hugging Face redirect 처리
            if (connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                connection.responseCode == 302
            ) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                connection = URL(newUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
            }

            val totalSize = connection.contentLength.toLong()
            var downloadedSize = 0L

            Log.i(TAG, "Downloading model ${model.name}: $totalSize bytes")

            connection.inputStream.use { input ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead

                        val progress = if (totalSize > 0) {
                            (downloadedSize * 100 / totalSize).toInt()
                        } else {
                            -1
                        }
                        _downloadState.value = LlmDownloadState.Downloading(progress)
                    }
                }
            }
            connection.disconnect()

            setSelectedModel(model)
            _downloadState.value = LlmDownloadState.Completed
            Log.i(TAG, "Model ${model.name} ready")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _downloadState.value = LlmDownloadState.Error(e.message ?: "Unknown error")
            false
        }
    }

    private fun copyFromAssets(model: LlmModel): Boolean {
        val assetPath = "llm-models/${model.fileName}"
        return try {
            _downloadState.value = LlmDownloadState.Downloading(0)
            
            val modelFile = getModelFile(model)
            modelFile.parentFile?.mkdirs()

            context.assets.open(assetPath).use { input ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            setSelectedModel(model)
            _downloadState.value = LlmDownloadState.Completed
            Log.i(TAG, "Model ${model.name} copied from assets")
            true
        } catch (e: Exception) {
            Log.d(TAG, "Model not found in assets: $assetPath")
            false
        }
    }

    fun getModelFile(model: LlmModel): File {
        val modelsDir = File(context.filesDir, "$MODELS_DIR/${model.name}")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return File(modelsDir, model.fileName)
    }

    fun getCurrentModelFile(): File {
        return getModelFile(getSelectedModel())
    }

    fun isModelDownloaded(model: LlmModel): Boolean {
        return getModelFile(model).exists()
    }

    fun isCurrentModelDownloaded(): Boolean {
        return isModelDownloaded(getSelectedModel())
    }

    fun deleteModel(model: LlmModel) {
        val modelFile = getModelFile(model)
        if (modelFile.exists()) {
            modelFile.delete()
        }
        modelFile.parentFile?.delete()
        if (model == getSelectedModel()) {
            _downloadState.value = LlmDownloadState.Idle
        }
    }

    fun getDownloadedModels(): List<LlmModel> {
        return LlmModel.entries.filter { isModelDownloaded(it) }
    }

    suspend fun importModelFromUri(uri: Uri, model: LlmModel): Boolean = withContext(Dispatchers.IO) {
        try {
            _downloadState.value = LlmDownloadState.Downloading(0)

            val modelFile = getModelFile(model)
            modelFile.parentFile?.mkdirs()

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        // 진행률은 알 수 없으므로 -1
                        _downloadState.value = LlmDownloadState.Downloading(-1)
                    }
                }
            }

            setSelectedModel(model)
            _downloadState.value = LlmDownloadState.Completed
            Log.i(TAG, "Model ${model.name} imported from file")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            _downloadState.value = LlmDownloadState.Error(e.message ?: "Import failed")
            false
        }
    }
}

sealed class LlmDownloadState {
    object Idle : LlmDownloadState()
    data class Downloading(val progress: Int) : LlmDownloadState()
    object Completed : LlmDownloadState()
    data class Error(val message: String) : LlmDownloadState()
}
