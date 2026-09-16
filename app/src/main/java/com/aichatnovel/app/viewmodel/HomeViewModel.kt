package com.aichatnovel.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aichatnovel.app.AIChatNovelApplication
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.repository.StoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 首页状态：当前作品，以及它的章节 / 场景规模。
 *
 * 首页只回答三个问题：我有什么作品、做到哪一步了、下一步做什么。
 * 因此这里只暴露作品本身与两个计数，不暴露任何内容结构细节。
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val story: Story? = null,
    val chapterCount: Int = 0,
    val sceneCount: Int = 0,
)

/**
 * 首页 ViewModel。
 *
 * 当前数据模型只保留一份「当前导入快照」，因此这里取第一部作品；
 * 场景数由各章节的场景流汇总得到，不额外引入新的数据入口。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    storyRepository: StoryRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = storyRepository.observeStories()
        .map { it.firstOrNull() }
        .flatMapLatest { story: Story? ->
            if (story == null) {
                flowOf(HomeUiState(isLoading = false))
            } else {
                storyRepository.observeChapters(story.id).flatMapLatest { chapters ->
                    if (chapters.isEmpty()) {
                        flowOf(HomeUiState(isLoading = false, story = story))
                    } else {
                        combine(chapters.map { storyRepository.observeScenes(it.id) }) { scenesByChapter ->
                            HomeUiState(
                                isLoading = false,
                                story = story,
                                chapterCount = chapters.size,
                                sceneCount = scenesByChapter.sumOf { it.size },
                            )
                        }
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HomeUiState(),
        )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AIChatNovelApplication
                HomeViewModel(app.container.storyRepository)
            }
        }
    }
}
