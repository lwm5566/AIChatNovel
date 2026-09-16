package com.aichatnovel.app.ui.character

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichatnovel.app.R
import com.aichatnovel.app.domain.model.Character
import com.aichatnovel.app.ui.components.EmptyState
import com.aichatnovel.app.ui.components.LoadingState
import com.aichatnovel.app.viewmodel.CharacterUiState
import com.aichatnovel.app.viewmodel.CharacterViewModel

@Composable
fun CharacterRoute(
    onBack: () -> Unit,
    viewModel: CharacterViewModel = viewModel(factory = CharacterViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CharacterScreen(uiState = uiState, onBack = onBack)
}

/**
 * 角色页：当前作品的角色，以及每个角色的声音状态。
 *
 * 声音状态如实反映「是否已经解析出音色」——没有音色时明确写「未设置声音」，
 * 不提供会假装保存成功的编辑入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterScreen(
    uiState: CharacterUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.character_title)) },
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

            uiState.characters.isEmpty() -> EmptyState(
                title = stringResource(R.string.character_empty_title),
                description = stringResource(R.string.character_empty_desc),
                modifier = contentModifier,
            )

            else -> LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = uiState.characters, key = { it.id }) { character ->
                    CharacterCard(character = character)
                }
                item(key = "voice-footnote") {
                    Text(
                        text = stringResource(R.string.character_voice_footnote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterCard(character: Character) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = character.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (character.description.isNotBlank()) {
                Text(
                    text = character.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val voiceRef = character.voiceProfile?.voiceRef
            Text(
                text = if (voiceRef.isNullOrBlank()) {
                    stringResource(R.string.character_voice_unset)
                } else {
                    stringResource(R.string.character_voice_set, voiceRef)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (voiceRef.isNullOrBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}
