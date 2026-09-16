package com.aichatnovel.app.ui.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichatnovel.app.R
import com.aichatnovel.app.ui.components.LoadingState
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
                title = { Text(stringResource(R.string.novel_title)) },
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
            else -> {
                val story = uiState.story
                Column(
                    modifier = contentModifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ImportEntryCard(onOpenImport)

                    if (story == null) {
                        Text(
                            text = "暂无作品",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        return@Column
                    }

                    Text(text = story.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "作者：${story.author}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = story.synopsis, style = MaterialTheme.typography.bodyMedium)

                    HorizontalDivider()

                    Text(text = "章节", style = MaterialTheme.typography.titleMedium)

                    uiState.chapters.forEach { chapter ->
                        Card(
                            onClick = { onChapterClick(chapter.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(text = chapter.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "查看该章节的场景",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportEntryCard(onOpenImport: () -> Unit) {
    Card(
        onClick = onOpenImport,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.novel_import_entry_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.novel_import_entry_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
