package com.aichatnovel.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aichatnovel.app.AIChatNovelApplication
import com.aichatnovel.app.domain.mapping.buildExecutableTimeline
import com.aichatnovel.app.domain.mapping.cursorAt
import com.aichatnovel.app.domain.mapping.effectivePresentationMode
import com.aichatnovel.app.domain.mapping.effectiveSpeakerId
import com.aichatnovel.app.domain.mapping.effectiveVoiceProfile
import com.aichatnovel.app.domain.mapping.orderedEvents
import com.aichatnovel.app.domain.model.ActionEvent
import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.CameraEvent
import com.aichatnovel.app.domain.model.DialogueEvent
import com.aichatnovel.app.domain.model.EnvironmentEvent
import com.aichatnovel.app.domain.model.ExecutableTimeline
import com.aichatnovel.app.domain.model.NarrationEvent
import com.aichatnovel.app.domain.model.PerformanceEvent
import com.aichatnovel.app.domain.model.PlaybackState
import com.aichatnovel.app.domain.model.PlaybackStatus
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.SoundEvent
import com.aichatnovel.app.domain.model.Timing
import com.aichatnovel.app.domain.model.VoiceProfile
import com.aichatnovel.app.repository.CharacterRepository
import com.aichatnovel.app.repository.PerformanceRepository
import com.aichatnovel.app.repository.StoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 剧情演出中的一行内容，供 UI 直接渲染。
 */
data class PerformanceLine(
    val id: String,
    val kind: String,
    val speaker: String?,
    val voiceLabel: String?,
    val text: String,
    val timing: Timing,
    val presentationMode: PresentationMode,
)

/** 一个演出节拍，包含并发/重叠的若干演出行。 */
data class BeatUi(
    val id: String,
    val label: String,
    val lines: List<PerformanceLine>,
)

data class StoryPlayUiState(
    val isLoading: Boolean = true,
    val presentationMode: PresentationMode = PresentationMode.LiveScene,
    val beats: List<BeatUi> = emptyList(),
)

/**
 * 剧情演出页 ViewModel：把某个场景的演出节拍转换为可直接渲染的结构。
 *
 * [sceneId] 为空时回退到第一部作品首个章节的首个场景，以便从首页直接进入。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryPlayViewModel(
    sceneId: String?,
    performanceRepository: PerformanceRepository,
    characterRepository: CharacterRepository,
    storyRepository: StoryRepository,
) : ViewModel() {

    private val scene: Flow<Scene?> = if (sceneId != null) {
        storyRepository.observeScene(sceneId)
    } else {
        storyRepository.observeStories()
            .map { it.firstOrNull()?.id }
            .flatMapLatest { storyId ->
                if (storyId == null) flowOf(emptyList()) else storyRepository.observeChapters(storyId)
            }
            .map { it.firstOrNull()?.id }
            .flatMapLatest { chapterId ->
                if (chapterId == null) flowOf(emptyList()) else storyRepository.observeScenes(chapterId)
            }
            .map { it.firstOrNull() }
    }

    private val beats: Flow<List<Beat>> = scene
        .map { it?.id }
        .flatMapLatest { resolvedSceneId ->
            if (resolvedSceneId == null) flowOf(emptyList()) else performanceRepository.observeBeats(resolvedSceneId)
        }

    /** 当前场景的可执行时间轴；场景或节拍变化时重建。 */
    private val timelineFlow: Flow<ExecutableTimeline> = combine(scene, beats) { currentScene, beatList ->
        if (currentScene == null) ExecutableTimeline() else buildExecutableTimeline(currentScene, beatList)
    }

    private val _timeline = MutableStateFlow(ExecutableTimeline())

    /** 当前场景的时间轴，供 UI 读取总时长与定位规则。 */
    val timeline: StateFlow<ExecutableTimeline> = _timeline.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())

    /** 播放状态。UI 只渲染它，并通过 [play] / [pause] / [reset] / [seekTo] 发出意图。 */
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var tickJob: Job? = null

    init {
        timelineFlow
            .onEach { newTimeline ->
                stopTicking()
                _timeline.value = newTimeline
                _playbackState.value = PlaybackState(durationMillis = newTimeline.totalDurationMillis)
            }
            .launchIn(viewModelScope)
    }

    val uiState: StateFlow<StoryPlayUiState> = combine(
        scene,
        beats,
        characterRepository.observeAllCharacters(),
    ) { currentScene, beatList, characters ->
        val names = characters.associate { it.id to it.name }
        val profiles = characters.associate { it.id to it.voiceProfile }
        val sceneMode = currentScene?.presentationMode ?: PresentationMode.LiveScene
        StoryPlayUiState(
            isLoading = false,
            presentationMode = sceneMode,
            beats = beatList.map { beat -> beat.toUi(names, profiles, sceneMode) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StoryPlayUiState(),
    )

    /** 开始或继续播放。没有可播放内容时不产生推进。 */
    fun play() {
        if (_playbackState.value.status == PlaybackStatus.Playing) return
        val next = _playbackState.value.play()
        _playbackState.value = if (next.status == PlaybackStatus.Playing) {
            next.withCursor(_timeline.value.cursorAt(next.positionMillis))
        } else {
            next
        }
        if (next.status == PlaybackStatus.Playing) startTicking() else stopTicking()
    }

    /** 暂停播放，位置保留。 */
    fun pause() {
        _playbackState.value = _playbackState.value.pause()
        stopTicking()
    }

    /** 重置到初始位置。 */
    fun reset() {
        stopTicking()
        _playbackState.value = _playbackState.value.reset()
    }

    /** 跳转到指定位置，并立刻重新定位当前事件。 */
    fun seekTo(positionMillis: Long) {
        val next = _playbackState.value.seekTo(positionMillis)
        _playbackState.value = next.withCursor(_timeline.value.cursorAt(next.positionMillis))
    }

    override fun onCleared() {
        stopTicking()
        super.onCleared()
    }

    private fun startTicking() {
        stopTicking()
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MILLIS)
                val current = _playbackState.value
                if (current.status != PlaybackStatus.Playing) break
                val advanced = current.advanceBy(TICK_INTERVAL_MILLIS)
                _playbackState.value = advanced.withCursor(_timeline.value.cursorAt(advanced.positionMillis))
                if (advanced.status != PlaybackStatus.Playing) break
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** 本地模拟播放的时间片长度；接入真实播放器后由播放器驱动推进。 */
        private const val TICK_INTERVAL_MILLIS = 100L

        fun factory(sceneId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AIChatNovelApplication
                StoryPlayViewModel(
                    sceneId = sceneId,
                    performanceRepository = app.container.performanceRepository,
                    characterRepository = app.container.characterRepository,
                    storyRepository = app.container.storyRepository,
                )
            }
        }
    }
}

private fun Beat.toUi(
    names: Map<String, String>,
    profiles: Map<String, VoiceProfile?>,
    sceneMode: PresentationMode,
): BeatUi = BeatUi(
    id = id,
    label = "节拍 $order",
    lines = orderedEvents().map { it.toLine(names, profiles, sceneMode) },
)

private fun PerformanceEvent.toLine(
    names: Map<String, String>,
    profiles: Map<String, VoiceProfile?>,
    sceneMode: PresentationMode,
): PerformanceLine = when (this) {
    is DialogueEvent -> {
        val speakerId = effectiveSpeakerId
        PerformanceLine(
            id = id,
            kind = "对白",
            speaker = names[speakerId] ?: speakerId,
            voiceLabel = effectiveVoiceProfile(profiles[speakerId])?.voiceRef,
            text = dialogue.text,
            timing = timing,
            presentationMode = effectivePresentationMode(sceneMode),
        )
    }

    is NarrationEvent -> PerformanceLine(
        id = id,
        kind = "旁白",
        speaker = null,
        voiceLabel = null,
        text = narration.text,
        timing = timing,
        presentationMode = effectivePresentationMode(sceneMode),
    )

    is ActionEvent -> PerformanceLine(
        id = id,
        kind = "动作",
        speaker = action.characterId?.let { names[it] },
        voiceLabel = null,
        text = action.description,
        timing = timing,
        presentationMode = effectivePresentationMode(sceneMode),
    )

    is EnvironmentEvent -> PerformanceLine(
        id = id,
        kind = "环境",
        speaker = null,
        voiceLabel = null,
        text = environment.description,
        timing = timing,
        presentationMode = effectivePresentationMode(sceneMode),
    )

    is SoundEvent -> PerformanceLine(
        id = id,
        kind = "环境音",
        speaker = null,
        voiceLabel = null,
        text = description,
        timing = timing,
        presentationMode = effectivePresentationMode(sceneMode),
    )

    is CameraEvent -> PerformanceLine(
        id = id,
        kind = "镜头",
        speaker = null,
        voiceLabel = null,
        text = description,
        timing = timing,
        presentationMode = effectivePresentationMode(sceneMode),
    )
}
