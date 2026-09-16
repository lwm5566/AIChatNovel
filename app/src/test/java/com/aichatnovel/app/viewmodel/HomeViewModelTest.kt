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
import org.junit.Before
import org.junit.Test

/**
 * 首页状态契约：首页只回答「我有什么作品、规模多大、下一步做什么」。
 *
 * 计数必须来自 Repository 的真实数据，没有导入时保持空状态而不是伪造占位作品。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

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
    fun `without an imported story the home stays empty`() = runTest {
        val state = HomeViewModel(repository(imported = null)).uiState.first { !it.isLoading }

        assertNull(state.story)
        assertEquals(0, state.chapterCount)
        assertEquals(0, state.sceneCount)
    }

    @Test
    fun `the home reports the current story with its chapter and scene counts`() = runTest {
        val state = HomeViewModel(repository(fixture())).uiState.first { !it.isLoading }

        assertEquals("长夜", state.story?.title)
        assertEquals("作者", state.story?.author)
        assertEquals(2, state.chapterCount)
        assertEquals(3, state.sceneCount)
    }

    @Test
    fun `a story without chapters reports zero scenes`() = runTest {
        val state = HomeViewModel(
            repository(fixture(chapters = emptyList(), scenes = emptyList())),
        ).uiState.first { !it.isLoading }

        assertEquals("长夜", state.story?.title)
        assertEquals(0, state.chapterCount)
        assertEquals(0, state.sceneCount)
    }

    private fun repository(imported: ImportedStory?): StoryRepository =
        InMemoryStoryRepository(StoryContentStore(imported))

    private fun fixture(
        chapters: List<Chapter> = listOf(
            Chapter(id = "chapter-1", storyId = "story-1", index = 1, title = "第一章"),
            Chapter(id = "chapter-2", storyId = "story-1", index = 2, title = "第二章"),
        ),
        scenes: List<Scene> = listOf(
            Scene(id = "s1", chapterId = "chapter-1", index = 1, title = "教室"),
            Scene(id = "s2", chapterId = "chapter-1", index = 2, title = "走廊"),
            Scene(id = "s3", chapterId = "chapter-2", index = 1, title = "天台"),
        ),
    ): ImportedStory = ImportedStory(
        story = Story(id = "story-1", title = "长夜", author = "作者", synopsis = "简介"),
        chapters = chapters,
        content = StoryContent(scenes = scenes),
    )
}
