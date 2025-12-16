package kr.jm.voicesummary.core.audio

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class AudioPlayer {

    private var mediaPlayer: MediaPlayer? = null

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration

    private val _currentFile = MutableStateFlow<File?>(null)
    val currentFile: StateFlow<File?> = _currentFile

    fun play(file: File) {
        // 같은 파일 재생 중이면 일시정지/재개
        if (_currentFile.value == file && mediaPlayer != null) {
            if (_playbackState.value == PlaybackState.PLAYING) {
                pause()
            } else {
                resume()
            }
            return
        }

        // 새 파일 재생
        stop()

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

            _currentFile.value = file
            _duration.value = mediaPlayer?.duration ?: 0
            _playbackState.value = PlaybackState.PLAYING

            startPositionUpdater()
        } catch (e: Exception) {
            e.printStackTrace()
            _playbackState.value = PlaybackState.ERROR
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        _playbackState.value = PlaybackState.PAUSED
    }

    fun resume() {
        mediaPlayer?.start()
        _playbackState.value = PlaybackState.PLAYING
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _playbackState.value = PlaybackState.IDLE
        _currentPosition.value = 0
        _currentFile.value = null
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _currentPosition.value = position
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

    fun release() {
        stop()
    }
}

enum class PlaybackState {
    IDLE, PLAYING, PAUSED, ERROR
}
