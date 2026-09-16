package com.aichatnovel.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aichatnovel.app.AIChatNovelApplication
import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.repository.StoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class NovelUiState(
    val isLoading: Boolean = true,
    val story: Story? = null,
    val chapters: List<Chapter> = emptyList(),
)

/**
 * 小说页 ViewModel：展示作品信息与章节列表。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NovelViewModel(
    storyRepository: StoryRepository,
) : ViewModel() {

    val uiState: StateFlow<NovelUiState> = storyRepository.observeStories()
        .map { it.firstOrNull() }
        .flatMapLatest { story: Story? ->
            if (story == null) {
                flowOf(NovelUiState(isLoading = false))
            } else {
                storyRepository.observeChapters(story.id)
                    .map { chapters -> NovelUiState(isLoading = false, story = story, chapters = chapters) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = NovelUiState(),
        )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AIChatNovelApplication
                NovelViewModel(app.container.storyRepository)
            }
        }
    }
}
