package kr.jm.voicesummary

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.jm.voicesummary.audio.AudioRecorder
import kr.jm.voicesummary.ui.RecordingScreen
import kr.jm.voicesummary.ui.theme.VoiceSummaryTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var audioRecorder: AudioRecorder
    private var currentRecordingFile: File? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecordingInternal()
        } else {
            Toast.makeText(this, "녹음 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioRecorder = AudioRecorder(this)
        enableEdgeToEdge()

        setContent {
            VoiceSummaryTheme {
                val recordingState by audioRecorder.recordingState.collectAsState()
                val recordingDuration by audioRecorder.recordingDuration.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RecordingScreen(
                        recordingState = recordingState,
                        recordingDuration = recordingDuration,
                        onStartRecording = { startRecording() },
                        onStopRecording = { stopRecording() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun startRecording() {
        if (audioRecorder.hasPermission()) {
            startRecordingInternal()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecordingInternal() {
        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date()) + ".wav"
        val recordingDir = getExternalFilesDir(null) ?: filesDir
        currentRecordingFile = File(recordingDir, fileName)

        lifecycleScope.launch {
            audioRecorder.startRecording(currentRecordingFile!!)
                .onFailure { e ->
                    Toast.makeText(
                        this@MainActivity,
                        "녹음 시작 실패: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun stopRecording() {
        audioRecorder.stopRecording()
        currentRecordingFile?.let { file ->
            if (file.exists()) {
                Toast.makeText(
                    this,
                    "녹음 저장됨: ${file.name} (${file.length() / 1024}KB)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder.release()
    }
}
