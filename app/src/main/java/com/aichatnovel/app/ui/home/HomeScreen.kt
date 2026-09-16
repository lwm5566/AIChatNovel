package com.aichatnovel.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichatnovel.app.R
import com.aichatnovel.app.ui.components.EmptyState
import com.aichatnovel.app.ui.components.LoadingState
import com.aichatnovel.app.viewmodel.HomeUiState
import com.aichatnovel.app.viewmodel.HomeViewModel

@Composable
fun HomeRoute(
    onContinueWork: () -> Unit,
    onImportStory: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onContinueWork = onContinueWork,
        onImportStory = onImportStory,
        onOpenCharacters = onOpenCharacters,
        onOpenSettings = onOpenSettings,
    )
}

/**
 * 首页。
 *
 * 第一视觉重点是**当前作品**与「继续制作」；角色与设置是辅助入口，
 * 刻意使用弱化的文字按钮，不与主操作争夺视觉权重。
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onContinueWork: () -> Unit,
    onImportStory: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BrandHeader()

            val story = uiState.story
            when {
                uiState.isLoading -> LoadingState(
                    modifier = Modifier.height(200.dp),
                    message = stringResource(R.string.home_loading),
                )

                story == null -> NoStoryState(onImportStory = onImportStory)

                else -> CurrentStorySection(
                    title = story.title,
                    author = story.author,
                    chapterCount = uiState.chapterCount,
                    sceneCount = uiState.sceneCount,
                    onContinueWork = onContinueWork,
                )
            }

            SecondaryEntries(
                onOpenCharacters = onOpenCharacters,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@Composable
private fun BrandHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoStoryState(onImportStory: () -> Unit) {
    EmptyState(
        title = stringResource(R.string.home_empty_title),
        description = stringResource(R.string.home_empty_desc),
        actionLabel = stringResource(R.string.home_action_import),
        onAction = onImportStory,
        modifier = Modifier.height(260.dp),
    )
}

@Composable
private fun CurrentStorySection(
    title: String,
    author: String,
    chapterCount: Int,
    sceneCount: Int,
    onContinueWork: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.home_current_story),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.home_story_scale, chapterCount, sceneCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(
            onClick = onContinueWork,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.home_action_continue))
        }
    }
}

@Composable
private fun SecondaryEntries(
    onOpenCharacters: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.home_secondary_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onOpenCharacters) {
                Text(stringResource(R.string.nav_character))
            }
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.nav_settings))
            }
        }
    }
}
