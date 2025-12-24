package kr.jm.voicesummary.data.stt

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
import kr.jm.voicesummary.domain.model.SttModel
import kr.jm.voicesummary.domain.repository.SttDownloadState
import kr.jm.voicesummary.domain.repository.SttRepository
import kr.jm.voicesummary.domain.repository.SttState
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SttRepositoryImpl(private val context: Context) : SttRepository {

    companion object {
        private const val TAG = "SttRepository"
        private const val MODELS_ROOT_DIR = "sherpa-models"
        private const val BUFFER_SIZE = 8192
        private const val PREFS_NAME = "stt_prefs"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val SAMPLE_RATE = 16000
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var recognizer: OfflineRecognizer? = null
    private var loadedModel: SttModel? = null

    private val _downloadState = MutableStateFlow<SttDownloadState>(SttDownloadState.Idle)
    override val downloadState: StateFlow<SttDownloadState> = _downloadState

    private val _selectedModel = MutableStateFlow(getSelectedModel())
    override val selectedModel: StateFlow<SttModel> = _selectedModel

    private val _transcriptionState = MutableStateFlow(SttState.NOT_INITIALIZED)
    override val transcriptionState: StateFlow<SttState> = _transcriptionState

    override fun getSelectedModel(): SttModel {
        val modelName = prefs.getString(KEY_SELECTED_MODEL, SttModel.WHISPER_BASE.name)
        return try {
            SttModel.valueOf(modelName!!)
        } catch (e: Exception) {
            SttModel.WHISPER_BASE
        }
    }

    override fun setSelectedModel(model: SttModel) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model.name).apply()
        _selectedModel.value = model
        _downloadState.value = SttDownloadState.Idle
    }

    override fun isModelDownloaded(model: SttModel): Boolean {
        val modelDir = getModelDir(model)
        return File(modelDir, model.encoderFile).exists() &&
                File(modelDir, model.decoderFile).exists() &&
                File(modelDir, model.tokensFile).exists()
    }

    override fun isCurrentModelDownloaded(): Boolean = isModelDownloaded(getSelectedModel())

    override fun getDownloadedModels(): List<SttModel> {
        return SttModel.entries.filter { isModelDownloaded(it) }
    }

    private fun getModelDir(model: SttModel): File {
        val modelDir = File(context.filesDir, "$MODELS_ROOT_DIR/${model.name}")
        if (!modelDir.exists()) modelDir.mkdirs()
        return modelDir
    }

    override suspend fun downloadModel(model: SttModel): Boolean = withContext(Dispatchers.IO) {
        if (isModelDownloaded(model)) {
            setSelectedModel(model)
            _downloadState.value = SttDownloadState.Completed
            return@withContext true
        }

        try {
            _downloadState.value = SttDownloadState.Downloading(0)
            val tempFile = File(context.cacheDir, "whisper-model.tar.bz2")

            val url = URL(model.downloadUrl)
            var connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.instanceFollowRedirects = true

            if (connection.responseCode in listOf(301, 302, 303, 307, 308)) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                connection = URL(newUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
            }

            val totalSize = connection.contentLength.toLong()
            var downloadedSize = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        val progress = if (totalSize > 0) (downloadedSize * 100 / totalSize).toInt() else -1
                        _downloadState.value = SttDownloadState.Downloading(progress)
                    }
                }
            }
            connection.disconnect()

            _downloadState.value = SttDownloadState.Extracting
            extractTarBz2(tempFile, context.filesDir)
            tempFile.delete()
            copyModelFiles(model)

            setSelectedModel(model)
            _downloadState.value = SttDownloadState.Completed
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _downloadState.value = SttDownloadState.Error(e.message ?: "Unknown error")
            false
        }
    }

    private fun extractTarBz2(tarBz2File: File, destDir: File) {
        BZip2CompressorInputStream(BufferedInputStream(tarBz2File.inputStream())).use { bzIn ->
            TarArchiveInputStream(bzIn).use { tarIn ->
                var entry = tarIn.nextEntry
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
                    entry = tarIn.nextEntry
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
            if (srcFile.exists()) srcFile.copyTo(destFile, overwrite = true)
        }
        extractedDir.deleteRecursively()
    }

    override suspend fun initializeTranscriber(): Boolean = withContext(Dispatchers.IO) {
        val selected = getSelectedModel()

        if (recognizer != null && loadedModel == selected) return@withContext true

        recognizer?.release()
        recognizer = null

        _transcriptionState.value = SttState.LOADING

        if (!isModelDownloaded(selected)) {
            _transcriptionState.value = SttState.MODEL_NOT_FOUND
            return@withContext false
        }

        try {
            val modelDir = getModelDir(selected)

            val whisperConfig = OfflineWhisperModelConfig(
                encoder = File(modelDir, selected.encoderFile).absolutePath,
                decoder = File(modelDir, selected.decoderFile).absolutePath,
                language = "ko",
                task = "transcribe"
            )

            val modelConfig = OfflineModelConfig(
                whisper = whisperConfig,
                tokens = File(modelDir, selected.tokensFile).absolutePath,
                numThreads = 4,
                provider = "nnapi"
            )

            val config = OfflineRecognizerConfig(
                featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = modelConfig
            )

            recognizer = OfflineRecognizer(null, config)
            loadedModel = selected
            _transcriptionState.value = SttState.READY
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
            _transcriptionState.value = SttState.ERROR
            false
        }
    }

    override suspend fun transcribe(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        val rec = recognizer ?: return@withContext Result.failure(IllegalStateException("Not initialized"))

        _transcriptionState.value = SttState.TRANSCRIBING

        try {
            val audioData = loadWavFile(audioFile)
            val stream = rec.createStream()
            stream.acceptWaveform(audioData, SAMPLE_RATE)
            rec.decode(stream)
            val result = rec.getResult(stream).text
            stream.release()

            _transcriptionState.value = SttState.READY
            Result.success(result.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            _transcriptionState.value = SttState.ERROR
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

            val shortBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val floatArray = FloatArray(numSamples)
            for (i in 0 until numSamples) {
                floatArray[i] = shortBuffer.get(i) / 32768.0f
            }
            return floatArray
        }
    }

    override fun releaseTranscriber() {
        recognizer?.release()
        recognizer = null
        loadedModel = null
        _transcriptionState.value = SttState.NOT_INITIALIZED
    }
}
