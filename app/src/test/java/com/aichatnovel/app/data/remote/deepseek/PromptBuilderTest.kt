package com.aichatnovel.app.data.remote.deepseek

import com.aichatnovel.app.data.parser.ParseSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    private val builder = PromptBuilder()

    @Test
    fun `system prompt declares the schema version`() {
        val prompt = builder.systemPrompt()

        assertTrue(prompt.contains("schemaVersion"))
        assertTrue(prompt.contains(ParseSchema.CURRENT))
    }

    @Test
    fun `system prompt demands pure json and forbids markdown`() {
        val prompt = builder.systemPrompt()

        assertTrue(prompt.contains("只输出一个 JSON 对象"))
        assertTrue(prompt.contains("不要使用 Markdown"))
        assertTrue(prompt.contains("不要输出代码围栏"))
        // prompt 自身也不能包含代码围栏，否则会诱导模型输出 Markdown
        assertFalse(prompt.contains("```"))
    }

    @Test
    fun `system prompt forbids inventing resources and tts durations`() {
        val prompt = builder.systemPrompt()

        assertTrue(prompt.contains("不要输出音频 URL"))
        assertTrue(prompt.contains("不要编造精确的 TTS 时长"))
        assertTrue(prompt.contains("不要输出 Android"))
    }

    @Test
    fun `system prompt encodes the presentation mode rules`() {
        val prompt = builder.systemPrompt()

        assertTrue(prompt.contains("LiveScene"))
        assertTrue(prompt.contains("InstantMessaging"))
        assertTrue(prompt.contains("presentationEvidence"))
        assertTrue(prompt.contains("严禁因为"))
        assertTrue(prompt.contains("聊天式呈现只是 UI 表现形式"))
    }

    @Test
    fun `system prompt encodes source span rules`() {
        val prompt = builder.systemPrompt()

        assertTrue(prompt.contains("startOffset"))
        assertTrue(prompt.contains("endOffset"))
        assertTrue(prompt.contains("snippet"))
        assertTrue(prompt.contains("逐字一致"))
    }

    @Test
    fun `system prompt lists exactly the six allowed event types`() {
        val prompt = builder.systemPrompt()

        listOf("dialogue", "narration", "action", "environment", "sound", "camera").forEach { type ->
            assertTrue("缺少事件类型 $type", prompt.contains("\"$type\""))
        }
        assertTrue(prompt.contains("不允许发明新的类型"))
    }

    @Test
    fun `user prompt carries chapter id and the whole novel text`() {
        val novel = "林晚放下手机。"

        val messages = builder.buildMessages(chapterId = "chapter-1", novelText = novel)

        assertEquals(2, messages.size)
        assertEquals(DeepSeekMessage.ROLE_SYSTEM, messages[0].role)
        assertEquals(DeepSeekMessage.ROLE_USER, messages[1].role)

        val user = messages[1].content
        assertTrue(user.contains("chapter-1"))
        assertTrue(user.contains(novel))
        assertTrue(user.contains("原文总长度（字符数）: ${novel.length}"))
    }
}
