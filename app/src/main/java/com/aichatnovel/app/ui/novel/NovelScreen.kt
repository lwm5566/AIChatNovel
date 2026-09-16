package com.aichatnovel.app.ui.novel

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichatnovel.app.R
import com.aichatnovel.app.ui.components.EmptyState
import com.aichatnovel.app.ui.components.LoadingState
import com.aichatnovel.app.ui.components.SectionHeader
import com.aichatnovel.app.viewmodel.ChapterUi
import com.aichatnovel.app.viewmodel.NovelUiState
import com.aichatnovel.app.viewmodel.NovelViewModel

@Composable
fun NovelRoute(
    onBack: () -> Unit,
    onChapterClick: (String) -> Unit,
    onOpenImport: () -> Unit,
    viewModel: NovelViewModel = viewModel(factory = NovelViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NovelScreen(
        uiState = uiState,
        onBack = onBack,
        onChapterClick = onChapterClick,
        onOpenImport = onOpenImport,
    )
}

/**
 * 作品页：作品信息 + 章节列表。
 *
 * 章节标题是列表里的主要信息，场景数量是辅助信息，「进入」是唯一的行内操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelScreen(
    uiState: NovelUiState,
    onBack: () -> Unit,
    onChapterClick: (String) -> Unit,
    onOpenImport: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.story?.title ?: stringResource(R.string.novel_title),
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

            uiState.story == null -> EmptyState(
                title = stringResource(R.string.novel_empty_title),
                description = stringResource(R.string.novel_empty_desc),
                actionLabel = stringResource(R.string.novel_action_import),
                onAction = onOpenImport,
                modifier = contentModifier,
            )

            else -> LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "story-author") {
                    Text(
                        text = uiState.story.author,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (uiState.story.synopsis.isNotBlank()) {
                    item(key = "story-synopsis") {
                        Text(
                            text = uiState.story.synopsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item(key = "chapters-header") {
                    SectionHeader(
                        title = stringResource(R.string.novel_chapters_title),
                        trailing = stringResource(R.string.novel_chapters_count, uiState.chapters.size),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(items = uiState.chapters, key = { it.id }) { chapter ->
                    ChapterCard(chapter = chapter, onClick = { onChapterClick(chapter.id) })
                }

                item(key = "import-entry") {
                    TextButton(
                        onClick = onOpenImport,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.novel_import_entry_title))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterCard(
    chapter: ChapterUi,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (chapter.sceneCount > 0) {
                        stringResource(R.string.novel_chapter_scene_count, chapter.sceneCount)
                    } else {
                        stringResource(R.string.novel_chapter_empty)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.novel_action_enter),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
