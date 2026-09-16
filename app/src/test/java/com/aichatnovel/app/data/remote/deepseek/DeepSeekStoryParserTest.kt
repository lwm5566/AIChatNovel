package com.aichatnovel.app.data.remote.deepseek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekStoryParserTest {

    private val parser = DeepSeekStoryParser()

    private fun envelope(content: String?) = DeepSeekChatResponse(
        id = "test",
        model = "deepseek-chat",
        choices = listOf(
            DeepSeekChoice(
                index = 0,
                message = content?.let { DeepSeekMessage(role = "assistant", content = it) },
                finishReason = "stop",
            ),
        ),
    )

    private val validJson = """
        {
          "schemaVersion": "1.0",
          "chapterId": "chapter-1",
          "characters": [{ "tempId": "c1", "name": "林晚" }],
          "scenes": [
            { "tempId": "s1", "title": "房间", "participants": ["c1"], "beats": [] }
          ]
        }
    """.trimIndent()

    @Test
    fun `valid json content is parsed into dto`() {
        val result = parser.parse(envelope(validJson))

        val success = result as DeepSeekStoryParseResult.Success
        assertEquals("1.0", success.dto.schemaVersion)
        assertEquals("chapter-1", success.dto.chapterId)
        assertEquals(1, success.dto.characters.size)
        assertEquals(1, success.dto.scenes.size)
        assertEquals(validJson, success.content)
    }

    @Test
    fun `markdown wrapped content is rejected instead of silently stripped`() {
        val fenced = "```json\n$validJson\n```"

        val result = parser.parse(envelope(fenced))

        val failure = result as DeepSeekStoryParseResult.Failure
        assertEquals(DeepSeekStoryParseResult.Reason.MARKDOWN_WRAPPED, failure.reason)
    }

    @Test
    fun `non json prose is rejected`() {
        val result = parser.parse(envelope("好的，这是解析结果：{ }"))

        val failure = result as DeepSeekStoryParseResult.Failure
        assertEquals(DeepSeekStoryParseResult.Reason.NOT_JSON_OBJECT, failure.reason)
    }

    @Test
    fun `json that does not match the dto contract is rejected`() {
        val missingChapterId = """{ "schemaVersion": "1.0", "characters": [], "scenes": [] }"""

        val result = parser.parse(envelope(missingChapterId))

        val failure = result as DeepSeekStoryParseResult.Failure
        assertEquals(DeepSeekStoryParseResult.Reason.INVALID_JSON, failure.reason)
    }

    @Test
    fun `empty content is rejected`() {
        assertEquals(
            DeepSeekStoryParseResult.Reason.EMPTY_RESPONSE,
            (parser.parse(envelope("   ")) as DeepSeekStoryParseResult.Failure).reason,
        )
        assertEquals(
            DeepSeekStoryParseResult.Reason.EMPTY_RESPONSE,
            (parser.parse(DeepSeekChatResponse(choices = emptyList())) as DeepSeekStoryParseResult.Failure).reason,
        )
    }

    @Test
    fun `unknown event type falls back to unknown dto which validator can reject`() {
        val json = """
            {
              "schemaVersion": "1.0",
              "chapterId": "chapter-1",
              "characters": [{ "tempId": "c1", "name": "林晚" }],
              "scenes": [
                { "tempId": "s1", "title": "房间", "participants": ["c1"],
                  "beats": [{ "order": 1, "events": [{ "type": "singing", "id": "e1" }] }] }
              ]
            }
        """.trimIndent()

        val result = parser.parse(envelope(json))

        // 解析层不负责判定合法性，交给 ParseValidator
        assertTrue(result is DeepSeekStoryParseResult.Success)
    }
}
