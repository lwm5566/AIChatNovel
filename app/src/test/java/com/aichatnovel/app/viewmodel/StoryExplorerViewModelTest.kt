package com.aichatnovel.app.viewmodel

import com.aichatnovel.app.domain.model.Action
import com.aichatnovel.app.domain.model.ActionEvent
import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.CameraEvent
import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Character
import com.aichatnovel.app.domain.model.CharacterSceneState
import com.aichatnovel.app.domain.model.Dialogue
import com.aichatnovel.app.domain.model.DialogueEvent
import com.aichatnovel.app.domain.model.DurationSource
import com.aichatnovel.app.domain.model.Environment
import com.aichatnovel.app.domain.model.EnvironmentEvent
import com.aichatnovel.app.domain.model.Narration
import com.aichatnovel.app.domain.model.NarrationEvent
import com.aichatnovel.app.domain.model.PerformanceEvent
import com.aichatnovel.app.domain.model.PresentationEvidence
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.SceneSetting
import com.aichatnovel.app.domain.model.SoundEvent
import com.aichatnovel.app.domain.model.SourceSpan
import com.aichatnovel.app.domain.model.SpeechParams
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.domain.model.Timeline
import com.aichatnovel.app.domain.model.Timing
import com.aichatnovel.app.domain.model.Utterance
import com.aichatnovel.app.repository.CharacterRepository
import com.aichatnovel.app.repository.PerformanceRepository
import com.aichatnovel.app.repository.StoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 解析结果页的展示树：Story → Chapter → Scene → Beat → PerformanceEvent。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryExplorerViewModelTest {

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
    fun `tree exposes story chapter scene beats and events`() = runTest(dispatcher) {
        val state = viewModel().uiState.first { !it.isLoading }

        assertEquals("星海回声", state.story?.title)
        assertEquals(1, state.chapters.size)

        val chapter = state.chapters.single()
        assertEquals("第一章 启程", chapter.title)
        assertEquals(listOf("chapter-1-s1", "chapter-1-s2"), chapter.scenes.map { it.id })

        val scene = chapter.scenes.first()
        assertEquals("黄昏的教室", scene.title)
        assertEquals(listOf("地点：教室", "时间：黄昏", "天气：晴", "光线：暖光"), scene.settingLines)
        assertEquals(listOf("林晚", "陆沉"), scene.participants)
        assertEquals("5.0s", scene.totalDurationLabel)

        val beat = scene.beats.single()
        assertEquals(1, beat.order)
        assertEquals(6, beat.events.size)
    }

    @Test
    fun `presentation mode stays exactly as declared by the domain`() = runTest(dispatcher) {
        val scenes = viewModel().uiState.first { !it.isLoading }.chapters.single().scenes

        val live = scenes.first { it.id == "chapter-1-s1" }
        assertEquals(PresentationMode.LiveScene, live.presentationMode)
        assertNull(live.evidence)

        val messaging = scenes.first { it.id == "chapter-1-s2" }
        assertEquals(PresentationMode.InstantMessaging, messaging.presentationMode)
        assertEquals("她拿出手机打开微信发消息", messaging.evidence)
    }

    @Test
    fun `all six performance event kinds are surfaced`() = runTest(dispatcher) {
        val events = viewModel().uiState.first { !it.isLoading }
            .chapters.single().scenes.first().beats.single().events

        assertEquals(
            setOf("对白", "旁白", "动作", "环境", "环境音", "镜头"),
            events.map { it.kind }.toSet(),
        )
    }

    @Test
    fun `dialogue event carries speaker emotion style addressee and speech params`() = runTest(dispatcher) {
        val dialogue = dialogueEvent()

        assertEquals("说话者：林晚", dialogue.details.first())
        assertTrue(dialogue.details.contains("情绪：惊讶"))
        assertTrue(dialogue.details.contains("演出情绪：平静"))
        assertTrue(dialogue.details.contains("语气风格：轻声"))
        assertTrue(dialogue.details.contains("对谁：陆沉"))
        assertTrue(dialogue.details.contains("语言：zh"))
        assertTrue(dialogue.details.contains("发声参数：语速=1.05, 音高=1.0, 音量=0.9"))
        assertTrue(dialogue.details.none { it == "内心独白" })
    }

    @Test
    fun `inner monologue and source span are surfaced`() = runTest(dispatcher) {
        val dialogue = dialogueEvent(innerMonologue = true)

        assertTrue(dialogue.details.contains("内心独白"))
        assertEquals("你还没走？", dialogue.sourceSpan?.snippet)
        assertEquals(10, dialogue.sourceSpan?.startOffset)
        assertEquals(18, dialogue.sourceSpan?.endOffset)
        assertEquals("0.0s +1.2s（Estimated）", dialogue.timingLabel)
    }

    @Test
    fun `empty content produces an empty but non failing tree`() = runTest(dispatcher) {
        val viewModel = StoryExplorerViewModel(
            storyRepository = FakeStoryRepository(
                stories = listOf(story()),
                chapters = listOf(chapter()),
                scenes = mapOf("chapter-1" to emptyList()),
            ),
            performanceRepository = FakePerformanceRepository(emptyMap()),
            characterRepository = FakeCharacterRepository(characters()),
        )

        val state = viewModel.uiState.first { !it.isLoading }

        assertFalse(state.isLoading)
        assertEquals("星海回声", state.story?.title)
        assertTrue(state.chapters.single().scenes.isEmpty())
    }

    private fun viewModel(innerMonologue: Boolean = false): StoryExplorerViewModel = StoryExplorerViewModel(
        storyRepository = FakeStoryRepository(
            stories = listOf(story()),
            chapters = listOf(chapter()),
            scenes = mapOf(
                "chapter-1" to listOf(
                    Scene(
                        id = "chapter-1-s1",
                        chapterId = "chapter-1",
                        index = 1,
                        title = "黄昏的教室",
                        presentationMode = PresentationMode.LiveScene,
                        setting = SceneSetting(
                            location = "教室",
                            timeOfDay = "黄昏",
                            weather = "晴",
                            lighting = "暖光",
                        ),
                        timeline = Timeline(totalDurationMillis = 5_000L, durationSource = DurationSource.Estimated),
                        characters = listOf(
                            CharacterSceneState(characterId = "char-林晚"),
                            CharacterSceneState(characterId = "char-陆沉"),
                        ),
                    ),
                    Scene(
                        id = "chapter-1-s2",
                        chapterId = "chapter-1",
                        index = 2,
                        title = "手机上的对话",
                        presentationMode = PresentationMode.InstantMessaging,
                        presentationEvidence = PresentationEvidence(text = "她拿出手机打开微信发消息"),
                    ),
                ),
            ),
        ),
        performanceRepository = FakePerformanceRepository(mapOf("chapter-1-s1" to listOf(beat(innerMonologue)))),
        characterRepository = FakeCharacterRepository(characters()),
    )

    private suspend fun dialogueEvent(innerMonologue: Boolean = false): ExplorerEvent = viewModel(innerMonologue).uiState
        .first { !it.isLoading }
        .chapters.single()
        .scenes.first { it.id == "chapter-1-s1" }
        .beats.single()
        .events
        .first { it.kind == "对白" }
        .also { assertEquals(innerMonologue, it.details.contains("内心独白")) }

    private fun story(): Story = Story(
        id = "story-1",
        title = "星海回声",
        author = "示例作者",
        synopsis = "两个习惯了沉默的人。",
    )

    private fun chapter(): Chapter = Chapter(id = "chapter-1", storyId = "story-1", index = 1, title = "第一章 启程")

    private fun characters(): List<Character> = listOf(
        Character(id = "char-林晚", storyId = "story-1", name = "林晚", description = ""),
        Character(id = "char-陆沉", storyId = "story-1", name = "陆沉", description = ""),
    )

    private fun beat(innerMonologue: Boolean = false): Beat = Beat(
        id = "beat-1",
        order = 1,
        events = listOf(
            DialogueEvent(
                id = "event-dialogue",
                timing = Timing(startOffsetMillis = 0L, durationMillis = 1_200L, durationSource = DurationSource.Estimated),
                dialogue = Dialogue(characterId = "char-林晚", text = "你还没走？", emotion = "惊讶"),
                utterance = Utterance(
                    speakerId = "char-林晚",
                    text = "你还没走？",
                    emotion = "平静",
                    speakingStyle = "轻声",
                    addressee = "char-陆沉",
                    isInnerMonologue = innerMonologue,
                    language = "zh",
                ),
                speech = SpeechParams(rate = 1.05f, pitch = 1.0f, volume = 0.9f),
                sourceSpan = SourceSpan(
                    chapterId = "chapter-1",
                    startOffset = 10,
                    endOffset = 18,
                    snippet = "你还没走？",
                ),
            ),
            narrationEvent(),
            ActionEvent(
                id = "event-action",
                timing = Timing(startOffsetMillis = 1_200L),
                action = Action(characterId = "char-陆沉", description = "推门走进来"),
            ),
            EnvironmentEvent(
                id = "event-environment",
                timing = Timing(startOffsetMillis = 1_400L),
                environment = Environment(description = "夕阳把课桌染成橘色", location = "教室"),
            ),
            SoundEvent(
                id = "event-sound",
                timing = Timing(startOffsetMillis = 1_600L),
                description = "走廊尽头的脚步声",
            ),
            CameraEvent(
                id = "event-camera",
                timing = Timing(startOffsetMillis = 1_800L),
                description = "镜头从窗外缓慢推近",
            ),
        ),
    )

    private fun narrationEvent(): PerformanceEvent = NarrationEvent(
        id = "event-narration",
        timing = Timing(startOffsetMillis = 600L),
        narration = Narration(text = "黄昏的风穿过走廊。"),
    )

    private class FakeStoryRepository(
        private val stories: List<Story>,
        private val chapters: List<Chapter>,
        private val scenes: Map<String, List<Scene>>,
    ) : StoryRepository {

        override fun observeStories(): Flow<List<Story>> = flowOf(stories)

        override fun observeChapters(storyId: String): Flow<List<Chapter>> =
            flowOf(chapters.filter { it.storyId == storyId })

        override fun observeScenes(chapterId: String): Flow<List<Scene>> =
            flowOf(scenes[chapterId].orEmpty())

        override fun observeScene(sceneId: String): Flow<Scene?> =
            flowOf(scenes.values.flatten().firstOrNull { it.id == sceneId })
    }

    private class FakePerformanceRepository(
        private val beats: Map<String, List<Beat>>,
    ) : PerformanceRepository {

        override fun observeBeats(sceneId: String): Flow<List<Beat>> = flowOf(beats[sceneId].orEmpty())
    }

    private class FakeCharacterRepository(
        private val characters: List<Character>,
    ) : CharacterRepository {

        override fun observeCharacters(storyId: String): Flow<List<Character>> = flowOf(characters)

        override fun observeAllCharacters(): Flow<List<Character>> = flowOf(characters)
    }
}
