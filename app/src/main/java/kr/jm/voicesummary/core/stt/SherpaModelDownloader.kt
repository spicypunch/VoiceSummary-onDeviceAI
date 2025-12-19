package kr.jm.voicesummary.core.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class SherpaModelDownloader(private val context: Context) {

    companion object {
        private const val TAG = "SherpaModelDownloader"
        private const val MODELS_ROOT_DIR = "sherpa-models"
        private const val BUFFER_SIZE = 8192
        private const val PREFS_NAME = "stt_prefs"
        private const val KEY_SELECTED_MODEL = "selected_model"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    private val _selectedModel = MutableStateFlow(getSelectedModel())
    val selectedModel: StateFlow<SttModel> = _selectedModel

    fun getSelectedModel(): SttModel {
        val modelName = prefs.getString(KEY_SELECTED_MODEL, SttModel.WHISPER_BASE.name)
        return try {
            SttModel.valueOf(modelName!!)
        } catch (e: Exception) {
            SttModel.WHISPER_BASE
        }
    }

    fun setSelectedModel(model: SttModel) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model.name).apply()
        _selectedModel.value = model
        // 모델 변경 시 다운로드 상태 초기화
        _downloadState.value = DownloadState.Idle
    }

    suspend fun downloadModel(model: SttModel): Boolean = withContext(Dispatchers.IO) {
        if (isModelDownloaded(model)) {
            Log.i(TAG, "Model ${model.name} already downloaded")
            setSelectedModel(model)
            _downloadState.value = DownloadState.Completed
            return@withContext true
        }

        try {
            _downloadState.value = DownloadState.Downloading(0)
            
            val tempFile = File(context.cacheDir, "whisper-model.tar.bz2")
            
            val url = URL(model.downloadUrl)
            var connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.instanceFollowRedirects = true
            
            // GitHub redirect 처리
            if (connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                connection.responseCode == 302) {
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
                FileOutputStream(tempFile).use { output ->
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
                        _downloadState.value = DownloadState.Downloading(progress)
                    }
                }
            }
            connection.disconnect()
            
            Log.i(TAG, "Download complete, extracting...")
            _downloadState.value = DownloadState.Extracting
            
            extractTarBz2(tempFile, context.filesDir)
            tempFile.delete()
            copyModelFiles(model)
            
            setSelectedModel(model)
            _downloadState.value = DownloadState.Completed
            Log.i(TAG, "Model ${model.name} ready")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
            false
        }
    }

    private fun extractTarBz2(tarBz2File: File, destDir: File) {
        BZip2CompressorInputStream(BufferedInputStream(tarBz2File.inputStream())).use { bzIn ->
            TarArchiveInputStream(bzIn).use { tarIn ->
                var entry = tarIn.nextTarEntry
                while (entry != null) {
                    val outFile = File(destDir, entry.name)
                    
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var len: Int
                            while (tarIn.read(buffer).also { len = it } != -1) {
                                out.write(buffer, 0, len)
                            }
                        }
                    }
                    entry = tarIn.nextTarEntry
                }
            }
        }
    }

    private fun copyModelFiles(model: SttModel) {
        val extractedDir = File(context.filesDir, model.extractedDirName)
        val modelDir = getModelDir(model)
        
        listOf(model.encoderFile, model.decoderFile, model.tokensFile).forEach { fileName ->
            val srcFile = File(extractedDir, fileName)
            val destFile = File(modelDir, fileName)
            if (srcFile.exists()) {
                srcFile.copyTo(destFile, overwrite = true)
            }
        }
        
        extractedDir.deleteRecursively()
    }

    // 모델별 개별 폴더 반환
    fun getModelDir(model: SttModel): File {
        val modelDir = File(context.filesDir, "$MODELS_ROOT_DIR/${model.name}")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        return modelDir
    }

    // 현재 선택된 모델의 폴더 반환
    fun getCurrentModelDir(): File {
        return getModelDir(getSelectedModel())
    }

    fun isModelDownloaded(model: SttModel): Boolean {
        val modelDir = getModelDir(model)
        return File(modelDir, model.encoderFile).exists() &&
                File(modelDir, model.decoderFile).exists() &&
                File(modelDir, model.tokensFile).exists()
    }

    fun isCurrentModelDownloaded(): Boolean {
        return isModelDownloaded(getSelectedModel())
    }

    // 특정 모델 삭제
    fun deleteModel(model: SttModel) {
        val modelDir = getModelDir(model)
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }
        if (model == getSelectedModel()) {
            _downloadState.value = DownloadState.Idle
        }
    }

    // 모든 모델 삭제
    fun deleteAllModels() {
        val rootDir = File(context.filesDir, MODELS_ROOT_DIR)
        if (rootDir.exists()) {
            rootDir.deleteRecursively()
        }
        _downloadState.value = DownloadState.Idle
    }

    // 다운로드된 모델 목록
    fun getDownloadedModels(): List<SttModel> {
        return SttModel.entries.filter { isModelDownloaded(it) }
    }
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Extracting : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}
