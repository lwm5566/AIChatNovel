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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import com.aichatnovel.app.domain.model.PlaybackState
import com.aichatnovel.app.domain.model.PlaybackStatus
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.domain.model.Timing
import com.aichatnovel.app.ui.components.EmptyState
import com.aichatnovel.app.ui.components.LoadingState
import com.aichatnovel.app.ui.components.presentationModeLabel
import com.aichatnovel.app.viewmodel.BeatUi
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
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    StoryPlayScreen(
        uiState = uiState,
        playbackState = playbackState,
        onPlay = viewModel::play,
        onPause = viewModel::pause,
        onReset = viewModel::reset,
        onSeek = viewModel::seekTo,
        onBack = onBack,
    )
}

/**
 * 剧情演出页。UI 只渲染 [StoryPlayUiState] 与 [PlaybackState]，
 * 并把播放意图交给上层——不自己计算时长、位置或当前事件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryPlayScreen(
    uiState: StoryPlayUiState,
    playbackState: PlaybackState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSeek: (Long) -> Unit,
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

                item(key = "playback-controls") {
                    PlaybackControls(
                        playbackState = playbackState,
                        onPlay = onPlay,
                        onPause = onPause,
                        onReset = onReset,
                        onSeek = onSeek,
                    )
                }

                uiState.beats.forEach { beat ->
                    item(key = beat.id) {
                        BeatHeader(beat = beat, isCurrent = beat.id == playbackState.currentBeatId)
                    }
                    items(items = beat.lines, key = { it.id }) { line ->
                        PerformanceLineCard(
                            line = line,
                            sceneMode = uiState.presentationMode,
                            isCurrent = line.id == playbackState.currentEventId,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    playbackState: PlaybackState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val isPlaying = playbackState.status == PlaybackStatus.Playing
    val duration = playbackState.durationMillis

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = playbackStatusLabel(playbackState.status),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReset, enabled = playbackState.hasPlayableContent) {
                    Text("重置")
                }
                Button(
                    onClick = if (isPlaying) onPause else onPlay,
                    enabled = playbackState.hasPlayableContent,
                ) {
                    Text(if (isPlaying) "暂停" else "播放")
                }
            }

            Slider(
                value = playbackState.progress,
                onValueChange = { fraction -> onSeek((fraction * duration).toLong()) },
                enabled = playbackState.hasPlayableContent,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatMillis(playbackState.positionMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatMillis(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BeatHeader(beat: BeatUi, isCurrent: Boolean) {
    Text(
        text = beat.label,
        style = MaterialTheme.typography.titleSmall,
        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
    isCurrent: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isCurrent) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
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

private fun playbackStatusLabel(status: PlaybackStatus): String = when (status) {
    PlaybackStatus.Idle -> "未播放"
    PlaybackStatus.Playing -> "播放中"
    PlaybackStatus.Paused -> "已暂停"
    PlaybackStatus.Completed -> "已结束"
}

private fun formatMillis(millis: Long): String = "${millis / 1000.0}s"

private fun timingLabel(timing: Timing): String {
    val start = "${timing.startOffsetMillis / 1000.0}s"
    val end = timing.durationMillis?.let { "+${it / 1000.0}s" } ?: "+时长待定"
    return "$start $end"
}
