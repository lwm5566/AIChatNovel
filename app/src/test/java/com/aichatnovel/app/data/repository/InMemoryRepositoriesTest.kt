package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Character
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.domain.model.StoryContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三个 InMemory Repository 的读取契约：按外键过滤、按顺序字段排序、按 id 查找。
 *
 * 夹具刻意把 index / order 打乱，并混入其他作品 / 章节的数据，
 * 因此「排序确实发生」「过滤确实生效」都能被区分出来。
 */
class InMemoryRepositoriesTest {

    private val store = StoryContentStore(importedFixture())
    private val storyRepository = InMemoryStoryRepository(store)
    private val characterRepository = InMemoryCharacterRepository(store)
    private val performanceRepository = InMemoryPerformanceRepository(store)

    @Test
    fun `stories expose only the current import`() = runTest {
        assertEquals(listOf("story-1"), storyRepository.observeStories().first().map { it.id })
    }

    @Test
    fun `chapters are filtered by story and sorted by index`() = runTest {
        val chapters = storyRepository.observeChapters("story-1").first()

        assertEquals(listOf("chapter-1", "chapter-2", "chapter-3"), chapters.map { it.id })
        assertEquals(listOf(1, 2, 3), chapters.map { it.index })
    }

    @Test
    fun `chapters never leak from another story`() = runTest {
        val chapters = storyRepository.observeChapters("story-1").first()

        assertTrue(chapters.all { it.storyId == "story-1" })
        assertTrue(chapters.none { it.id == "chapter-other" })
    }

    @Test
    fun `scenes are filtered by chapter and sorted by index`() = runTest {
        val scenes = storyRepository.observeScenes("chapter-1").first()

        assertEquals(listOf("scene-1", "scene-2"), scenes.map { it.id })
        assertEquals(listOf(1, 2), scenes.map { it.index })
    }

    @Test
    fun `scenes never leak from another chapter`() = runTest {
        val scenes = storyRepository.observeScenes("chapter-1").first()

        assertTrue(scenes.all { it.chapterId == "chapter-1" })
        assertTrue(scenes.none { it.id == "scene-other" })
    }

    @Test
    fun `beats are sorted by order`() = runTest {
        val beats = performanceRepository.observeBeats("scene-1").first()

        assertEquals(listOf("beat-1", "beat-2", "beat-3"), beats.map { it.id })
        assertEquals(listOf(1, 2, 3), beats.map { it.order })
    }

    @Test
    fun `beats never leak from another scene`() = runTest {
        val beats = performanceRepository.observeBeats("scene-1").first()

        assertTrue(beats.none { it.id == "beat-other" })
        assertTrue(performanceRepository.observeBeats("scene-without-beats").first().isEmpty())
    }

    @Test
    fun `a single scene is found by id`() = runTest {
        val scene = storyRepository.observeScene("scene-2").first()

        assertEquals("scene-2", scene?.id)
        assertEquals("chapter-1", scene?.chapterId)
    }

    @Test
    fun `an unknown scene id yields null`() = runTest {
        assertNull(storyRepository.observeScene("scene-missing").first())
    }

    @Test
    fun `characters are filtered by story`() = runTest {
        val characters = characterRepository.observeCharacters("story-1").first()

        assertEquals(listOf("char-1"), characters.map { it.id })
        assertTrue(characters.all { it.storyId == "story-1" })
    }

    @Test
    fun `all characters are exposed without filtering`() = runTest {
        val characters = characterRepository.observeAllCharacters().first()

        assertEquals(listOf("char-1", "char-other"), characters.map { it.id })
    }

    private fun importedFixture(): ImportedStory = ImportedStory(
        story = Story(id = "story-1", title = "作品", author = "作者", synopsis = ""),
        chapters = listOf(
            Chapter(id = "chapter-3", storyId = "story-1", index = 3, title = "第三章"),
            Chapter(id = "chapter-1", storyId = "story-1", index = 1, title = "第一章"),
            Chapter(id = "chapter-2", storyId = "story-1", index = 2, title = "第二章"),
            Chapter(id = "chapter-other", storyId = "story-other", index = 1, title = "别的作品"),
        ),
        content = StoryContent(
            characters = listOf(
                Character(id = "char-1", storyId = "story-1", name = "甲", description = ""),
                Character(id = "char-other", storyId = "story-other", name = "乙", description = ""),
            ),
            scenes = listOf(
                Scene(id = "scene-2", chapterId = "chapter-1", index = 2, title = "场景二"),
                Scene(id = "scene-1", chapterId = "chapter-1", index = 1, title = "场景一"),
                Scene(id = "scene-other", chapterId = "chapter-2", index = 1, title = "别的章节"),
            ),
            beatsByScene = mapOf(
                "scene-1" to listOf(
                    Beat(id = "beat-2", order = 2),
                    Beat(id = "beat-3", order = 3),
                    Beat(id = "beat-1", order = 1),
                ),
                "scene-2" to listOf(
                    Beat(id = "beat-other", order = 1),
                ),
            ),
        ),
    )
}
