package com.aichatnovel.app.ui.scene

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichatnovel.app.R
import com.aichatnovel.app.ui.components.EmptyState
import com.aichatnovel.app.ui.components.LoadingState
import com.aichatnovel.app.ui.components.presentationModeLabel
import com.aichatnovel.app.viewmodel.SceneUi
import com.aichatnovel.app.viewmodel.SceneUiState
import com.aichatnovel.app.viewmodel.SceneViewModel

@Composable
fun SceneRoute(
    chapterId: String?,
    onBack: () -> Unit,
    onSceneClick: (String) -> Unit,
    viewModel: SceneViewModel = viewModel(factory = SceneViewModel.factory(chapterId)),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SceneScreen(
        uiState = uiState,
        onBack = onBack,
        onSceneClick = onSceneClick,
    )
}

/**
 * 场景页：一个章节下的全部场景。
 *
 * 每个场景卡片回答：这段剧情发生在哪、以什么方式发生、有多少角色与节拍、语音是否已生成，
 * 以及唯一的下一步——进入演出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneScreen(
    uiState: SceneUiState,
    onBack: () -> Unit,
    onSceneClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.chapterTitle ?: stringResource(R.string.scene_title),
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

            uiState.scenes.isEmpty() -> EmptyState(
                title = stringResource(R.string.scene_empty_title),
                description = stringResource(R.string.scene_empty_desc),
                modifier = contentModifier,
            )

            else -> LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = uiState.scenes, key = { it.id }) { scene ->
                    SceneCard(scene = scene, onClick = { onSceneClick(scene.id) })
                }
            }
        }
    }
}

@Composable
private fun SceneCard(
    scene: SceneUi,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = scene.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            scene.location?.let { location ->
                Text(
                    text = stringResource(R.string.scene_meta_location, location),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            scene.timeOfDay?.let { timeOfDay ->
                Text(
                    text = stringResource(R.string.scene_meta_time, timeOfDay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = presentationModeLabel(scene.presentationMode),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = buildString {
                    append(stringResource(R.string.scene_meta_characters, scene.characterCount))
                    append(" · ")
                    append(stringResource(R.string.scene_meta_beats, scene.beatCount))
                    append(" · ")
                    append(
                        stringResource(
                            if (scene.hasAudio) R.string.scene_audio_ready else R.string.scene_audio_absent,
                        ),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.scene_action_enter))
            }
        }
    }
}
