package com.aichatnovel.app.viewmodel

import com.aichatnovel.app.data.repository.ImportedStory
import com.aichatnovel.app.data.repository.InMemoryAudioAssetRepository
import com.aichatnovel.app.data.repository.InMemoryPerformanceRepository
import com.aichatnovel.app.data.repository.InMemoryStoryRepository
import com.aichatnovel.app.data.repository.StoryContentStore
import com.aichatnovel.app.domain.model.AudioAsset
import com.aichatnovel.app.domain.model.AudioFormat
import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.CharacterSceneState
import com.aichatnovel.app.domain.model.NarrationEvent
import com.aichatnovel.app.domain.model.PerformanceEvent
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.SceneSetting
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.domain.model.StoryContent
import com.aichatnovel.app.domain.model.Timing
import com.aichatnovel.app.domain.model.TtsProviderId
import com.aichatnovel.app.repository.AudioAssetRepository
import com.aichatnovel.app.repository.PerformanceRepository
import com.aichatnovel.app.repository.StoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 场景页契约：地点 / 时间 / 呈现方式 / 角色数 / 节拍数，以及**只来自真实音频**的语音状态。
 *
 * 没有 [AudioAsset] 时绝不能显示「已有语音」。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SceneViewModelTest {

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
    fun `scenes carry place time presentation and counts`() = runTest {
        val state = viewModel(chapterId = "chapter-1").uiState.first { !it.isLoading }

        assertEquals("第一章", state.chapterTitle)
        assertEquals(listOf("s1", "s2"), state.scenes.map { it.id })

        val first = state.scenes.first { it.id == "s1" }
        assertEquals("教室", first.location)
        assertEquals("黄昏", first.timeOfDay)
        assertEquals(PresentationMode.LiveScene, first.presentationMode)
        assertEquals(1, first.characterCount)
        assertEquals(2, first.beatCount)
    }

    @Test
    fun `without real assets no scene claims to have audio`() = runTest {
        val state = viewModel(chapterId = "chapter-1").uiState.first { !it.isLoading }

        assertTrue(state.scenes.none { it.hasAudio })
    }

    @Test
    fun `only the scene owning a real asset reports audio`() = runTest {
        val assets = InMemoryAudioAssetRepository(mapOf("e1" to audioAsset("e1")))
        val state = viewModel(chapterId = "chapter-1", audioAssetRepository = assets)
            .uiState.first { !it.isLoading }

        assertTrue(state.scenes.first { it.id == "s1" }.hasAudio)
        assertFalse(state.scenes.first { it.id == "s2" }.hasAudio)
    }

    @Test
    fun `an asset from another scene does not mark this scene as having audio`() = runTest {
        val assets = InMemoryAudioAssetRepository(mapOf("unknown-event" to audioAsset("unknown-event")))
        val state = viewModel(chapterId = "chapter-1", audioAssetRepository = assets)
            .uiState.first { !it.isLoading }

        assertTrue(state.scenes.none { it.hasAudio })
    }

    @Test
    fun `an unknown chapter yields no scenes`() = runTest {
        val state = viewModel(chapterId = "chapter-missing").uiState.first { !it.isLoading }

        assertTrue(state.scenes.isEmpty())
    }

    @Test
    fun `a missing chapter id falls back to the first chapter`() = runTest {
        val state = viewModel(chapterId = null).uiState.first { !it.isLoading }

        assertEquals("第一章", state.chapterTitle)
        assertEquals(listOf("s1", "s2"), state.scenes.map { it.id })
    }

    private fun viewModel(
        chapterId: String?,
        audioAssetRepository: AudioAssetRepository? = null,
    ): SceneViewModel {
        val store = StoryContentStore(fixture())
        return SceneViewModel(
            chapterId = chapterId,
            storyRepository = storyRepository(store),
            performanceRepository = performanceRepository(store),
            audioAssetRepository = audioAssetRepository,
        )
    }

    private fun storyRepository(store: StoryContentStore): StoryRepository = InMemoryStoryRepository(store)

    private fun performanceRepository(store: StoryContentStore): PerformanceRepository =
        InMemoryPerformanceRepository(store)

    private fun audioAsset(eventId: String): AudioAsset = AudioAsset(
        id = "asset-$eventId",
        reference = "/audio/$eventId.mp3",
        durationMillis = 1_200L,
        format = AudioFormat.MP3,
        source = TtsProviderId.AZURE,
    )

    private fun narration(id: String): PerformanceEvent =
        NarrationEvent(id = id, timing = Timing(startOffsetMillis = 0L), narration = com.aichatnovel.app.domain.model.Narration(text = "他开口了"))

    private fun fixture(): ImportedStory = ImportedStory(
        story = Story(id = "story-1", title = "长夜", author = "作者", synopsis = "简介"),
        chapters = listOf(
            Chapter(id = "chapter-1", storyId = "story-1", index = 1, title = "第一章"),
            Chapter(id = "chapter-2", storyId = "story-1", index = 2, title = "第二章"),
        ),
        content = StoryContent(
            scenes = listOf(
                Scene(
                    id = "s1",
                    chapterId = "chapter-1",
                    index = 1,
                    title = "教室",
                    presentationMode = PresentationMode.LiveScene,
                    setting = SceneSetting(location = "教室", timeOfDay = "黄昏"),
                    characters = listOf(CharacterSceneState(characterId = "char-1")),
                ),
                Scene(id = "s2", chapterId = "chapter-1", index = 2, title = "走廊"),
                Scene(id = "s3", chapterId = "chapter-2", index = 1, title = "天台"),
            ),
            beatsByScene = mapOf(
                "s1" to listOf(
                    Beat(id = "beat-1", order = 1, events = listOf(narration("e1"))),
                    Beat(id = "beat-2", order = 2, events = listOf(narration("e2"))),
                ),
            ),
        ),
    )
}
