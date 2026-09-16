package com.aichatnovel.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aichatnovel.app.R

@Composable
fun HomeRoute(
    onOpenNovel: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenScenes: () -> Unit,
    onOpenStoryPlay: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    HomeScreen(
        onOpenNovel = onOpenNovel,
        onOpenCharacters = onOpenCharacters,
        onOpenScenes = onOpenScenes,
        onOpenStoryPlay = onOpenStoryPlay,
        onOpenSettings = onOpenSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenNovel: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenScenes: () -> Unit,
    onOpenStoryPlay: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_title)) })
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
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FeatureCard(
                title = stringResource(R.string.nav_novel),
                description = "浏览作品、章节与场景的内容结构",
                onClick = onOpenNovel,
            )
            FeatureCard(
                title = stringResource(R.string.nav_character),
                description = "查看角色设定，以及 TTS 音色占位",
                onClick = onOpenCharacters,
            )
            FeatureCard(
                title = stringResource(R.string.nav_scene),
                description = "查看章节下的场景列表",
                onClick = onOpenScenes,
            )
            FeatureCard(
                title = stringResource(R.string.nav_story_play),
                description = "按演出事件播放一段剧情",
                onClick = onOpenStoryPlay,
            )
            FeatureCard(
                title = stringResource(R.string.nav_settings),
                description = "播放与展示相关的设置",
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
