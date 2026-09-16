package com.aichatnovel.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aichatnovel.app.AIChatNovelApplication
import com.aichatnovel.app.domain.mapping.effectiveSpeakerId
import com.aichatnovel.app.domain.mapping.orderedEvents
import com.aichatnovel.app.domain.model.ActionEvent
import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.CameraEvent
import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Character
import com.aichatnovel.app.domain.model.DialogueEvent
import com.aichatnovel.app.domain.model.EnvironmentEvent
import com.aichatnovel.app.domain.model.NarrationEvent
import com.aichatnovel.app.domain.model.PerformanceEvent
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.SceneSetting
import com.aichatnovel.app.domain.model.SoundEvent
import com.aichatnovel.app.domain.model.SourceSpan
import com.aichatnovel.app.domain.model.SpeechParams
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.domain.model.Timing
import com.aichatnovel.app.repository.CharacterRepository
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

/** 解析结果页的事件展示模型。只做信息展示，不承载播放语义。 */
data class ExplorerEvent(
    val id: String,
    val kind: String,
    val summary: String,
    val details: List<String>,
    val timingLabel: String,
    val presentationOverride: PresentationMode?,
    val sourceSpan: SourceSpan?,
)

data class ExplorerBeat(
    val id: String,
    val order: Int,
    val events: List<ExplorerEvent>,
)

data class ExplorerScene(
    val id: String,
    val index: Int,
    val title: String,
    val presentationMode: PresentationMode,
    val evidence: String?,
    val settingLines: List<String>,
    val participants: List<String>,
    val totalDurationLabel: String?,
    val beats: List<ExplorerBeat>,
)

data class ExplorerChapter(
    val id: String,
    val index: Int,
    val title: String,
    val scenes: List<ExplorerScene>,
)

data class StoryExplorerUiState(
    val isLoading: Boolean = true,
    val story: Story? = null,
    val chapters: List<ExplorerChapter> = emptyList(),
)

/**
 * 解析结果页 ViewModel：把 StoryContent 组织成 Story → Chapter → Scene → Beat → PerformanceEvent 的展示树。
 *
 * 全部数据都经 Repository 取得（作品 / 章节 / 场景 / 节拍 / 角色），
 * 不直接读内容仓库、不持有任何 AI 输出对象；呈现方式等语义完全沿用 Domain。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryExplorerViewModel(
    private val storyRepository: StoryRepository,
    private val performanceRepository: PerformanceRepository,
    private val characterRepository: CharacterRepository,
) : ViewModel() {

    val uiState: StateFlow<StoryExplorerUiState> = storyRepository.observeStories()
        .flatMapLatest { stories ->
            val story = stories.firstOrNull()
            if (story == null) {
                flowOf(StoryExplorerUiState(isLoading = false))
            } else {
                val characters = characterRepository.observeAllCharacters()
                storyRepository.observeChapters(story.id).flatMapLatest { chapters ->
                    if (chapters.isEmpty()) {
                        flowOf(StoryExplorerUiState(isLoading = false, story = story))
                    } else {
                        combine(chapters.map { chapter -> chapterFlow(chapter, characters) }) { array ->
                            StoryExplorerUiState(isLoading = false, story = story, chapters = array.toList())
                        }
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = StoryExplorerUiState(),
        )

    private fun chapterFlow(
        chapter: Chapter,
        characters: Flow<List<Character>>,
    ): Flow<ExplorerChapter> = storyRepository.observeScenes(chapter.id)
        .flatMapLatest { scenes ->
            if (scenes.isEmpty()) {
                flowOf(emptyList<ExplorerScene>())
            } else {
                combine(scenes.map { scene -> sceneFlow(scene, characters) }) { array -> array.toList() }
            }
        }
        .map { scenes -> ExplorerChapter(chapter.id, chapter.index, chapter.title, scenes) }

    private fun sceneFlow(
        scene: Scene,
        characters: Flow<List<Character>>,
    ): Flow<ExplorerScene> = combine(
        performanceRepository.observeBeats(scene.id),
        characters,
    ) { beats, characterList ->
        scene.toExplorerScene(beats, characterList)
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AIChatNovelApplication
                StoryExplorerViewModel(
                    storyRepository = app.container.storyRepository,
                    performanceRepository = app.container.performanceRepository,
                    characterRepository = app.container.characterRepository,
                )
            }
        }
    }
}

private fun Scene.toExplorerScene(beats: List<Beat>, characterList: List<Character>): ExplorerScene {
    val names = characterList.associate { it.id to it.name }
    return ExplorerScene(
        id = id,
        index = index,
        title = title,
        presentationMode = presentationMode,
        evidence = presentationEvidence?.text,
        settingLines = setting.toLines(),
        participants = characters.map { it.characterId }.map { names[it] ?: it },
        totalDurationLabel = timeline.totalDurationMillis?.let { millis ->
            "${millis / 1000.0}s（${timeline.durationSource?.name ?: "来源未标注"}）"
        },
        beats = beats.sortedBy { it.order }.map { it.toExplorerBeat(names) },
    )
}

private fun SceneSetting.toLines(): List<String> = buildList {
    location?.let { add("地点：$it") }
    timeOfDay?.let { add("时间：$it") }
    weather?.let { add("天气：$it") }
    lighting?.let { add("光线：$it") }
}

private fun Beat.toExplorerBeat(names: Map<String, String>): ExplorerBeat = ExplorerBeat(
    id = id,
    order = order,
    events = orderedEvents().map { it.toExplorerEvent(names) },
)

private fun PerformanceEvent.toExplorerEvent(
    names: Map<String, String>,
): ExplorerEvent {
    val kind: String
    val summary: String
    val details = mutableListOf<String>()

    when (this) {
        is DialogueEvent -> {
            val speakerId = effectiveSpeakerId
            kind = "对白"
            summary = dialogue.text
            details += "说话者：${names[speakerId] ?: speakerId}"
            dialogue.emotion?.let { details += "情绪：$it" }
            utterance.emotion?.let { details += "演出情绪：$it" }
            utterance.speakingStyle?.let { details += "语气风格：$it" }
            utterance.addressee?.let { details += "对谁：${names[it] ?: it}" }
            if (utterance.isInnerMonologue) details += "内心独白"
            utterance.language?.let { details += "语言：$it" }
            speech?.toSpeechLines()?.let { details += it }
            voiceOverride?.voiceRef?.let { details += "音色覆盖：$it" }
        }

        is NarrationEvent -> {
            kind = "旁白"
            summary = narration.text
        }

        is ActionEvent -> {
            kind = "动作"
            summary = action.description
            action.characterId?.let { details += "角色：${names[it] ?: it}" }
        }

        is EnvironmentEvent -> {
            kind = "环境"
            summary = environment.description
            environment.location?.let { details += "地点：$it" }
        }

        is SoundEvent -> {
            kind = "环境音"
            summary = description
            soundRef?.let { details += "音效资源：${it.id}" }
        }

        is CameraEvent -> {
            kind = "镜头"
            summary = description
            shotRef?.let { details += "画面资源：${it.id}" }
        }
    }

    return ExplorerEvent(
        id = id,
        kind = kind,
        summary = summary,
        details = details,
        timingLabel = timing.toLabel(),
        presentationOverride = presentationOverride,
        sourceSpan = sourceSpan,
    )
}

private fun SpeechParams.toSpeechLines(): String? {
    val parts = buildList {
        rate?.let { add("语速=$it") }
        pitch?.let { add("音高=$it") }
        volume?.let { add("音量=$it") }
    }
    return if (parts.isEmpty()) null else "发声参数：${parts.joinToString()}"
}

private fun Timing.toLabel(): String {
    val start = "${startOffsetMillis / 1000.0}s"
    val duration = durationMillis?.let { "+${it / 1000.0}s" } ?: "+时长待定"
    val source = durationSource?.let { "（${it.name}）" }.orEmpty()
    return "$start $duration$source"
}
