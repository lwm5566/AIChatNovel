package com.aichatnovel.app.ui.scene

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichatnovel.app.R
import com.aichatnovel.app.ui.components.EmptyState
import com.aichatnovel.app.ui.components.LoadingState
import com.aichatnovel.app.ui.components.presentationModeLabel
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
                title = { Text(stringResource(R.string.scene_title)) },
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
            uiState.scenes.isEmpty() -> EmptyState(message = "暂无场景", modifier = contentModifier)

            else -> LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = uiState.scenes, key = { it.id }) { scene ->
                    Card(
                        onClick = { onSceneClick(scene.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(text = scene.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "地点：${scene.setting.location ?: "未知"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "呈现方式：${presentationModeLabel(scene.presentationMode)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "进入剧情演出",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
