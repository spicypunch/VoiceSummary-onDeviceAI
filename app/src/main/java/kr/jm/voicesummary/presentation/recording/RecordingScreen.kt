package kr.jm.voicesummary.presentation.recording

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.jm.voicesummary.core.audio.RecordingState

@Composable
fun RecordingScreen(
    uiState: RecordingUiState,
    hasPermission: Boolean,
    onRecordClick: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = formatDuration(uiState.recordingDuration),
            fontSize = 48.sp,
            color = if (uiState.recordingState == RecordingState.RECORDING) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                if (uiState.recordingState == RecordingState.RECORDING) {
                    onRecordClick()
                } else {
                    if (hasPermission) {
                        onRecordClick()
                    } else {
                        onRequestPermission()
                    }
                }
            },
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.recordingState == RecordingState.RECORDING) {
                    Color.Red
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        ) {
            Text(
                text = if (uiState.recordingState == RecordingState.RECORDING) "중지" else "녹음",
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = when (uiState.recordingState) {
                RecordingState.IDLE -> "버튼을 눌러 녹음을 시작하세요"
                RecordingState.RECORDING -> "녹음 중..."
                RecordingState.ERROR -> "오류가 발생했습니다"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000 / 60) % 60
    val hours = millis / 1000 / 60 / 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}
