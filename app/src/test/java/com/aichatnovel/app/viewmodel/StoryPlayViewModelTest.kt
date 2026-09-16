package com.aichatnovel.app.viewmodel

import com.aichatnovel.app.data.repository.ImportedStory
import com.aichatnovel.app.data.repository.InMemoryCharacterRepository
import com.aichatnovel.app.data.repository.InMemoryPerformanceRepository
import com.aichatnovel.app.data.repository.InMemoryStoryRepository
import com.aichatnovel.app.data.repository.StoryContentStore
import com.aichatnovel.app.domain.model.Action
import com.aichatnovel.app.domain.model.ActionEvent
import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.CameraEvent
import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Character
import com.aichatnovel.app.domain.model.Dialogue
import com.aichatnovel.app.domain.model.DialogueEvent
import com.aichatnovel.app.domain.model.DurationSource
import com.aichatnovel.app.domain.model.Environment
import com.aichatnovel.app.domain.model.EnvironmentEvent
import com.aichatnovel.app.domain.model.Narration
import com.aichatnovel.app.domain.model.NarrationEvent
import com.aichatnovel.app.domain.model.PerformanceEvent
import com.aichatnovel.app.domain.model.PlaybackStatus
import com.aichatnovel.app.domain.model.PresentationEvidence
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.SoundEvent
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.domain.model.StoryContent
import com.aichatnovel.app.domain.model.Timing
import com.aichatnovel.app.domain.model.Utterance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 剧情演出页的只读链路契约：场景选择与三级回退、节拍顺序、演出行映射、空数据降级。
 *
 * 这里用的是真实的 InMemory Repository 与 StoryContentStore（不是 Fake），
 * 因此同时锁定「Domain → Repository → ViewModel」这一整段的既有行为。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryPlayViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a given scene id shows only that scene`() = runTest(dispatcher) {
        val state = viewModel(sceneId = "scene-2").uiState.first { !it.isLoading }

        assertEquals(listOf("beat-scene2"), state.beats.map { it.id })
        assertEquals(PresentationMode.InstantMessaging, state.presentationMode)
    }

    @Test
    fun `another scene id shows only the other scene`() = runTest(dispatcher) {
        val state = viewModel(sceneId = "scene-1").uiState.first { !it.isLoading }

        assertEquals(listOf("beat-1", "beat-2"), state.beats.map { it.id })
        assertTrue(state.beats.none { it.id == "beat-scene2" })
        assertEquals(PresentationMode.LiveScene, state.presentationMode)
    }

    @Test
    fun `an unknown scene id shows nothing`() = runTest(dispatcher) {
        val state = viewModel(sceneId = "scene-missing").uiState.first { !it.isLoading }

        assertTrue(state.beats.isEmpty())
    }

    @Test
    fun `without a scene id it falls back to the first scene of the first chapter`() = runTest(dispatcher) {
        val state = viewModel(sceneId = null).uiState.first { !it.isLoading }

        // story → 第一个章节（index 1）→ 第一个场景（index 1）
        assertEquals(listOf("beat-1", "beat-2"), state.beats.map { it.id })
        assertEquals(PresentationMode.LiveScene, state.presentationMode)
    }

    @Test
    fun `beats keep the order and label coming from the domain`() = runTest(dispatcher) {
        val state = viewModel(sceneId = "scene-1").uiState.first { !it.isLoading }

        // 夹具里节拍是乱序存放的，输出顺序必须是 order 升序
        assertEquals(listOf("beat-1", "beat-2"), state.beats.map { it.id })
        assertEquals(listOf("节拍 1", "节拍 2"), state.beats.map { it.label })
    }

    @Test
    fun `no imported story yields an empty state instead of fake data`() = runTest(dispatcher) {
        val state = viewModel(sceneId = null, imported = null).uiState.first { !it.isLoading }

        assertTrue(state.beats.isEmpty())
        assertEquals(PresentationMode.LiveScene, state.presentationMode)
    }

    @Test
    fun `a story without chapters yields an empty state`() = runTest(dispatcher) {
        val state = viewModel(sceneId = null, imported = fixture(chapters = emptyList()))
            .uiState.first { !it.isLoading }

        assertTrue(state.beats.isEmpty())
    }

    @Test
    fun `a chapter without scenes yields an empty state`() = runTest(dispatcher) {
        val state = viewModel(sceneId = null, imported = fixture(scenes = emptyList()))
            .uiState.first { !it.isLoading }

        assertTrue(state.beats.isEmpty())
    }

    @Test
    fun `a scene without beats yields an empty state`() = runTest(dispatcher) {
        val state = viewModel(sceneId = "scene-1", imported = fixture(beatsByScene = emptyMap()))
            .uiState.first { !it.isLoading }

        assertTrue(state.beats.isEmpty())
    }

    @Test
    fun `every performance event kind is surfaced`() = runTest(dispatcher) {
        val lines = linesOf("scene-1", "beat-1")

        assertEquals(
            setOf("对白", "旁白", "动作", "环境", "环境音", "镜头"),
            lines.map { it.kind }.toSet(),
        )
    }

    @Test
    fun `the utterance speaker wins over the dialogue character`() = runTest(dispatcher) {
        val line = linesOf("scene-1", "beat-1").first { it.kind == "对白" }

        assertEquals("乙", line.speaker)
    }

    @Test
    fun `the dialogue text is carried unchanged`() = runTest(dispatcher) {
        val line = linesOf("scene-1", "beat-1").first { it.kind == "对白" }

        assertEquals("你还没走？", line.text)
    }

    @Test
    fun `voice label stays null while no character carries a voice profile`() = runTest(dispatcher) {
        // 当前导入链路不产生 VoiceProfile（AI 不提供音色），这里锁定的是「暂无音色」而不是兜底值
        assertNull(linesOf("scene-1", "beat-1").first { it.kind == "对白" }.voiceLabel)
    }

    @Test
    fun `an event without override inherits the scene mode`() = runTest(dispatcher) {
        val line = linesOf("scene-1", "beat-1").first { it.id == "n1" }

        assertEquals(PresentationMode.LiveScene, line.presentationMode)
    }

    @Test
    fun `an event override replaces the scene mode`() = runTest(dispatcher) {
        val line = linesOf("scene-1", "beat-1").first { it.id == "n-override" }

        assertEquals(PresentationMode.PhoneCall, line.presentationMode)
    }

    @Test
    fun `an override equal to the scene mode keeps that mode`() = runTest(dispatcher) {
        val lines = linesOf("scene-2", "beat-scene2")

        assertEquals(PresentationMode.InstantMessaging, lines.first { it.id == "x1" }.presentationMode)
        assertEquals(PresentationMode.InstantMessaging, lines.first { it.id == "x2" }.presentationMode)
    }

    // ---------- 播放 ----------

    @Test
    fun `playback starts idle with the timeline duration`() = runTest(dispatcher) {
        val vm = playbackViewModel()
        advanceUntilIdle()

        val state = vm.playbackState.value
        assertEquals(PlaybackStatus.Idle, state.status)
        assertEquals(0L, state.positionMillis)
        assertEquals(3_500L, state.durationMillis)
        assertNull(state.currentEventId)
    }

    @Test
    fun `play starts playing and locates the first event`() = runTest(dispatcher) {
        val vm = playbackViewModel()
        advanceUntilIdle()

        vm.play()

        val state = vm.playbackState.value
        assertEquals(PlaybackStatus.Playing, state.status)
        assertEquals(0L, state.positionMillis)
        assertEquals("pb-1", state.currentBeatId)
        assertEquals("p1", state.currentEventId)
    }

    @Test
    fun `pause keeps the position`() = runTest(dispatcher) {
        val vm = playbackViewModel()
        advanceUntilIdle()
        vm.play()
        vm.seekTo(1_000L)

        vm.pause()

        val state = vm.playbackState.value
        assertEquals(PlaybackStatus.Paused, state.status)
        assertEquals(1_000L, state.positionMillis)
    }

    @Test
    fun `resume continues from where it paused`() = runTest(dispatcher) {
        val vm = playbackViewModel()
        advanceUntilIdle()
        vm.play()
        vm.seekTo(1_000L)
        vm.pause()

        vm.play()

        val state = vm.playbackState.value
        assertEquals(PlaybackStatus.Playing, state.status)
        assertEquals(1_000L, state.positionMillis)
    }

    @Test
    fun `reset returns to the initial position and clears the cursor`() = runTest(dispatcher) {
        val vm = playbackViewModel()
        advanceUntilIdle()
        vm.play()
        vm.seekTo(2_000L)

        vm.reset()

        val state = vm.playbackState.value
        assertEquals(PlaybackStatus.Idle, state.status)
        assertEquals(0L, state.positionMillis)
        assertEquals(3_500L, state.durationMillis)
        assertNull(state.currentBeatId)
        assertNull(state.currentEventId)
    }

    @Test
    fun `seek relocates the current event immediately`() = runTest(dispatcher) {
        val vm = playbackViewModel()
        advanceUntilIdle()

        vm.seekTo(500L)
        assertEquals("p1", vm.playbackState.value.currentEventId)

        vm.seekTo(2_500L)
        assertEquals("p2", vm.playbackState.value.currentEventId)

        vm.seekTo(3_200L)
        assertEquals("pb-2", vm.playbackState.value.currentBeatId)
        assertEquals("p4", vm.playbackState.value.currentEventId)
    }

    @Test
    fun `seek is clamped to the timeline range`() = runTest(dispatcher) {
        val vm = playbackViewModel()
        advanceUntilIdle()

        vm.seekTo(-100L)
        assertEquals(0L, vm.playbackState.value.positionMillis)

        vm.seekTo(99_000L)
        assertEquals(3_500L, vm.playbackState.value.positionMillis)
    }

    @Test
    fun `a gap between events has no current event`() = runTest(dispatcher) {
        val vm = playbackViewModel()
        advanceUntilIdle()

        vm.seekTo(1_500L)

        val state = vm.playbackState.value
        assertNull(state.currentBeatId)
        assertNull(state.currentEventId)
    }

    @Test
    fun `concurrent events at the same offset resolve to the last one`() = runTest(dispatcher) {
        val vm = playbackViewModel()
        advanceUntilIdle()

        vm.seekTo(3_200L)

        assertEquals("p4", vm.playbackState.value.currentEventId)
    }

    @Test
    fun `an empty story has nothing to play`() = runTest(dispatcher) {
        val vm = viewModel(sceneId = null, imported = null)
        advanceUntilIdle()

        vm.play()

        val state = vm.playbackState.value
        assertEquals(0L, state.durationMillis)
        assertEquals(PlaybackStatus.Idle, state.status)
        assertEquals(0L, state.positionMillis)
        assertNull(state.currentEventId)
    }

    @Test
    fun `playing advances the position over time`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = playbackViewModel()
        advanceUntilIdle()

        vm.play()
        advanceTimeBy(1_500L)
        runCurrent()

        val state = vm.playbackState.value
        assertEquals(PlaybackStatus.Playing, state.status)
        assertEquals(1_500L, state.positionMillis)
        // 此时正好落在空档里，游标应当为空
        assertNull(state.currentEventId)
    }

    @Test
    fun `reaching the end completes playback and stops advancing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = playbackViewModel()
        advanceUntilIdle()

        vm.play()
        advanceUntilIdle()

        assertEquals(PlaybackStatus.Completed, vm.playbackState.value.status)
        assertEquals(3_500L, vm.playbackState.value.positionMillis)

        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(PlaybackStatus.Completed, vm.playbackState.value.status)
        assertEquals(3_500L, vm.playbackState.value.positionMillis)
    }

    @Test
    fun `pausing stops the time advancement`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = playbackViewModel()
        advanceUntilIdle()

        vm.play()
        advanceTimeBy(500L)
        runCurrent()
        val beforePause = vm.playbackState.value.positionMillis

        vm.pause()
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(PlaybackStatus.Paused, vm.playbackState.value.status)
        assertEquals(beforePause, vm.playbackState.value.positionMillis)
    }

    @Test
    fun `resetting stops the time advancement`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = playbackViewModel()
        advanceUntilIdle()

        vm.play()
        advanceTimeBy(500L)
        runCurrent()

        vm.reset()
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(PlaybackStatus.Idle, vm.playbackState.value.status)
        assertEquals(0L, vm.playbackState.value.positionMillis)
    }

    @Test
    fun `completing playback keeps the last event as the current one`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = playbackViewModel()
        advanceUntilIdle()

        vm.play()
        advanceUntilIdle()

        val state = vm.playbackState.value
        assertEquals(PlaybackStatus.Completed, state.status)
        assertEquals(3_500L, state.positionMillis)
        // Completed 与「位置正好落在最后一个事件末端」并存：这是 Phase 6B 的既定语义
        assertEquals("pb-2", state.currentBeatId)
        assertEquals("p4", state.currentEventId)
    }

    @Test
    fun `seeking to the very end locates the last event without completing`() = runTest(dispatcher) {
        val vm = playbackViewModel()
        advanceUntilIdle()

        vm.seekTo(3_500L)

        val state = vm.playbackState.value
        // seek 不是播放：位置可以落在末端，但状态由播放（advance）驱动
        assertEquals(PlaybackStatus.Idle, state.status)
        assertEquals(3_500L, state.positionMillis)
        assertEquals("pb-2", state.currentBeatId)
        assertEquals("p4", state.currentEventId)
    }

    @Test
    fun `seeking beyond the end is clamped before locating`() = runTest(dispatcher) {
        val vm = playbackViewModel()
        advanceUntilIdle()

        vm.seekTo(99_000L)

        val state = vm.playbackState.value
        assertEquals(3_500L, state.positionMillis)
        assertEquals("p4", state.currentEventId)
    }

    @Test
    fun `a negative seek locates the first event`() = runTest(dispatcher) {
        val vm = playbackViewModel()
        advanceUntilIdle()

        vm.seekTo(-100L)

        val state = vm.playbackState.value
        assertEquals(0L, state.positionMillis)
        assertEquals("pb-1", state.currentBeatId)
        assertEquals("p1", state.currentEventId)
    }

    @Test
    fun `pausing keeps the current event`() = runTest(dispatcher) {
        val vm = playbackViewModel()
        advanceUntilIdle()
        vm.play()
        vm.seekTo(500L)

        vm.pause()

        val state = vm.playbackState.value
        assertEquals(PlaybackStatus.Paused, state.status)
        assertEquals(500L, state.positionMillis)
        assertEquals("p1", state.currentEventId)
    }

    @Test
    fun `the current event follows the advancing position`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = playbackViewModel()
        advanceUntilIdle()

        vm.play()
        advanceTimeBy(500L)
        runCurrent()
        assertEquals("p1", vm.playbackState.value.currentEventId)

        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals("p2", vm.playbackState.value.currentEventId)
    }

    private suspend fun linesOf(sceneId: String, beatId: String): List<PerformanceLine> =
        viewModel(sceneId = sceneId).uiState.first { !it.isLoading }
            .beats.first { it.id == beatId }
            .lines

    private fun viewModel(
        sceneId: String? = null,
        imported: ImportedStory? = fixture(),
    ): StoryPlayViewModel {
        val store = StoryContentStore(imported)
        return StoryPlayViewModel(
            sceneId = sceneId,
            performanceRepository = InMemoryPerformanceRepository(store),
            characterRepository = InMemoryCharacterRepository(store),
            storyRepository = InMemoryStoryRepository(store),
        )
    }

    private fun fixture(
        chapters: List<Chapter> = defaultChapters(),
        scenes: List<Scene> = defaultScenes(),
        beatsByScene: Map<String, List<Beat>> = defaultBeats(),
    ): ImportedStory = ImportedStory(
        story = Story(id = "story-1", title = "作品", author = "作者", synopsis = ""),
        chapters = chapters,
        content = StoryContent(
            characters = listOf(
                Character(id = "char-甲", storyId = "story-1", name = "甲", description = ""),
                Character(id = "char-乙", storyId = "story-1", name = "乙", description = ""),
            ),
            scenes = scenes,
            beatsByScene = beatsByScene,
        ),
    )

    private fun defaultChapters(): List<Chapter> = listOf(
        Chapter(id = "chapter-2", storyId = "story-1", index = 2, title = "第二章"),
        Chapter(id = "chapter-1", storyId = "story-1", index = 1, title = "第一章"),
    )

    private fun defaultScenes(): List<Scene> = listOf(
        Scene(
            id = "scene-2",
            chapterId = "chapter-1",
            index = 2,
            title = "聊天窗口",
            presentationMode = PresentationMode.InstantMessaging,
            presentationEvidence = PresentationEvidence(text = "她拿出手机发消息"),
        ),
        Scene(
            id = "scene-1",
            chapterId = "chapter-1",
            index = 1,
            title = "教室",
            presentationMode = PresentationMode.LiveScene,
        ),
    )

    private fun defaultBeats(): Map<String, List<Beat>> = mapOf(
        "scene-1" to listOf(
            Beat(id = "beat-2", order = 2, events = listOf(narration("n2", 0L))),
            Beat(
                id = "beat-1",
                order = 1,
                events = listOf(
                    dialogueEvent(),
                    narration("n1", 0L),
                    ActionEvent(
                        id = "a1",
                        timing = Timing(startOffsetMillis = 0L),
                        action = Action(characterId = "char-甲", description = "推门"),
                    ),
                    EnvironmentEvent(
                        id = "e1",
                        timing = Timing(startOffsetMillis = 0L),
                        environment = Environment(description = "夕阳", location = "教室"),
                    ),
                    SoundEvent(id = "s1", timing = Timing(startOffsetMillis = 0L), description = "蝉鸣"),
                    CameraEvent(id = "c1", timing = Timing(startOffsetMillis = 0L), description = "拉远"),
                    narration("n-override", 0L, PresentationMode.PhoneCall),
                ),
            ),
        ),
        "scene-2" to listOf(
            Beat(
                id = "beat-scene2",
                order = 1,
                events = listOf(
                    narration("x1", 0L),
                    narration("x2", 0L, PresentationMode.InstantMessaging),
                ),
            ),
        ),
    )

    private fun playbackViewModel(): StoryPlayViewModel =
        viewModel(sceneId = PLAYBACK_SCENE_ID, imported = playbackFixture())

    /**
     * 一条可预测的时间轴：
     * - beat pb-1：p1(0..1000)、p2(2000..3000) → 1s 空档，节拍末端 3000；
     * - beat pb-2：p3/p4 从 3000 起，同起点并发 → 节拍末端 3500。
     */
    private fun playbackFixture(): ImportedStory = fixture(
        scenes = listOf(
            Scene(
                id = PLAYBACK_SCENE_ID,
                chapterId = "chapter-1",
                index = 3,
                title = "天台",
                presentationMode = PresentationMode.LiveScene,
            ),
        ),
        beatsByScene = mapOf(
            PLAYBACK_SCENE_ID to listOf(
                Beat(
                    id = "pb-1",
                    order = 1,
                    events = listOf(
                        timedNarration("p1", startOffsetMillis = 0L, durationMillis = 1_000L),
                        timedNarration("p2", startOffsetMillis = 2_000L, durationMillis = 1_000L),
                    ),
                ),
                Beat(
                    id = "pb-2",
                    order = 2,
                    events = listOf(
                        timedNarration("p3", startOffsetMillis = 0L, durationMillis = 500L),
                        timedNarration("p4", startOffsetMillis = 0L, durationMillis = 500L),
                    ),
                ),
            ),
        ),
    )

    private fun timedNarration(
        id: String,
        startOffsetMillis: Long,
        durationMillis: Long,
    ): PerformanceEvent = NarrationEvent(
        id = id,
        timing = Timing(
            startOffsetMillis = startOffsetMillis,
            durationMillis = durationMillis,
            durationSource = DurationSource.Manual,
        ),
        narration = Narration(text = id),
    )

    private fun dialogueEvent(): PerformanceEvent = DialogueEvent(
        id = "d1",
        timing = Timing(startOffsetMillis = 0L),
        dialogue = Dialogue(characterId = "char-甲", text = "你还没走？"),
        utterance = Utterance(speakerId = "char-乙", text = "你还没走？"),
    )

    private fun narration(
        id: String,
        startOffsetMillis: Long,
        presentationOverride: PresentationMode? = null,
    ): PerformanceEvent = NarrationEvent(
        id = id,
        timing = Timing(startOffsetMillis = startOffsetMillis),
        narration = Narration(text = id),
        presentationOverride = presentationOverride,
    )

    private companion object {
        const val PLAYBACK_SCENE_ID = "scene-play"
    }
}
