package com.aichatnovel.app.ui.storyimport

import com.aichatnovel.app.repository.StoryImportFailure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入失败提示必须面向普通用户：说清发生了什么、下一步怎么办，
 * 并且不把内部枚举名、JSON 结构或 HTTP 细节带进界面。
 */
class ImportFailureTextTest {

    @Test
    fun `every failure reason has a readable sentence`() {
        StoryImportFailure.entries.forEach { reason ->
            val text = importFailureText(reason)

            assertTrue("$reason 缺少文案", text.isNotBlank())
            assertFalse("$reason 的文案泄露了内部编码", text.contains(reason.name))
        }
    }

    @Test
    fun `missing credentials tells the user what is wrong`() {
        val text = importFailureText(StoryImportFailure.MISSING_API_KEY)

        assertTrue(text.contains("凭据"))
    }

    @Test
    fun `network and timeout failures suggest retrying`() {
        assertTrue(importFailureText(StoryImportFailure.NETWORK).contains("重试"))
        assertTrue(importFailureText(StoryImportFailure.TIMEOUT).contains("重试"))
    }

    @Test
    fun `technical failures stay free of json and http detail`() {
        listOf(
            StoryImportFailure.INVALID_JSON,
            StoryImportFailure.NOT_JSON_OBJECT,
            StoryImportFailure.MARKDOWN_RESPONSE,
            StoryImportFailure.MALFORMED_ENVELOPE,
            StoryImportFailure.EMPTY_RESPONSE,
            StoryImportFailure.HTTP_ERROR,
            StoryImportFailure.VALIDATION_ERROR,
        ).forEach { reason ->
            val text = importFailureText(reason)

            assertFalse("$reason 泄露了 HTTP 细节", text.contains("HTTP", ignoreCase = true))
            assertFalse("$reason 泄露了 JSON 结构", text.contains("{"))
            assertFalse("$reason 泄露了 JSON 字样", text.contains("json", ignoreCase = true))
            assertFalse("$reason 泄露了栈信息", text.contains("Exception"))
        }
    }
}
