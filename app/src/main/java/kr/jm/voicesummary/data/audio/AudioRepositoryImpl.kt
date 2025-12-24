package kr.jm.voicesummary.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kr.jm.voicesummary.domain.repository.AudioRepository
import kr.jm.voicesummary.domain.repository.PlaybackState
import kr.jm.voicesummary.domain.repository.RecordingState
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class AudioRepositoryImpl(private val context: Context) : AudioRepository {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false

    private var mediaPlayer: MediaPlayer? = null

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    override val recordingState: StateFlow<RecordingState> = _recordingState

    private val _recordingDuration = MutableStateFlow(0L)
    override val recordingDuration: StateFlow<Long> = _recordingDuration

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _currentPosition = MutableStateFlow(0)
    override val currentPosition: StateFlow<Int> = _currentPosition

    private val _duration = MutableStateFlow(0)
    override val duration: StateFlow<Int> = _duration

    private val _currentPlayingFile = MutableStateFlow<File?>(null)
    override val currentPlayingFile: StateFlow<File?> = _currentPlayingFile

    override fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun startRecording(outputFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasRecordPermission()) {
            return@withContext Result.failure(SecurityException("녹음 권한이 없습니다"))
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext Result.failure(IllegalStateException("AudioRecord 초기화 실패"))
            }

            isRecording = true
            _recordingState.value = RecordingState.RECORDING
            audioRecord?.startRecording()

            recordingThread = Thread { writeWavFile(outputFile, bufferSize) }.apply { start() }

            Result.success(Unit)
        } catch (e: Exception) {
            _recordingState.value = RecordingState.ERROR
            Result.failure(e)
        }
    }

    private fun writeWavFile(outputFile: File, bufferSize: Int) {
        val buffer = ShortArray(bufferSize)
        val outputStream = FileOutputStream(outputFile)
        val startTime = System.currentTimeMillis()

        try {
            outputStream.write(ByteArray(44))
            var totalBytes = 0L

            while (isRecording) {
                val readCount = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (readCount > 0) {
                    val byteBuffer = ByteArray(readCount * 2)
                    for (i in 0 until readCount) {
                        byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                    }
                    outputStream.write(byteBuffer)
                    totalBytes += byteBuffer.size
                    _recordingDuration.value = System.currentTimeMillis() - startTime
                }
            }

            outputStream.close()
            writeWavHeader(outputFile, totalBytes)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            outputStream.close()
        }
    }

    private fun writeWavHeader(file: File, totalAudioLen: Long) {
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = SAMPLE_RATE * channels * 2

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (SAMPLE_RATE and 0xff).toByte()
        header[25] = (SAMPLE_RATE shr 8 and 0xff).toByte()
        header[26] = (SAMPLE_RATE shr 16 and 0xff).toByte()
        header[27] = (SAMPLE_RATE shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 2).toByte(); header[33] = 0
        header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }

    override fun stopRecording() {
        isRecording = false
        _recordingState.value = RecordingState.IDLE
        _recordingDuration.value = 0L

        recordingThread?.join(1000)
        recordingThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun play(file: File) {
        if (_currentPlayingFile.value == file && mediaPlayer != null) {
            if (_playbackState.value == PlaybackState.PLAYING) {
                pause()
            } else {
                resume()
            }
            return
        }

        stopPlayback()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    _playbackState.value = PlaybackState.IDLE
                    _currentPosition.value = 0
                }
            }

            _currentPlayingFile.value = file
            _duration.value = mediaPlayer?.duration ?: 0
            _playbackState.value = PlaybackState.PLAYING
            startPositionUpdater()
        } catch (e: Exception) {
            e.printStackTrace()
            _playbackState.value = PlaybackState.ERROR
        }
    }

    override fun pause() {
        mediaPlayer?.pause()
        _playbackState.value = PlaybackState.PAUSED
    }

    private fun resume() {
        mediaPlayer?.start()
        _playbackState.value = PlaybackState.PLAYING
    }

    override fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _currentPosition.value = position
    }

    override fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _playbackState.value = PlaybackState.IDLE
        _currentPosition.value = 0
        _currentPlayingFile.value = null
    }

    private fun startPositionUpdater() {
        Thread {
            while (mediaPlayer != null && _playbackState.value == PlaybackState.PLAYING) {
                try {
                    _currentPosition.value = mediaPlayer?.currentPosition ?: 0
                    Thread.sleep(100)
                } catch (e: Exception) {
                    break
                }
            }
        }.start()
    }

    override fun release() {
        stopRecording()
        stopPlayback()
    }
}
