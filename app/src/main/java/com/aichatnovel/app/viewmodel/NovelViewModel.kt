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

/** 作品页的一个章节条目：章节本身，加上它下面有多少个场景。 */
data class ChapterUi(
    val id: String,
    val index: Int,
    val title: String,
    val sceneCount: Int,
)

data class NovelUiState(
    val isLoading: Boolean = true,
    val story: Story? = null,
    val chapters: List<ChapterUi> = emptyList(),
)

/**
 * 作品页 ViewModel：展示当前作品与它的章节。
 *
 * 场景数由各章节的场景流汇总得到，章节顺序沿用 Repository 的既有顺序，不做二次排序。
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
                    .flatMapLatest { chapters ->
                        if (chapters.isEmpty()) {
                            flowOf(NovelUiState(isLoading = false, story = story))
                        } else {
                            combine(chapters.map { storyRepository.observeScenes(it.id) }) { scenesByChapter ->
                                NovelUiState(
                                    isLoading = false,
                                    story = story,
                                    chapters = chapters.mapIndexed { position, chapter ->
                                        ChapterUi(
                                            id = chapter.id,
                                            index = chapter.index,
                                            title = chapter.title,
                                            sceneCount = scenesByChapter[position].size,
                                        )
                                    },
                                )
                            }
                        }
                    }
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
