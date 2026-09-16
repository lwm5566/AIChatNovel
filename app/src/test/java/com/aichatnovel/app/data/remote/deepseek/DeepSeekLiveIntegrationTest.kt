package com.aichatnovel.app.data.remote.deepseek

import com.aichatnovel.app.data.ai.DeepSeekTextProvider
import com.aichatnovel.app.data.parser.sample.SampleParseSource
import com.aichatnovel.app.data.parser.sample.SampleParseSources
import com.aichatnovel.app.data.repository.RemoteStoryImportRepository
import com.aichatnovel.app.domain.model.DialogueEvent
import com.aichatnovel.app.domain.model.SourceSpan
import com.aichatnovel.app.repository.StoryImportFailure
import com.aichatnovel.app.repository.StoryImportRequest
import com.aichatnovel.app.repository.StoryImportResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.minutes

/**
 * 真实调用 DeepSeek 的手工验证。
 *
 * 未提供 API Key（环境变量 `DEEPSEEK_API_KEY` 或 `-Ddeepseek.api.key=...`）时**自动跳过**，
 * 因此不会污染常规的 `./gradlew test`。
 *
 * 运行方式：
 * ```
 * DEEPSEEK_API_KEY=sk-xxxx ./gradlew testDebugUnitTest --tests '*DeepSeekLiveIntegrationTest*' -i
 * ```
 *
 * 统计报告会写到 `app/build/deepseek-live-report.txt`。
 */
class DeepSeekLiveIntegrationTest {

    private val apiKey = DeepSeekConfig.apiKeyFromEnvironment()
    private val report = StringBuilder()

    @Before
    fun requireApiKey() {
        Assume.assumeTrue(
            "未提供 DEEPSEEK_API_KEY，跳过真实 DeepSeek 调用",
            !apiKey.isNullOrBlank(),
        )
    }

    @After
    fun writeReport() {
        if (report.isEmpty()) return
        val file = File("build/deepseek-live-report.txt")
        file.parentFile?.mkdirs()
        file.writeText(report.toString())
        println(report.toString())
    }

    @Test
    fun `live deepseek parses both sample chapters`() = runTest(timeout = 10.minutes) {
        val config = DeepSeekConfig(apiKey = apiKey)
        val repository = RemoteStoryImportRepository(
            textProvider = DeepSeekTextProvider(
                apiClient = OkHttpDeepSeekApiClient(config),
                config = config,
            ),
        )

        val chapters = listOf(
            "chapter-1" to SampleParseSources.liveScene,
            "chapter-2" to SampleParseSources.instantMessaging,
        )

        chapters.forEach { (chapterId, source) ->
            verifyChapter(repository, chapterId, source)
        }
    }

    private suspend fun verifyChapter(
        repository: RemoteStoryImportRepository,
        chapterId: String,
        source: SampleParseSource,
    ) {
        val novelText = source.chapterText
        val startedAt = System.currentTimeMillis()
        val result = repository.importStory(
            StoryImportRequest(chapterId = chapterId, storyId = "story-1", novelText = novelText),
        )
        val elapsedMillis = System.currentTimeMillis() - startedAt

        report.appendLine("================ 章节 $chapterId（${source.name}）================")
        report.appendLine("原文长度（字符）: ${novelText.length}")
        report.appendLine("耗时（毫秒）    : $elapsedMillis")
        report.appendLine("API 调用        : ${if (isTransportFailure(result)) "失败" else "成功"}")
        report.appendLine("最终结果        : ${result::class.simpleName}")
        report.appendLine("Validator       : errors=${result.validation.errors.size} warnings=${result.validation.warnings.size}")
        result.validation.issues.forEach { report.appendLine("  - [${it.severity}] ${it.code} @ ${it.path}") }

        val content = when (result) {
            is StoryImportResult.Success -> result.content
            is StoryImportResult.Partial -> result.content
            is StoryImportResult.Failure -> null
        }

        if (content == null) {
            report.appendLine("StoryContent    : 失败（${(result as StoryImportResult.Failure).reason}）")
            report.appendLine("说明            : ${(result as StoryImportResult.Failure).message}")
            report.appendLine()
        } else {
            val events = content.beatsByScene.values.flatten().flatMap { it.events }
            report.appendLine("StoryContent    : 成功（scenes=${content.scenes.size} characters=${content.characters.size} beats=${content.beatsByScene.values.sumOf { it.size }} events=${events.size}）")
            report.appendLine("PresentationMode: ${content.scenes.joinToString { "${it.id}=${it.presentationMode}" }}")
            content.scenes.forEach { scene ->
                report.appendLine("  - ${scene.id} evidence=${scene.presentationEvidence?.text ?: "(无)"}")
            }
            report.appendLine("事件类型分布    : ${events.groupingBy { it::class.simpleName }.eachCount()}")
            report.appendLine("未解析角色引用  : ${events.filterIsInstance<DialogueEvent>().count { it.dialogue.characterId.startsWith("char-unresolved-") }}")

            val spans = events.mapNotNull { it.sourceSpan }
            val correct = spans.count { it.matches(novelText) }
            report.appendLine("SourceSpan      : 总数=${spans.size} 正确=$correct 错误=${spans.size - correct}")
            spans.filterNot { it.matches(novelText) }.forEach { span ->
                report.appendLine("  - 错误 span [${span.startOffset},${span.endOffset}) snippet=「${span.snippet}」")
            }
            report.appendLine()
        }

        // 只断言传输层可用：模型内容是否符合契约，由报告如实记录，不用测试去掩盖
        assertFalse(
            "DeepSeek 调用出现传输层失败：${(result as? StoryImportResult.Failure)?.reason}",
            isTransportFailure(result),
        )
    }

    private fun isTransportFailure(result: StoryImportResult): Boolean =
        result is StoryImportResult.Failure && result.reason in setOf(
            StoryImportFailure.MISSING_API_KEY,
            StoryImportFailure.NETWORK,
            StoryImportFailure.TIMEOUT,
            StoryImportFailure.HTTP_ERROR,
            StoryImportFailure.MALFORMED_ENVELOPE,
        )

    private fun SourceSpan.matches(novelText: String): Boolean =
        startOffset >= 0 && endOffset <= novelText.length && startOffset < endOffset &&
            novelText.substring(startOffset, endOffset) == snippet
}
