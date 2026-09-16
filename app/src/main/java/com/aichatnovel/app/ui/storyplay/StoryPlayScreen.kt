package com.aichatnovel.app.ui.storyplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichatnovel.app.R
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.domain.model.Timing
import com.aichatnovel.app.ui.components.EmptyState
import com.aichatnovel.app.ui.components.LoadingState
import com.aichatnovel.app.ui.components.presentationModeLabel
import com.aichatnovel.app.viewmodel.PerformanceLine
import com.aichatnovel.app.viewmodel.StoryPlayUiState
import com.aichatnovel.app.viewmodel.StoryPlayViewModel

@Composable
fun StoryPlayRoute(
    sceneId: String?,
    onBack: () -> Unit,
    viewModel: StoryPlayViewModel = viewModel(factory = StoryPlayViewModel.factory(sceneId)),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StoryPlayScreen(uiState = uiState, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryPlayScreen(
    uiState: StoryPlayUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.story_play_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)

        when {
            uiState.isLoading -> LoadingState(contentModifier)
            uiState.beats.isEmpty() -> EmptyState(message = "暂无演出节拍", modifier = contentModifier)

            else -> LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "presentation-mode") {
                    PresentationModeHeader(uiState.presentationMode)
                }

                uiState.beats.forEach { beat ->
                    item(key = beat.id) {
                        Text(
                            text = beat.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(items = beat.lines, key = { it.id }) { line ->
                        PerformanceLineCard(line = line, sceneMode = uiState.presentationMode)
                    }
                }
            }
        }
    }
}

@Composable
private fun PresentationModeHeader(mode: PresentationMode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "呈现方式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = presentationModeLabel(mode),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun PerformanceLineCard(
    line: PerformanceLine,
    sceneMode: PresentationMode,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = line.kind,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                line.speaker?.let { speaker ->
                    Text(text = speaker, style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    text = timingLabel(line.timing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = line.text, style = MaterialTheme.typography.bodyLarge)
            line.voiceLabel?.let { voice ->
                Text(
                    text = "音色：$voice",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (line.presentationMode != sceneMode) {
                Text(
                    text = "呈现方式覆盖：${presentationModeLabel(line.presentationMode)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

private fun timingLabel(timing: Timing): String {
    val start = "${timing.startOffsetMillis / 1000.0}s"
    val end = timing.durationMillis?.let { "+${it / 1000.0}s" } ?: "+时长待定"
    return "$start $end"
}
