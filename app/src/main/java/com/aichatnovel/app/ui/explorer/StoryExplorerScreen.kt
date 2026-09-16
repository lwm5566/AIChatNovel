package com.aichatnovel.app.ui.explorer

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
import androidx.compose.material3.HorizontalDivider
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
import com.aichatnovel.app.domain.model.SourceSpan
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.ui.components.EmptyState
import com.aichatnovel.app.ui.components.LoadingState
import com.aichatnovel.app.ui.components.presentationModeLabel
import com.aichatnovel.app.viewmodel.ExplorerBeat
import com.aichatnovel.app.viewmodel.ExplorerChapter
import com.aichatnovel.app.viewmodel.ExplorerEvent
import com.aichatnovel.app.viewmodel.ExplorerScene
import com.aichatnovel.app.viewmodel.StoryExplorerUiState
import com.aichatnovel.app.viewmodel.StoryExplorerViewModel

@Composable
fun StoryExplorerRoute(
    onBack: () -> Unit,
    viewModel: StoryExplorerViewModel = viewModel(factory = StoryExplorerViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StoryExplorerScreen(uiState = uiState, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryExplorerScreen(
    uiState: StoryExplorerUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.explorer_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        val story = uiState.story

        when {
            uiState.isLoading -> LoadingState(contentModifier)

            story == null -> EmptyState(
                message = "暂无解析结果，请先在小说页导入原文",
                modifier = contentModifier,
            )

            else -> LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "story") { StoryHeader(story) }

                uiState.chapters.forEach { chapter ->
                    item(key = "chapter-${chapter.id}") { ChapterHeader(chapter) }

                    if (chapter.scenes.isEmpty()) {
                        item(key = "chapter-empty-${chapter.id}") { EmptyNote("该章节暂无场景") }
                    }

                    chapter.scenes.forEach { scene ->
                        item(key = "scene-${scene.id}") { SceneCard(scene) }

                        scene.beats.forEach { beat ->
                            item(key = "beat-${beat.id}") { BeatHeader(beat) }
                            items(items = beat.events, key = { "event-${it.id}" }) { event ->
                                EventCard(event = event, sceneMode = scene.presentationMode)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryHeader(story: Story) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = story.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "作者：${story.author}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = story.synopsis, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ChapterHeader(chapter: ExplorerChapter) {
    Text(
        text = "第 ${chapter.index} 章　${chapter.title}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun EmptyNote(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SceneCard(scene: ExplorerScene) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "场景 ${scene.index}｜${scene.title}",
                style = MaterialTheme.typography.titleMedium,
            )

            PresentationModeBadge(scene.presentationMode)

            scene.evidence?.let { evidence ->
                Text(
                    text = "判定依据：$evidence",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            scene.settingLines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "参与角色：${scene.participants.joinToString("、").ifBlank { "（无）" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            scene.totalDurationLabel?.let { duration ->
                Text(
                    text = "总时长：$duration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "节拍数：${scene.beats.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PresentationModeBadge(mode: PresentationMode) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "呈现方式",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Text(
                text = presentationModeLabel(mode),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun BeatHeader(beat: ExplorerBeat) {
    Text(
        text = "节拍 ${beat.order}",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun EventCard(
    event: ExplorerEvent,
    sceneMode: PresentationMode,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = event.kind,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = event.timingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(text = event.summary, style = MaterialTheme.typography.bodyLarge)

            event.details.forEach { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            event.presentationOverride?.takeIf { it != sceneMode }?.let { override ->
                Text(
                    text = "呈现方式覆盖：${presentationModeLabel(override)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            event.sourceSpan?.let { span ->
                Text(
                    text = span.toLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun SourceSpan.toLabel(): String =
    "原文：\u300C$snippet\u300D（$startOffset–$endOffset）"
