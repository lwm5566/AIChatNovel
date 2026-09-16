package com.aichatnovel.app.ui.storyimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichatnovel.app.R
import com.aichatnovel.app.di.StoryImportMode
import com.aichatnovel.app.repository.StoryImportFailure
import com.aichatnovel.app.viewmodel.ImportStatus
import com.aichatnovel.app.viewmodel.ImportUiState
import com.aichatnovel.app.viewmodel.ImportViewModel

@Composable
fun ImportRoute(
    onBack: () -> Unit,
    onOpenStory: () -> Unit,
    onOpenExplorer: () -> Unit,
    viewModel: ImportViewModel = viewModel(factory = ImportViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ImportScreen(
        uiState = uiState,
        onBack = onBack,
        onModeChange = viewModel::onModeChange,
        onNovelTextChange = viewModel::onNovelTextChange,
        onUseSampleText = viewModel::useSampleText,
        onStoryTitleChange = viewModel::onStoryTitleChange,
        onAuthorChange = viewModel::onAuthorChange,
        onSynopsisChange = viewModel::onSynopsisChange,
        onChapterTitleChange = viewModel::onChapterTitleChange,
        onImport = viewModel::import,
        onOpenStory = onOpenStory,
        onOpenExplorer = onOpenExplorer,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    uiState: ImportUiState,
    onBack: () -> Unit,
    onModeChange: (StoryImportMode) -> Unit,
    onNovelTextChange: (String) -> Unit,
    onUseSampleText: () -> Unit,
    onStoryTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onSynopsisChange: (String) -> Unit,
    onChapterTitleChange: (String) -> Unit,
    onImport: () -> Unit,
    onOpenStory: () -> Unit,
    onOpenExplorer: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeSelector(mode = uiState.mode, onModeChange = onModeChange)

            OutlinedTextField(
                value = uiState.novelText,
                onValueChange = onNovelTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.import_text_label)) },
                minLines = 6,
            )

            MetadataSection(
                uiState = uiState,
                onStoryTitleChange = onStoryTitleChange,
                onAuthorChange = onAuthorChange,
                onSynopsisChange = onSynopsisChange,
                onChapterTitleChange = onChapterTitleChange,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onUseSampleText) {
                    Text(stringResource(R.string.import_action_fill_sample))
                }
                Button(
                    onClick = onImport,
                    enabled = uiState.status != ImportStatus.Importing &&
                        (uiState.mode == StoryImportMode.LOCAL_SAMPLE || uiState.novelText.isNotBlank()),
                ) {
                    Text(stringResource(R.string.import_action_submit))
                }
            }

            ImportStatusView(
                status = uiState.status,
                onOpenStory = onOpenStory,
                onOpenExplorer = onOpenExplorer,
            )
        }
    }
}

@Composable
private fun MetadataSection(
    uiState: ImportUiState,
    onStoryTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onSynopsisChange: (String) -> Unit,
    onChapterTitleChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.import_metadata_title),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = uiState.storyTitle,
                onValueChange = onStoryTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.import_story_title_label)) },
                singleLine = true,
            )

            OutlinedTextField(
                value = uiState.author,
                onValueChange = onAuthorChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.import_author_label)) },
                singleLine = true,
            )

            OutlinedTextField(
                value = uiState.synopsis,
                onValueChange = onSynopsisChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.import_synopsis_label)) },
                minLines = 2,
            )

            OutlinedTextField(
                value = uiState.chapterTitle,
                onValueChange = onChapterTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.import_chapter_title_label)) },
                singleLine = true,
            )

            Text(
                text = stringResource(R.string.import_metadata_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.mode == StoryImportMode.LOCAL_SAMPLE) {
                Text(
                    text = stringResource(R.string.import_metadata_local_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModeSelector(
    mode: StoryImportMode,
    onModeChange: (StoryImportMode) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.import_mode_title),
                style = MaterialTheme.typography.titleMedium,
            )
            ModeOption(
                label = stringResource(R.string.import_mode_local),
                value = StoryImportMode.LOCAL_SAMPLE,
                selected = mode,
                onSelect = onModeChange,
            )
            ModeOption(
                label = stringResource(R.string.import_mode_remote),
                value = StoryImportMode.REMOTE_DEEPSEEK,
                selected = mode,
                onSelect = onModeChange,
            )
            Text(
                text = when (mode) {
                    StoryImportMode.LOCAL_SAMPLE -> "本地样例使用内置样例原文，输入内容会被忽略。"
                    StoryImportMode.REMOTE_DEEPSEEK -> "需要有效的 DeepSeek API Key；未配置时会明确失败，不会伪装成功。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeOption(
    label: String,
    value: StoryImportMode,
    selected: StoryImportMode,
    onSelect: (StoryImportMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = value == selected, onClick = { onSelect(value) })
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ImportStatusView(
    status: ImportStatus,
    onOpenStory: () -> Unit,
    onOpenExplorer: () -> Unit,
) {
    when (status) {
        ImportStatus.Idle -> Unit

        ImportStatus.Importing -> Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.import_parsing),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is ImportStatus.Success -> ResultCard(
            title = stringResource(R.string.import_result_success),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            lines = listOf(
                stringResource(R.string.import_result_scene_count, status.sceneCount),
                stringResource(R.string.import_result_character_count, status.characterCount),
            ),
            onOpenStory = onOpenStory,
            onOpenExplorer = onOpenExplorer,
        )

        is ImportStatus.Partial -> ResultCard(
            title = stringResource(R.string.import_result_partial),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            lines = listOf(
                stringResource(R.string.import_result_scene_count, status.sceneCount),
                stringResource(R.string.import_result_character_count, status.characterCount),
                stringResource(R.string.import_result_warning_count, status.warnings.size),
            ),
            onOpenStory = onOpenStory,
            onOpenExplorer = onOpenExplorer,
        )

        is ImportStatus.Failure -> FailureCard(reason = status.reason)
    }
}

/**
 * 导入失败的展示。
 *
 * 只给出用户看得懂的原因与下一步建议；内部编码、JSON 路径与供应商原始信息
 * 属于诊断信息，不进普通界面。
 */
@Composable
private fun FailureCard(reason: StoryImportFailure) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.import_result_failure),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = importFailureText(reason),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * 导入失败原因的用户可读说明：一句话说清发生了什么，一句话说下一步怎么办。
 */
internal fun importFailureText(reason: StoryImportFailure): String = when (reason) {
    StoryImportFailure.MISSING_API_KEY -> "尚未配置 AI 服务凭据，无法解析小说。请联系管理员配置后重试。"
    StoryImportFailure.NETWORK -> "网络连接失败，请检查网络后重试。"
    StoryImportFailure.TIMEOUT -> "解析等待超时，请稍后重试。"
    StoryImportFailure.HTTP_ERROR -> "AI 服务暂时不可用，请稍后重试。"
    StoryImportFailure.EMPTY_RESPONSE,
    StoryImportFailure.MALFORMED_ENVELOPE,
    StoryImportFailure.MARKDOWN_RESPONSE,
    StoryImportFailure.NOT_JSON_OBJECT,
    StoryImportFailure.INVALID_JSON,
    -> "AI 返回的内容无法解析成剧情结构，请重试；若反复失败，请换一段原文。"

    StoryImportFailure.VALIDATION_ERROR -> "解析结果未通过内容校验，请尝试换一段原文后重试。"
}

@Composable
private fun ResultCard(
    title: String,
    containerColor: Color,
    contentColor: Color,
    lines: List<String>,
    onOpenStory: () -> Unit,
    onOpenExplorer: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            lines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = onOpenStory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.import_action_open_story))
            }
            TextButton(onClick = onOpenExplorer) {
                Text(stringResource(R.string.import_action_open_explorer))
            }
        }
    }
}
