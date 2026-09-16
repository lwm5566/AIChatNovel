package com.aichatnovel.app.di

import com.aichatnovel.app.data.remote.deepseek.DeepSeekChatResponse
import com.aichatnovel.app.data.remote.deepseek.DeepSeekChoice
import com.aichatnovel.app.data.remote.deepseek.DeepSeekConfig
import com.aichatnovel.app.data.remote.deepseek.DeepSeekMessage
import com.aichatnovel.app.data.repository.SampleStoryData
import com.aichatnovel.app.repository.StoryImportFailure
import com.aichatnovel.app.repository.StoryImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 验证容器装配、两种模式的接线，以及「当前导入」的归属语义（Phase 5B-1）。
 */
class AppContainerTest {

    @Test
    fun `nothing is exposed before the first import`() = runTest {
        val container = AppContainer()

        assertNull(container.storyContentStore.imported.value)
        assertTrue(container.storyRepository.observeStories().first().isEmpty())
        assertTrue(container.storyRepository.observeChapters(SampleStoryData.STORY_ID).first().isEmpty())
    }

    @Test
    fun `local sample mode fills the store and the repositories`() = runTest {
        val container = AppContainer()

        assertEquals(StoryImportMode.LOCAL_SAMPLE, container.config.storyImportMode)

        val result = container.importStory()
        assertTrue("期望本地样例导入成功，实际：$result", result is StoryImportResult.Success)

        val stored = container.storyContentStore.content.value
        assertTrue(stored.scenes.isNotEmpty())

        val scenes = container.storyRepository.observeScenes(SampleStoryData.CHAPTER_ID_1).first()
        assertEquals(1, scenes.size)

        val beats = container.performanceRepository.observeBeats(scenes.single().id).first()
        assertTrue(beats.isNotEmpty())

        val characters = container.characterRepository.observeAllCharacters().first()
        assertEquals(listOf("char-林晚", "char-陆沉"), characters.map { it.id }.sorted())
    }

    @Test
    fun `startup preload goes through the same current-import mechanism`() = runTest {
        val container = AppContainer()

        container.importStory()

        val imported = requireNotNull(container.storyContentStore.imported.value) {
            "启动预载必须写入当前导入快照"
        }
        assertTrue(imported.content.scenes.isNotEmpty())
        assertTrue(imported.chapters.isNotEmpty())
    }

    @Test
    fun `imported story never reuses the static sample metadata`() = runTest {
        val container = AppContainer()
        container.importStory(StoryImportMode.LOCAL_SAMPLE, "任意小说原文")

        val story = container.storyRepository.observeStories().first().single()

        assertEquals("未命名作品", story.title)
        assertEquals("未命名作者", story.author)
        assertNotEquals("星海回声", story.title)
        assertNotEquals("示例作者", story.author)
    }

    @Test
    fun `local sample keeps the fixture ownership and owns both chapters`() = runTest {
        val container = AppContainer()
        container.importStory(StoryImportMode.LOCAL_SAMPLE, "任意小说原文")

        val imported = requireNotNull(container.storyContentStore.imported.value)

        // 本地样例是明确的 sample fixture，保留它自己的归属
        assertEquals(SampleStoryData.STORY_ID, imported.story.id)
        assertEquals(
            listOf(SampleStoryData.CHAPTER_ID_1, SampleStoryData.CHAPTER_ID_2),
            imported.chapters.map { it.id },
        )
        assertTrue(imported.chapters.all { it.storyId == imported.story.id })
        assertEquals(listOf(1, 2), imported.chapters.map { it.index })
    }

    @Test
    fun `story chapters and content always come from the same import`() = runTest {
        val container = AppContainer()
        container.importStory(StoryImportMode.LOCAL_SAMPLE, "任意小说原文")

        val imported = requireNotNull(container.storyContentStore.imported.value)
        val chapterIds = imported.chapters.map { it.id }.toSet()

        assertTrue(imported.content.scenes.isNotEmpty())
        assertTrue(
            "每个场景都必须挂在本次导入的章节下",
            imported.content.scenes.all { it.chapterId in chapterIds },
        )
        assertTrue(
            "每个角色都必须属于本次导入的作品",
            imported.content.characters.all { it.storyId == imported.story.id },
        )
        assertEquals(imported.content, container.storyContentStore.content.value)
    }

    @Test
    fun `explicit mode import runs the shared pipeline and fills the store`() = runTest {
        val container = AppContainer()

        val result = container.importStory(
            StoryImportMode.LOCAL_SAMPLE,
            "用户粘贴的原文（本地样例模式会忽略它）",
        )

        assertTrue("期望导入成功，实际：$result", result is StoryImportResult.Success)

        val stored = container.storyContentStore.content.value
        assertEquals(2, stored.scenes.size)
        assertEquals(listOf("char-林晚", "char-陆沉"), stored.characters.map { it.id }.sorted())
    }

    @Test
    fun `two remote imports never share story or chapter ids`() = runTest {
        MockWebServer().use { server ->
            server.dispatcher = EchoingImportIdDispatcher()
            server.start()

            val container = containerFor(
                server,
                FixedImportIdGenerator(
                    storyIds = listOf("story-A", "story-B"),
                    chapterIds = listOf("chapter-A1", "chapter-B1"),
                ),
            )

            container.importStory(StoryImportMode.REMOTE_DEEPSEEK, "小说 A")
            val first = requireNotNull(container.storyContentStore.imported.value)

            container.importStory(StoryImportMode.REMOTE_DEEPSEEK, "小说 B")
            val second = requireNotNull(container.storyContentStore.imported.value)

            assertEquals("story-A", first.story.id)
            assertEquals(listOf("chapter-A1"), first.chapters.map { it.id })
            assertEquals("story-B", second.story.id)
            assertEquals(listOf("chapter-B1"), second.chapters.map { it.id })

            assertNotEquals(first.story.id, second.story.id)
            assertNotEquals(first.chapters.map { it.id }, second.chapters.map { it.id })

            // 第一次的 content 不会与第二次的 ownership 串
            assertTrue(first.content.characters.all { it.storyId == "story-A" })
            assertTrue(first.content.scenes.all { it.chapterId == "chapter-A1" })
            assertTrue(second.content.characters.all { it.storyId == "story-B" })
            assertTrue(second.content.scenes.all { it.chapterId == "chapter-B1" })

            // 普通导入不再使用 SampleStoryData 的常量作为归属
            assertNotEquals(SampleStoryData.STORY_ID, second.story.id)
            assertNotEquals(listOf(SampleStoryData.CHAPTER_ID_1), second.chapters.map { it.id })
        }
    }

    @Test
    fun `remote import keeps the authoritative ids even when the model returns different ones`() = runTest {
        MockWebServer().use { server ->
            // 模型不守协议：回显了一套完全不同的 id
            server.enqueue(responseFor(modelJson("story-hallucinated", "chapter-hallucinated")))
            server.start()

            val container = containerFor(
                server,
                FixedImportIdGenerator(storyIds = listOf("story-A"), chapterIds = listOf("chapter-A1")),
            )

            val result = container.importStory(StoryImportMode.REMOTE_DEEPSEEK, "小说 A")
            assertTrue("期望成功，实际：$result", result is StoryImportResult.Success)

            val imported = requireNotNull(container.storyContentStore.imported.value)

            // 1) Story 归属由 App 决定
            assertEquals("story-A", imported.story.id)
            assertNotEquals("story-hallucinated", imported.story.id)

            // 2) Chapter 归属由 App 决定
            assertEquals(listOf("chapter-A1"), imported.chapters.map { it.id })
            assertEquals(listOf("story-A"), imported.chapters.map { it.storyId })
            assertEquals(listOf(1), imported.chapters.map { it.index })

            // 3) 所有 character 归到权威 storyId
            assertTrue(imported.content.characters.isNotEmpty())
            assertTrue(imported.content.characters.all { it.storyId == "story-A" })

            // 4) 所有 scene 归到权威 chapterId（无条件归一，不看是否“已经一致”）
            assertTrue(imported.content.scenes.isNotEmpty())
            assertTrue(imported.content.scenes.all { it.chapterId == "chapter-A1" })

            // 5) scene.chapterId 必须属于本次导入的 chapters
            val chapterIds = imported.chapters.map { it.id }.toSet()
            assertTrue(imported.content.scenes.all { it.chapterId in chapterIds })
        }
    }

    @Test
    fun `the previous snapshot stays visible while the next import is still in flight`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                responseFor(modelJson("story-second", "chapter-second"))
                    .setHeadersDelay(400, TimeUnit.MILLISECONDS),
            )
            server.start()

            val container = containerFor(
                server,
                FixedImportIdGenerator(
                    storyIds = listOf("story-first", "story-second"),
                    chapterIds = listOf("chapter-first", "chapter-second"),
                ),
            )

            container.importStory(StoryImportMode.LOCAL_SAMPLE, "第一次：本地样例")
            val before = requireNotNull(container.storyContentStore.imported.value)

            val job = launch(Dispatchers.IO) {
                container.importStory(StoryImportMode.REMOTE_DEEPSEEK, "第二次：远程")
            }
            delay(150)

            // 导入尚未完成：旧快照必须继续可见，不能被提前清空
            assertEquals(before, container.storyContentStore.imported.value)
            assertEquals(before.content, container.storyContentStore.content.value)

            job.join()

            val after = requireNotNull(container.storyContentStore.imported.value)
            assertNotEquals(before, after)
            assertEquals("story-second", after.story.id)
        }
    }

    @Test
    fun `partial import replaces the current snapshot`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                responseFor(modelJson("story-P", "chapter-P", presentationMode = "InstantMessaging")),
            )
            server.start()

            val container = containerFor(
                server,
                FixedImportIdGenerator(
                    storyIds = listOf("story-unused", "story-P"),
                    chapterIds = listOf("chapter-unused", "chapter-P"),
                ),
            )

            container.importStory(StoryImportMode.LOCAL_SAMPLE, "第一次：本地样例")
            val first = requireNotNull(container.storyContentStore.imported.value)

            val result = container.importStory(StoryImportMode.REMOTE_DEEPSEEK, "第二次：远程")

            assertTrue("期望 Partial，实际：$result", result is StoryImportResult.Partial)
            val second = requireNotNull(container.storyContentStore.imported.value)
            assertEquals("story-P", second.story.id)
            assertNotEquals(first.story.id, second.story.id)
        }
    }

    @Test
    fun `failed import leaves previously imported content untouched`() = runTest {
        val container = AppContainer()
        container.importStory(StoryImportMode.LOCAL_SAMPLE, "第一次导入")
        val before = requireNotNull(container.storyContentStore.imported.value)

        val result = container.importStory(StoryImportMode.REMOTE_DEEPSEEK, "第二次导入（无 Key，必定失败）")

        assertEquals(StoryImportFailure.MISSING_API_KEY, (result as StoryImportResult.Failure).reason)
        assertEquals(before, container.storyContentStore.imported.value)
        assertEquals(before.content, container.storyContentStore.content.value)
    }

    @Test
    fun `first import failure leaves no current story behind`() = runTest {
        val container = AppContainer(
            config = AppConfig(
                storyImportMode = StoryImportMode.REMOTE_DEEPSEEK,
                deepSeek = DeepSeekConfig(apiKey = null),
            ),
        )

        val result = container.importStory(StoryImportMode.REMOTE_DEEPSEEK, "任意原文")

        assertEquals(StoryImportFailure.MISSING_API_KEY, (result as StoryImportResult.Failure).reason)
        assertNull(container.storyContentStore.imported.value)
        assertTrue(container.storyRepository.observeStories().first().isEmpty())
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

    // --- helpers -------------------------------------------------------------

    private fun containerFor(server: MockWebServer, ids: ImportIdGenerator): AppContainer = AppContainer(
        config = AppConfig(
            storyImportMode = StoryImportMode.REMOTE_DEEPSEEK,
            deepSeek = DeepSeekConfig(
                baseUrl = server.url("/").toString().trimEnd('/'),
                model = "deepseek-chat",
                apiKey = "test-key-not-real",
            ),
        ),
        importIdGenerator = ids,
    )

    /** 回显 prompt 里给出的 chapterId / storyId，模拟真实模型遵守契约的行为。 */
    private class EchoingImportIdDispatcher : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val body = request.body.readUtf8()
            val chapterId = Regex("chapterId: ([A-Za-z0-9-]+)").find(body)?.groupValues?.get(1).orEmpty()
            val storyId = Regex("storyId: ([A-Za-z0-9-]+)").find(body)?.groupValues?.get(1).orEmpty()
            return responseFor(modelJson(storyId, chapterId))
        }
    }

    private class FixedImportIdGenerator(
        private val storyIds: List<String>,
        private val chapterIds: List<String>,
    ) : ImportIdGenerator {

        private val storyIndex = AtomicInteger(0)
        private val chapterIndex = AtomicInteger(0)

        override fun newStoryId(): String = storyIds[storyIndex.getAndIncrement()]

        override fun newChapterId(): String = chapterIds[chapterIndex.getAndIncrement()]
    }

    private companion object {

        fun responseFor(modelJson: String): MockResponse = MockResponse()
            .setResponseCode(200)
            .setBody(
                Json.encodeToString(
                    DeepSeekChatResponse.serializer(),
                    DeepSeekChatResponse(
                        id = "test",
                        model = "deepseek-chat",
                        choices = listOf(
                            DeepSeekChoice(0, DeepSeekMessage("assistant", modelJson), "stop"),
                        ),
                    ),
                ),
            )

        fun modelJson(
            storyId: String,
            chapterId: String,
            presentationMode: String? = null,
        ): String = buildString {
            append("""{"schemaVersion":"1.0","storyId":"$storyId","chapterId":"$chapterId",""")
            append(""""characters":[{"tempId":"c1","name":"林晚"}],""")
            append(""""scenes":[{"tempId":"s1","title":"房间",""")
            presentationMode?.let { append(""""presentationMode":"$it",""") }
            append(""""participants":["c1"],"beats":[{"id":"b1","order":1,"events":[""")
            append("""{"type":"dialogue","id":"e1","speakerTempId":"c1","text":"放下手机。"}]}]}]}""")
        }
    }
}
