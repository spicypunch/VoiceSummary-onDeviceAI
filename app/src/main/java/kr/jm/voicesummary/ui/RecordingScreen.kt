package kr.jm.voicesummary.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.jm.voicesummary.audio.RecordingState

@Composable
fun RecordingScreen(
    recordingState: RecordingState,
    recordingDuration: Long,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 녹음 시간 표시
        Text(
            text = formatDuration(recordingDuration),
            fontSize = 48.sp,
            color = if (recordingState == RecordingState.RECORDING) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 녹음 버튼
        Button(
            onClick = {
                if (recordingState == RecordingState.RECORDING) {
                    onStopRecording()
                } else {
                    onStartRecording()
                }
            },
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (recordingState == RecordingState.RECORDING) {
                    Color.Red
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        ) {
            Text(
                text = if (recordingState == RecordingState.RECORDING) "중지" else "녹음",
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 상태 텍스트
        Text(
            text = when (recordingState) {
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
