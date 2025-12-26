package kr.jm.voicesummary

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import kr.jm.voicesummary.presentation.list.RecordingListScreen
import kr.jm.voicesummary.presentation.list.RecordingListViewModel
import kr.jm.voicesummary.presentation.recording.RecordingScreen
import kr.jm.voicesummary.presentation.recording.RecordingViewModel
import kr.jm.voicesummary.presentation.settings.SettingsScreen
import kr.jm.voicesummary.ui.theme.VoiceSummaryTheme

class MainActivity : ComponentActivity() {

    private lateinit var recordingViewModel: RecordingViewModel
    private lateinit var listViewModel: RecordingListViewModel

    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            recordingViewModel.onRecordClick()
        } else {
            Toast.makeText(this, "녹음 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val container = (application as VoiceSummaryApp).container

        recordingViewModel = ViewModelProvider(
            this,
            RecordingViewModel.Factory(container.audioRepository, this)
        )[RecordingViewModel::class.java]

        listViewModel = ViewModelProvider(
            this,
            RecordingListViewModel.Factory(
                container.recordingRepository,
                container.audioRepository,
                container.sttRepository,
                container.billingRepository,
                this
            )
        )[RecordingListViewModel::class.java]

        setContent {
            VoiceSummaryTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                var showSettings by remember { mutableStateOf(false) }
                val recordingUiState by recordingViewModel.uiState.collectAsState()
                val listUiState by listViewModel.uiState.collectAsState()

                if (showSettings) {
                    SettingsScreen(
                        onBackClick = { showSettings = false }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Mic, contentDescription = "녹음") },
                                    label = { Text("녹음") },
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "목록") },
                                    label = { Text("목록") },
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 }
                                )
                            }
                        }
                    ) { innerPadding ->
                        when (selectedTab) {
                            0 -> RecordingScreen(
                                uiState = recordingUiState,
                                hasPermission = recordingViewModel.hasPermission(),
                                onRecordClick = { recordingViewModel.onRecordClick() },
                                onRequestPermission = { recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                modifier = Modifier.padding(innerPadding)
                            )
                            1 -> RecordingListScreen(
                                uiState = listUiState,
                                onPlayClick = { listViewModel.onPlayClick(it) },
                                onLongClick = { listViewModel.onLongClick(it) },
                                onSeek = { listViewModel.onSeek(it) },
                                onTranscribeClick = { listViewModel.onTranscribeClick(it) },
                                onExpandToggle = { listViewModel.onExpandToggle(it) },
                                onDeleteConfirm = { listViewModel.onDeleteConfirm() },
                                onDeleteCancel = { listViewModel.onDeleteCancel() },
                                onDownloadSttModel = { listViewModel.onDownloadSttModel(it) },
                                onShowSttModelSelector = { listViewModel.onShowSttModelSelector() },
                                onDismissSttModelSelector = { listViewModel.onDismissSttModelSelector() },
                                onSelectSttModel = { listViewModel.onSelectSttModel(it) },
                                onSettingsClick = { showSettings = true },
                                onDismissPremiumDialog = { listViewModel.onDismissPremiumDialog() },
                                onPurchasePremium = { listViewModel.onPurchasePremium { this@MainActivity } },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}
