package com.aichatnovel.app.ui.storyplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichatnovel.app.R
import com.aichatnovel.app.domain.model.ExecutableTimeline
import com.aichatnovel.app.domain.model.PlaybackState
import com.aichatnovel.app.domain.model.PlaybackStatus
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.domain.model.TtsProviderId
import com.aichatnovel.app.ui.components.EmptyState
import com.aichatnovel.app.ui.components.LoadingState
import com.aichatnovel.app.ui.components.SectionHeader
import com.aichatnovel.app.ui.components.formatDurationWithSource
import com.aichatnovel.app.ui.components.formatPlaybackTime
import com.aichatnovel.app.ui.components.presentationModeLabel
import com.aichatnovel.app.ui.components.timelineDurationSource
import com.aichatnovel.app.viewmodel.AudioGenerationStatus
import com.aichatnovel.app.viewmodel.AudioState
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
    val audioState by viewModel.audioState.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    StoryPlayScreen(
        uiState = uiState,
        playbackState = playbackState,
        audioState = audioState,
        timeline = timeline,
        onPlay = viewModel::play,
        onPause = viewModel::pause,
        onReset = viewModel::reset,
        onSeek = viewModel::seekTo,
        onSelectProvider = viewModel::selectProvider,
        onGenerateAudio = viewModel::generateSceneAudio,
        onBack = onBack,
    )
}

/**
 * 剧情演出页。
 *
 * 视觉顺序：场景概览 → 播放器（本页的视觉中心）→ 语音 → 节拍。
 * UI 只渲染 [StoryPlayUiState] / [PlaybackState] / [AudioState] / [ExecutableTimeline]，
 * 不计算时长与位置，也不推断当前事件——当前事件由 [PlaybackState] 给出，这里只按 id 查找对应行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryPlayScreen(
    uiState: StoryPlayUiState,
    playbackState: PlaybackState,
    audioState: AudioState,
    timeline: ExecutableTimeline,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSeek: (Long) -> Unit,
    onSelectProvider: (TtsProviderId) -> Unit,
    onGenerateAudio: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.sceneTitle ?: stringResource(R.string.story_play_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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

            uiState.beats.isEmpty() -> EmptyState(
                title = stringResource(R.string.story_play_empty_title),
                description = stringResource(R.string.story_play_empty_desc),
                modifier = contentModifier,
            )

            else -> LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "overview") {
                    SceneOverview(uiState = uiState, hasAudio = audioState.hasAudio)
                }

                item(key = "player") {
                    PlayerCard(
                        uiState = uiState,
                        playbackState = playbackState,
                        timeline = timeline,
                        onPlay = onPlay,
                        onPause = onPause,
                        onReset = onReset,
                        onSeek = onSeek,
                    )
                }

                item(key = "voice") {
                    VoiceSection(
                        audioState = audioState,
                        onSelectProvider = onSelectProvider,
                        onGenerateAudio = onGenerateAudio,
                    )
                }

                item(key = "script-header") {
                    SectionHeader(
                        title = stringResource(R.string.story_play_section_script),
                        trailing = "${uiState.beats.size}",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                uiState.beats.forEach { beat ->
                    item(key = "beat-${beat.order}-${beat.id}") {
                        BeatHeader(beat = beat, isCurrent = beat.id == playbackState.currentBeatId)
                    }
                    items(items = beat.lines, key = { "event-${beat.order}-${it.id}" }) { line ->
                        EventLine(line = line, isCurrent = line.id == playbackState.currentEventId)
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneOverview(
    uiState: StoryPlayUiState,
    hasAudio: Boolean,
) {
    Text(
        text = buildString {
            append(presentationModeLabel(uiState.presentationMode))
            append(" · ")
            append(stringResource(R.string.scene_meta_characters, uiState.characterCount))
            append(" · ")
            append(stringResource(R.string.scene_meta_beats, uiState.beats.size))
            append(" · ")
            append(
                stringResource(
                    if (hasAudio) R.string.story_play_voice_ready else R.string.story_play_voice_absent,
                ),
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlayerCard(
    uiState: StoryPlayUiState,
    playbackState: PlaybackState,
    timeline: ExecutableTimeline,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val isPlaying = playbackState.status == PlaybackStatus.Playing
    val duration = playbackState.durationMillis
    val currentLine = findLine(uiState.beats, playbackState.currentEventId)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = playbackStatusLabel(playbackState.status),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            NowPlaying(line = currentLine)

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
                    text = formatPlaybackTime(playbackState.positionMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDurationWithSource(duration, timelineDurationSource(timeline.positions)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onReset,
                    enabled = playbackState.hasPlayableContent,
                ) {
                    Text(stringResource(R.string.story_play_action_reset))
                }
                Button(
                    onClick = if (isPlaying) onPause else onPlay,
                    enabled = playbackState.hasPlayableContent,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(
                        text = if (isPlaying) {
                            stringResource(R.string.story_play_action_pause)
                        } else {
                            stringResource(R.string.story_play_action_play)
                        },
                    )
                }
            }
        }
    }
}

/** 播放器里的「正在演出」区域：当前角色与对白。没有演出中的事件时不伪造内容。 */
@Composable
private fun NowPlaying(line: PerformanceLine?) {
    if (line == null) {
        Text(
            text = stringResource(R.string.story_play_idle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        line.speaker?.let { speaker ->
            Text(
                text = speaker,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VoiceSection(
    audioState: AudioState,
    onSelectProvider: (TtsProviderId) -> Unit,
    onGenerateAudio: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(title = stringResource(R.string.story_play_section_voice))

            Text(
                text = stringResource(
                    if (audioState.hasAudio) R.string.story_play_voice_ready else R.string.story_play_voice_absent,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = stringResource(R.string.story_play_voice_service),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TtsProviderId.entries.forEach { provider ->
                    FilterChip(
                        selected = provider == audioState.providerId,
                        onClick = { onSelectProvider(provider) },
                        label = { Text(provider.displayName) },
                    )
                }
            }

            if (!audioState.providerConfigured) {
                Text(
                    text = stringResource(R.string.story_play_voice_unconfigured),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = onGenerateAudio,
                enabled = audioState.status != AudioGenerationStatus.Generating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = if (audioState.status == AudioGenerationStatus.Generating) {
                        stringResource(R.string.story_play_action_generating)
                    } else {
                        stringResource(R.string.story_play_action_generate)
                    },
                )
            }

            audioState.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (audioState.status == AudioGenerationStatus.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun BeatHeader(beat: BeatUi, isCurrent: Boolean) {
    Text(
        text = beat.label,
        style = MaterialTheme.typography.labelLarge,
        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * 一行演出内容。
 *
 * 视觉层级按「谁在说话、说了什么」排布：
 * [PerformanceLine] 的对白最突出，旁白次之，动作 / 环境 / 环境音 / 镜头进一步弱化。
 */
@Composable
private fun EventLine(line: PerformanceLine, isCurrent: Boolean) {
    val isDialogue = line.kind == "对白"
    val isAmbient = line.kind == "环境" || line.kind == "环境音" || line.kind == "镜头"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .padding(vertical = 8.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(if (isDialogue) 40.dp else 24.dp)
                .background(
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (isDialogue && line.speaker != null) {
                Text(
                    text = line.speaker,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (isDialogue) {
                Text(text = line.text, style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    text = "${line.kind}　${line.text}",
                    style = if (isAmbient) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (line.presentationMode != PresentationMode.LiveScene) {
                Text(
                    text = presentationModeLabel(line.presentationMode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/** 按 [PlaybackState.currentEventId] 查找对应的演出行；找不到就返回 null，不推断。 */
private fun findLine(beats: List<BeatUi>, eventId: String?): PerformanceLine? {
    if (eventId == null) return null
    return beats.asSequence()
        .flatMap { it.lines.asSequence() }
        .firstOrNull { it.id == eventId }
}

private fun playbackStatusLabel(status: PlaybackStatus): String = when (status) {
    PlaybackStatus.Idle -> "未播放"
    PlaybackStatus.Playing -> "播放中"
    PlaybackStatus.Paused -> "已暂停"
    PlaybackStatus.Completed -> "已结束"
}
