package com.aichatnovel.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aichatnovel.app.AIChatNovelApplication
import com.aichatnovel.app.domain.model.AudioAsset
import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.repository.AudioAssetRepository
import com.aichatnovel.app.repository.PerformanceRepository
import com.aichatnovel.app.repository.StoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 场景列表里的一个条目。
 *
 * [hasAudio] 只在**真实存在**该场景事件的 [AudioAsset] 时为真；
 * 它绝不根据估算时长推断。
 */
data class SceneUi(
    val id: String,
    val title: String,
    val location: String?,
    val timeOfDay: String?,
    val presentationMode: PresentationMode,
    val characterCount: Int,
    val beatCount: Int,
    val hasAudio: Boolean,
)

data class SceneUiState(
    val isLoading: Boolean = true,
    val chapterTitle: String? = null,
    val scenes: List<SceneUi> = emptyList(),
)

/**
 * 场景页 ViewModel：展示某个章节下的场景列表。
 *
 * [chapterId] 为空时回退到第一部作品的首个章节，以便从首页直接进入。
 * 场景的角色数与节拍数来自 Domain/Repository 的既有数据，音频状态来自音频索引，
 * 二者都不做任何推断。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SceneViewModel(
    chapterId: String?,
    storyRepository: StoryRepository,
    performanceRepository: PerformanceRepository,
    audioAssetRepository: AudioAssetRepository? = null,
) : ViewModel() {

    private val chapter: Flow<Chapter?> = storyRepository.observeStories()
        .map { it.firstOrNull()?.id }
        .flatMapLatest { storyId ->
            if (storyId == null) flowOf(emptyList()) else storyRepository.observeChapters(storyId)
        }
        .map { chapters ->
            if (chapterId == null) chapters.firstOrNull() else chapters.firstOrNull { it.id == chapterId }
        }

    private val scenes: Flow<List<Scene>> = chapter
        .map { it?.id }
        .flatMapLatest { resolvedChapterId ->
            if (resolvedChapterId == null) flowOf(emptyList()) else storyRepository.observeScenes(resolvedChapterId)
        }

    private val audioAssets: Flow<Map<String, AudioAsset>> =
        audioAssetRepository?.observeAudioAssets() ?: flowOf(emptyMap())

    private val scenesWithBeats: Flow<List<Pair<Scene, List<Beat>>>> = scenes
        .flatMapLatest { sceneList ->
            if (sceneList.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(sceneList.map { scene -> performanceRepository.observeBeats(scene.id).map { scene to it } }) { it.toList() }
            }
        }

    val uiState: StateFlow<SceneUiState> = combine(
        chapter,
        scenesWithBeats,
        audioAssets,
    ) { currentChapter, sceneBeats, assets ->
        SceneUiState(
            isLoading = false,
            chapterTitle = currentChapter?.title,
            scenes = sceneBeats.map { (scene, beats) -> scene.toUi(beats, assets) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SceneUiState(),
    )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(chapterId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AIChatNovelApplication
                SceneViewModel(
                    chapterId = chapterId,
                    storyRepository = app.container.storyRepository,
                    performanceRepository = app.container.performanceRepository,
                    audioAssetRepository = app.container.audioAssetRepository,
                )
            }
        }
    }
}

private fun Scene.toUi(
    beats: List<Beat>,
    audioAssets: Map<String, AudioAsset>,
): SceneUi = SceneUi(
    id = id,
    title = title,
    location = setting.location,
    timeOfDay = setting.timeOfDay,
    presentationMode = presentationMode,
    characterCount = characters.size,
    beatCount = beats.size,
    hasAudio = beats.any { beat -> beat.events.any { audioAssets.containsKey(it.id) } },
)
