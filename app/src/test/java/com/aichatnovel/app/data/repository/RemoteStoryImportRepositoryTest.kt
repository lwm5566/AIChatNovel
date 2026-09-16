package com.aichatnovel.app.data.repository

import com.aichatnovel.app.data.parser.validation.ValidationCode
import com.aichatnovel.app.data.remote.deepseek.DeepSeekApiClient
import com.aichatnovel.app.data.remote.deepseek.DeepSeekApiResult
import com.aichatnovel.app.data.remote.deepseek.DeepSeekChatRequest
import com.aichatnovel.app.data.remote.deepseek.DeepSeekChatResponse
import com.aichatnovel.app.data.remote.deepseek.DeepSeekChoice
import com.aichatnovel.app.data.remote.deepseek.DeepSeekConfig
import com.aichatnovel.app.data.remote.deepseek.DeepSeekLogger
import com.aichatnovel.app.data.remote.deepseek.DeepSeekMessage
import com.aichatnovel.app.domain.model.DialogueEvent
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.repository.StoryImportFailure
import com.aichatnovel.app.repository.StoryImportRequest
import com.aichatnovel.app.repository.StoryImportResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 使用假 API Client，绝不真实调用 DeepSeek。
 */
class RemoteStoryImportRepositoryTest {

    private val novelText = "林晚放下手机。"

    private class FakeApiClient(var result: DeepSeekApiResult) : DeepSeekApiClient {
        var lastRequest: DeepSeekChatRequest? = null

        override suspend fun completeChat(request: DeepSeekChatRequest): DeepSeekApiResult {
            lastRequest = request
            return result
        }
    }

    private fun envelope(content: String) = DeepSeekApiResult.Success(
        DeepSeekChatResponse(
            id = "test",
            model = "deepseek-chat",
            choices = listOf(
                DeepSeekChoice(0, DeepSeekMessage("assistant", content), "stop"),
            ),
        ),
    )

    private fun modelJson(
        schemaVersion: String = "1.0",
        presentationMode: String? = null,
        evidence: String? = null,
    ): String = buildString {
        append("""{"schemaVersion":"$schemaVersion","storyId":"story-1","chapterId":"chapter-1",""")
        append(""""characters":[{"tempId":"c1","name":"林晚"}],""")
        append(""""scenes":[{"tempId":"s1","title":"房间",""")
        presentationMode?.let { append(""""presentationMode":"$it",""") }
        evidence?.let { append(""""presentationEvidence":{"text":"$it"},""") }
        append(""""participants":["c1"],"beats":[{"id":"b1","order":1,"events":[""")
        append("""{"type":"dialogue","id":"e1","speakerTempId":"c1","text":"放下手机。",""")
        append(""""sourceSpan":{"chapterId":"chapter-1","startOffset":2,"endOffset":6,"snippet":"放下手机"}}""")
        append("""]}]}]}""")
    }

    private fun repository(
        client: DeepSeekApiClient,
        logger: DeepSeekLogger = DeepSeekLogger.NoOp,
    ) = RemoteStoryImportRepository(
        apiClient = client,
        config = DeepSeekConfig(apiKey = "test-key-not-real", model = "deepseek-chat"),
        logger = logger,
    )

    private val request = StoryImportRequest(chapterId = "chapter-1", storyId = "story-1", novelText = "林晚放下手机。")

    @Test
    fun `successful response goes through validator and mapper into domain`() = runTest {
        val client = FakeApiClient(envelope(modelJson()))

        val result = repository(client).importStory(request)

        val success = result as StoryImportResult.Success
        assertEquals("1.0", success.appliedSchemaVersion)
        assertEquals(listOf("char-林晚"), success.content.characters.map { it.id })
        assertEquals(PresentationMode.LiveScene, success.content.scenes.single().presentationMode)
        assertEquals(1, success.content.beatsByScene.getValue("chapter-1-s1").size)

        val dialogue = success.content.beatsByScene.getValue("chapter-1-s1")
            .flatMap { it.events }
            .filterIsInstance<DialogueEvent>()
            .single()
        assertEquals("放下手机。", dialogue.dialogue.text)
        assertEquals("char-林晚", dialogue.dialogue.characterId)
        assertEquals(2, dialogue.sourceSpan?.startOffset)
        assertEquals(6, dialogue.sourceSpan?.endOffset)
    }

    @Test
    fun `instant messaging without evidence becomes partial with degraded mode`() = runTest {
        val client = FakeApiClient(envelope(modelJson(presentationMode = "InstantMessaging")))

        val result = repository(client).importStory(request)

        val partial = result as StoryImportResult.Partial
        assertEquals(PresentationMode.LiveScene, partial.content.scenes.single().presentationMode)

        val codes = partial.validation.warnings.map { it.code }
        assertTrue(codes.contains(ValidationCode.MISSING_PRESENTATION_EVIDENCE))
        assertTrue(codes.contains(ValidationCode.DEGRADED_PRESENTATION_MODE))
        assertTrue(partial.validation.errors.isEmpty())
    }

    @Test
    fun `instant messaging with evidence keeps the mode`() = runTest {
        val client = FakeApiClient(
            envelope(modelJson(presentationMode = "InstantMessaging", evidence = "她拿起手机打开微信")),
        )

        val result = repository(client).importStory(request)

        val content = when (result) {
            is StoryImportResult.Success -> result.content
            is StoryImportResult.Partial -> result.content
            is StoryImportResult.Failure -> throw AssertionError("导入失败：${result.reason}")
        }
        assertEquals(PresentationMode.InstantMessaging, content.scenes.single().presentationMode)
    }

    @Test
    fun `unsupported schema version fails validation without producing domain`() = runTest {
        val client = FakeApiClient(envelope(modelJson(schemaVersion = "9.9")))

        val result = repository(client).importStory(request)

        val failure = result as StoryImportResult.Failure
        assertEquals(StoryImportFailure.VALIDATION_ERROR, failure.reason)
        assertTrue(failure.validation.errors.any { it.code == ValidationCode.UNSUPPORTED_SCHEMA_VERSION })
    }

    @Test
    fun `http error is mapped to failure`() = runTest {
        val client = FakeApiClient(DeepSeekApiResult.HttpError(401, """{"error":{"message":"auth"}}"""))

        val result = repository(client).importStory(request)

        assertEquals(StoryImportFailure.HTTP_ERROR, (result as StoryImportResult.Failure).reason)
    }

    @Test
    fun `timeout and network errors are mapped to failure`() = runTest {
        assertEquals(
            StoryImportFailure.TIMEOUT,
            (
                repository(FakeApiClient(DeepSeekApiResult.Timeout("read timeout")))
                    .importStory(request) as StoryImportResult.Failure
                ).reason,
        )
        assertEquals(
            StoryImportFailure.NETWORK,
            (
                repository(FakeApiClient(DeepSeekApiResult.NetworkError("dns")))
                    .importStory(request) as StoryImportResult.Failure
                ).reason,
        )
    }

    @Test
    fun `missing api key short circuits`() = runTest {
        val result = repository(FakeApiClient(DeepSeekApiResult.MissingApiKey)).importStory(request)

        assertEquals(StoryImportFailure.MISSING_API_KEY, (result as StoryImportResult.Failure).reason)
    }

    @Test
    fun `markdown and empty responses are rejected`() = runTest {
        assertEquals(
            StoryImportFailure.MARKDOWN_RESPONSE,
            (
                repository(FakeApiClient(envelope("```json\n${modelJson()}\n```")))
                    .importStory(request) as StoryImportResult.Failure
                ).reason,
        )
        assertEquals(
            StoryImportFailure.EMPTY_RESPONSE,
            (
                repository(FakeApiClient(envelope("  ")))
                    .importStory(request) as StoryImportResult.Failure
                ).reason,
        )
    }

    @Test
    fun `prompt carries novel text and schema but never the api key`() = runTest {
        val client = FakeApiClient(envelope(modelJson()))

        repository(client).importStory(request)

        val sent = requireNotNull(client.lastRequest)
        assertEquals("deepseek-chat", sent.model)
        assertEquals(2, sent.messages.size)

        val joined = sent.messages.joinToString("\n") { it.content }
        assertTrue(joined.contains(novelText))
        assertTrue(joined.contains("schemaVersion"))
        assertTrue(joined.contains(StoryImportRequest(chapterId = "chapter-1", novelText = "").chapterId))
        assertFalse("请求里不允许出现 API Key", joined.contains("test-key-not-real"))
    }

    @Test
    fun `logger never receives the api key`() = runTest {
        val records = mutableListOf<String>()
        val client = FakeApiClient(DeepSeekApiResult.HttpError(500, "boom"))

        repository(client, DeepSeekLogger { stage, message -> records += "$stage $message" })
            .importStory(request)

        assertTrue(records.isNotEmpty())
        assertTrue(records.none { it.contains("test-key-not-real") })

        // 常量本身也不允许携带凭据形态的字符串
        assertNull(DeepSeekConfig.apiKeyFromEnvironment()?.takeIf { it == "test-key-not-real" })
    }
}
