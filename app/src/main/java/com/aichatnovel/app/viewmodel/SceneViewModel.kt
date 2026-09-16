package com.aichatnovel.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aichatnovel.app.AIChatNovelApplication
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.repository.StoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SceneUiState(
    val isLoading: Boolean = true,
    val scenes: List<Scene> = emptyList(),
)

/**
 * 场景页 ViewModel：展示某个章节下的场景列表。
 *
 * [chapterId] 为空时回退到第一部作品的首个章节，以便从首页直接进入。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SceneViewModel(
    chapterId: String?,
    storyRepository: StoryRepository,
) : ViewModel() {

    private val scenes: Flow<List<Scene>> = if (chapterId != null) {
        storyRepository.observeScenes(chapterId)
    } else {
        storyRepository.observeStories()
            .map { it.firstOrNull()?.id }
            .flatMapLatest { storyId ->
                if (storyId == null) flowOf(emptyList()) else storyRepository.observeChapters(storyId)
            }
            .map { it.firstOrNull()?.id }
            .flatMapLatest { resolvedChapterId ->
                if (resolvedChapterId == null) flowOf(emptyList()) else storyRepository.observeScenes(resolvedChapterId)
            }
    }

    val uiState: StateFlow<SceneUiState> = scenes
        .map { SceneUiState(isLoading = false, scenes = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SceneUiState(),
        )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(chapterId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AIChatNovelApplication
                SceneViewModel(chapterId = chapterId, storyRepository = app.container.storyRepository)
            }
        }
    }
}
