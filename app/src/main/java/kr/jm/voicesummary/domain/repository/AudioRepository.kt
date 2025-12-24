package kr.jm.voicesummary.domain.repository

import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface AudioRepository {
    val recordingState: StateFlow<RecordingState>
    val recordingDuration: StateFlow<Long>
    val playbackState: StateFlow<PlaybackState>
    val currentPosition: StateFlow<Int>
    val duration: StateFlow<Int>
    val currentPlayingFile: StateFlow<File?>

    fun hasRecordPermission(): Boolean
    suspend fun startRecording(outputFile: File): Result<Unit>
    fun stopRecording()
    fun play(file: File)
    fun pause()
    fun seekTo(position: Int)
    fun stopPlayback()
    fun release()
}

enum class RecordingState {
    IDLE, RECORDING, ERROR
}

enum class PlaybackState {
    IDLE, PLAYING, PAUSED, ERROR
}
