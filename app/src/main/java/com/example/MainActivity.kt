package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.BatchUploadScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.TranscriptDetailScreen
import com.example.ui.theme.AudioScribeTheme
import com.example.viewmodel.AppTab
import com.example.viewmodel.AudioScribeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioScribeTheme {
                AudioScribeApp()
            }
        }
    }
}

@Composable
fun AudioScribeApp(viewModel: AudioScribeViewModel = viewModel()) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val allTranscripts by viewModel.allTranscripts.collectAsStateWithLifecycle()
    val searchFilterState by viewModel.searchFilterState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val transcriptionProgress by viewModel.transcriptionProgress.collectAsStateWithLifecycle()
    val selectedTranscriptId by viewModel.selectedTranscriptId.collectAsStateWithLifecycle()
    val jumpToTimestampMs by viewModel.jumpToTimestampMs.collectAsStateWithLifecycle()

    val selectedTranscriptItem = allTranscripts.find { it.transcript.id == selectedTranscriptId }

    // Intercept back button when inside detail or search to return to dashboard
    BackHandler(enabled = currentTab != AppTab.DASHBOARD) {
        viewModel.setTab(AppTab.DASHBOARD)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (currentTab != AppTab.DETAIL) {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = currentTab == AppTab.DASHBOARD,
                        onClick = { viewModel.setTab(AppTab.DASHBOARD) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == AppTab.DASHBOARD) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                                contentDescription = "Transcripts"
                            )
                        },
                        label = { Text("Transcripts", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_dashboard")
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.SEARCH,
                        onClick = { viewModel.setTab(AppTab.SEARCH) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (searchFilterState.isActiveFilter) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (currentTab == AppTab.SEARCH) Icons.Filled.Search else Icons.Outlined.Search,
                                    contentDescription = "Search"
                                )
                            }
                        },
                        label = { Text("Search All", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_search")
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.BATCH_UPLOAD,
                        onClick = { viewModel.setTab(AppTab.BATCH_UPLOAD) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (transcriptionProgress.isRunning) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("${transcriptionProgress.completedCount}/${transcriptionProgress.totalCount}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (currentTab == AppTab.BATCH_UPLOAD) Icons.Filled.QueueMusic else Icons.Outlined.QueueMusic,
                                    contentDescription = "Bulk Analyze"
                                )
                            }
                        },
                        label = { Text("Bulk Queue", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_bulk")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                AppTab.DASHBOARD -> {
                    DashboardScreen(
                        viewModel = viewModel,
                        transcripts = allTranscripts,
                        progress = transcriptionProgress,
                        onNavigateTab = { viewModel.setTab(it) },
                        onOpenTranscript = { transcriptId ->
                            viewModel.openTranscriptDetail(transcriptId)
                        }
                    )
                }

                AppTab.SEARCH -> {
                    SearchScreen(
                        viewModel = viewModel,
                        filterState = searchFilterState,
                        searchResults = searchResults,
                        onJumpToTimestamp = { transcriptId, timestampMs ->
                            viewModel.openTranscriptDetail(transcriptId, timestampMs)
                        }
                    )
                }

                AppTab.BATCH_UPLOAD -> {
                    BatchUploadScreen(
                        viewModel = viewModel,
                        progress = transcriptionProgress,
                        onNavigateTab = { viewModel.setTab(it) }
                    )
                }

                AppTab.DETAIL -> {
                    TranscriptDetailScreen(
                        viewModel = viewModel,
                        transcriptItem = selectedTranscriptItem,
                        targetSeekTimestampMs = jumpToTimestampMs,
                        onBack = { viewModel.setTab(AppTab.DASHBOARD) }
                    )
                }
            }
        }
    }
}
