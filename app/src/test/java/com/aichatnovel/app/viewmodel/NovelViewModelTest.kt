package com.aichatnovel.app.viewmodel

import com.aichatnovel.app.data.repository.ImportedStory
import com.aichatnovel.app.data.repository.InMemoryStoryRepository
import com.aichatnovel.app.data.repository.StoryContentStore
import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.domain.model.StoryContent
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 作品页契约：作品信息 + 每个章节的场景数量。
 *
 * 章节顺序沿用 Repository 的既有顺序（按 chapter.index 升序），ViewModel 不做二次排序；
 * 场景数量必须与内容一致，不得推断。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NovelViewModelTest {

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
    fun `without an imported story the novel page reports an empty state`() = runTest {
        val state = NovelViewModel(repository(imported = null)).uiState.first { !it.isLoading }

        assertNull(state.story)
        assertTrue(state.chapters.isEmpty())
    }

    @Test
    fun `chapters are listed in repository order with their scene counts`() = runTest {
        val state = NovelViewModel(repository(fixture())).uiState.first { !it.isLoading }

        // Repository 按 chapter.index 升序返回，ViewModel 不二次排序。
        assertEquals(listOf("chapter-1", "chapter-2"), state.chapters.map { it.id })
        assertEquals(listOf(2, 1), state.chapters.map { it.sceneCount })
    }

    @Test
    fun `a chapter without scenes reports zero`() = runTest {
        val state = NovelViewModel(
            repository(fixture(scenes = listOf(scene("s1", "chapter-1", 1)))),
        ).uiState.first { !it.isLoading }

        assertEquals(0, state.chapters.first { it.id == "chapter-2" }.sceneCount)
        assertEquals(1, state.chapters.first { it.id == "chapter-1" }.sceneCount)
    }

    @Test
    fun `a chapter keeps its index and title`() = runTest {
        val state = NovelViewModel(repository(fixture())).uiState.first { !it.isLoading }

        val chapter = state.chapters.first { it.id == "chapter-1" }
        assertEquals(1, chapter.index)
        assertEquals("第一章", chapter.title)
    }

    private fun repository(imported: ImportedStory?): StoryRepository =
        InMemoryStoryRepository(StoryContentStore(imported))

    private fun scene(id: String, chapterId: String, index: Int): Scene =
        Scene(id = id, chapterId = chapterId, index = index, title = "场景 $id")

    private fun fixture(
        scenes: List<Scene> = listOf(
            scene("s1", "chapter-1", 1),
            scene("s2", "chapter-1", 2),
            scene("s3", "chapter-2", 1),
        ),
    ): ImportedStory = ImportedStory(
        story = Story(id = "story-1", title = "长夜", author = "作者", synopsis = "简介"),
        chapters = listOf(
            Chapter(id = "chapter-2", storyId = "story-1", index = 2, title = "第二章"),
            Chapter(id = "chapter-1", storyId = "story-1", index = 1, title = "第一章"),
        ),
        content = StoryContent(scenes = scenes),
    )
}