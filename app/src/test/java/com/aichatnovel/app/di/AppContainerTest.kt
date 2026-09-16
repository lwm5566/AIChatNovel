package com.aichatnovel.app.di

import com.aichatnovel.app.data.remote.deepseek.DeepSeekConfig
import com.aichatnovel.app.repository.StoryImportFailure
import com.aichatnovel.app.repository.StoryImportResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证容器装配与两种模式的接线。
 */
class AppContainerTest {

    @Test
    fun `local sample mode fills the store and the repositories`() = runTest {
        val container = AppContainer()

        assertEquals(StoryImportMode.LOCAL_SAMPLE, container.config.storyImportMode)

        val result = container.importStory()
        assertTrue("期望本地样例导入成功，实际：$result", result is StoryImportResult.Success)

        val stored = container.storyContentStore.content.value
        assertTrue(stored.scenes.isNotEmpty())

        val scenes = container.storyRepository.observeScenes("chapter-1").first()
        assertEquals(1, scenes.size)

        val beats = container.performanceRepository.observeBeats(scenes.single().id).first()
        assertTrue(beats.isNotEmpty())

        val characters = container.characterRepository.observeAllCharacters().first()
        assertEquals(listOf("char-林晚", "char-陆沉"), characters.map { it.id }.sorted())
    }

    @Test
    fun `remote mode without api key fails without touching the store`() = runTest {
        val container = AppContainer(
            config = AppConfig(
                storyImportMode = StoryImportMode.REMOTE_DEEPSEEK,
                deepSeek = DeepSeekConfig(apiKey = null),
            ),
        )

        // 无 Key 时请求层直接短路，不会发起任何网络调用
        val result = container.importStory()

        assertEquals(StoryImportFailure.MISSING_API_KEY, (result as StoryImportResult.Failure).reason)
        assertTrue(container.storyContentStore.content.value.scenes.isEmpty())
    }
}
