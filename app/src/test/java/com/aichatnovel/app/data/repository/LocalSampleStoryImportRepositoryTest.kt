package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.DialogueEvent
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.repository.StoryImportRequest
import com.aichatnovel.app.repository.StoryImportResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSampleStoryImportRepositoryTest {

    private val repository = LocalSampleStoryImportRepository()

    private val request = StoryImportRequest(chapterId = "chapter-1", novelText = "")

    @Test
    fun `imports both bundled chapters into one story content`() = runTest {
        val content = when (val result = repository.importStory(request)) {
            is StoryImportResult.Success -> result.content
            is StoryImportResult.Partial -> result.content
            is StoryImportResult.Failure -> throw AssertionError("导入失败：${result.reason} ${result.message}")
        }

        assertEquals(listOf("char-林晚", "char-陆沉"), content.characters.map { it.id }.sorted())

        assertEquals(2, content.scenes.size)
        assertEquals(
            mapOf("chapter-1" to PresentationMode.LiveScene, "chapter-2" to PresentationMode.InstantMessaging),
            content.scenes.associate { it.chapterId to it.presentationMode },
        )

        assertTrue(content.beatsByScene.containsKey("chapter-1-s1"))
        assertTrue(content.beatsByScene.containsKey("chapter-2-s1"))
    }

    @Test
    fun `characters from both chapters are deduplicated`() = runTest {
        val content = when (val result = repository.importStory(request)) {
            is StoryImportResult.Success -> result.content
            is StoryImportResult.Partial -> result.content
            is StoryImportResult.Failure -> throw AssertionError("导入失败：${result.reason} ${result.message}")
        }

        assertEquals(content.characters.size, content.characters.distinctBy { it.id }.size)
    }

    @Test
    fun `each scene has mapped events`() = runTest {
        val content = when (val result = repository.importStory(request)) {
            is StoryImportResult.Success -> result.content
            is StoryImportResult.Partial -> result.content
            is StoryImportResult.Failure -> throw AssertionError("导入失败：${result.reason} ${result.message}")
        }

        assertTrue(content.beatsByScene.getValue("chapter-1-s1").flatMap { it.events }.isNotEmpty())
        assertTrue(
            content.beatsByScene.getValue("chapter-2-s1")
                .flatMap { it.events }
                .filterIsInstance<DialogueEvent>()
                .size >= 3,
        )
    }
}
