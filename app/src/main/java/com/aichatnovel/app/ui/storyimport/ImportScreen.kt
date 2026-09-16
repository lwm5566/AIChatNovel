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
import com.aichatnovel.app.viewmodel.ImportStatus
import com.aichatnovel.app.viewmodel.ImportUiState
import com.aichatnovel.app.viewmodel.ImportViewModel

@Composable
fun ImportRoute(
    onBack: () -> Unit,
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
        onImport = viewModel::import,
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
    onImport: () -> Unit,
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

            ImportStatusView(status = uiState.status, onOpenExplorer = onOpenExplorer)
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
    onOpenExplorer: () -> Unit,
) {
    when (status) {
        ImportStatus.Idle -> Unit

        ImportStatus.Importing -> Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator()
            Text(text = "正在解析…", style = MaterialTheme.typography.bodyMedium)
        }

        is ImportStatus.Success -> ResultCard(
            title = "解析成功",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            lines = listOf(
                "场景：${status.sceneCount}",
                "角色：${status.characterCount}",
                "schemaVersion：${status.schemaVersion}",
            ),
            onOpenExplorer = onOpenExplorer,
        )

        is ImportStatus.Partial -> ResultCard(
            title = "解析完成，但存在告警",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            lines = listOf(
                "场景：${status.sceneCount}",
                "角色：${status.characterCount}",
                "schemaVersion：${status.schemaVersion}",
            ) + status.warnings.map { "告警：$it" },
            onOpenExplorer = onOpenExplorer,
        )

        is ImportStatus.Failure -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "解析失败", style = MaterialTheme.typography.titleMedium)
                Text(text = "原因：${status.reason.name}", style = MaterialTheme.typography.bodyMedium)
                Text(text = status.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    containerColor: Color,
    contentColor: Color,
    lines: List<String>,
    onOpenExplorer: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            lines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onOpenExplorer) {
                Text(stringResource(R.string.import_action_open_explorer))
            }
        }
    }
}
