package com.aichatnovel.app.data.repository

import com.aichatnovel.app.data.parser.StoryParsePipeline
import com.aichatnovel.app.data.remote.deepseek.DeepSeekLogger
import com.aichatnovel.app.data.remote.deepseek.DeepSeekMessage
import com.aichatnovel.app.data.remote.deepseek.DeepSeekStoryParseResult
import com.aichatnovel.app.data.remote.deepseek.DeepSeekStoryParser
import com.aichatnovel.app.data.remote.deepseek.PromptBuilder
import com.aichatnovel.app.repository.AiTextFailure
import com.aichatnovel.app.repository.AiTextProvider
import com.aichatnovel.app.repository.AiTextRequest
import com.aichatnovel.app.repository.AiTextResult
import com.aichatnovel.app.repository.StoryImportFailure
import com.aichatnovel.app.repository.StoryImportRepository
import com.aichatnovel.app.repository.StoryImportRequest
import com.aichatnovel.app.repository.StoryImportResult

/**
 * 真实 DeepSeek 解析实现。
 *
 * 它只做**流程协调**，不实现任何 DTO → Domain 的映射逻辑：
 * 校验与映射一律复用第三阶段的 [StoryParsePipeline]（内部就是 ParseValidator + AiParseMapper）。
 *
 * 数据流：
 * 原文 → PromptBuilder → [AiTextProvider]（DeepSeekTextProvider）→ 模型正文
 *      → DeepSeekStoryParser（正文 → ParseJson → ParseResponseDto）
 *      → StoryParsePipeline（ParseValidator → AiParseMapper）→ StoryContent
 *
 * 依赖 [AiTextProvider] 抽象而非具体客户端：换成别的文本模型不需要改这里。
 * 网络层与 domain 之间始终隔着 DTO，模型服务的任何对象都不会进入 domain。
 */
class RemoteStoryImportRepository(
    private val textProvider: AiTextProvider,
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val storyParser: DeepSeekStoryParser = DeepSeekStoryParser(),
    private val pipeline: StoryParsePipeline = StoryParsePipeline(),
    private val logger: DeepSeekLogger = DeepSeekLogger.NoOp,
) : StoryImportRepository {

    override suspend fun importStory(request: StoryImportRequest): StoryImportResult {
        val messages = promptBuilder.buildMessages(
            chapterId = request.chapterId,
            storyId = request.storyId,
            novelText = request.novelText,
        )
        val aiRequest = AiTextRequest(
            userPrompt = messages.lastOrNull { it.role == DeepSeekMessage.ROLE_USER }?.content.orEmpty(),
            systemPrompt = messages.firstOrNull { it.role == DeepSeekMessage.ROLE_SYSTEM }?.content,
            requireJsonObject = true,
        )

        val parseResult = when (val aiResult = textProvider.complete(aiRequest)) {
            is AiTextResult.Failure -> {
                logger.log(
                    DeepSeekLogger.STAGE_RAW_RESPONSE,
                    "文本 provider 失败 reason=${aiResult.reason} status=${aiResult.httpStatus}",
                )
                return aiResult.toImportFailure()
            }

            is AiTextResult.Success -> storyParser.parse(aiResult.text)
        }

        if (parseResult is DeepSeekStoryParseResult.Failure) {
            return StoryImportResult.Failure(parseResult.reason.toImportFailure(), parseResult.message)
        }

        val parsed = parseResult as DeepSeekStoryParseResult.Success

        // 第三阶段唯一一条校验 + 映射路径，不做任何旁路。
        val pipelineResult = pipeline.parse(parsed.dto, request.novelText)
        logger.log(
            DeepSeekLogger.STAGE_VALIDATION,
            "errors=${pipelineResult.validation.errors.size} warnings=${pipelineResult.validation.warnings.size}",
        )

        val content = pipelineResult.content
        if (content == null) {
            val detail = pipelineResult.validation.errors.joinToString { "${it.code}@${it.path}" }
            logger.log(DeepSeekLogger.STAGE_MAPPING, "映射未执行：校验存在阻断性错误")
            return StoryImportResult.Failure(
                StoryImportFailure.VALIDATION_ERROR,
                "模型输出不符合契约，未生成领域模型：$detail",
                pipelineResult.validation,
            )
        }

        logger.log(
            DeepSeekLogger.STAGE_MAPPING,
            "映射成功 scenes=${content.scenes.size} characters=${content.characters.size} beats=" +
                content.beatsByScene.values.sumOf { it.size },
        )

        return if (pipelineResult.validation.warnings.isEmpty()) {
            StoryImportResult.Success(pipelineResult.validation, content, parsed.dto.schemaVersion)
        } else {
            StoryImportResult.Partial(pipelineResult.validation, content, parsed.dto.schemaVersion)
        }
    }

    private fun AiTextResult.Failure.toImportFailure(): StoryImportResult.Failure {
        val failure = when (reason) {
            AiTextFailure.MISSING_CREDENTIALS -> StoryImportFailure.MISSING_API_KEY
            AiTextFailure.UNAUTHORIZED -> StoryImportFailure.HTTP_ERROR
            AiTextFailure.TIMEOUT -> StoryImportFailure.TIMEOUT
            AiTextFailure.NETWORK -> StoryImportFailure.NETWORK
            AiTextFailure.HTTP_ERROR -> StoryImportFailure.HTTP_ERROR
            AiTextFailure.MALFORMED_RESPONSE -> StoryImportFailure.MALFORMED_ENVELOPE
            AiTextFailure.EMPTY_RESPONSE -> StoryImportFailure.EMPTY_RESPONSE
        }
        return StoryImportResult.Failure(failure, message ?: "文本模型调用失败")
    }

    private fun DeepSeekStoryParseResult.Reason.toImportFailure(): StoryImportFailure = when (this) {
        DeepSeekStoryParseResult.Reason.EMPTY_RESPONSE -> StoryImportFailure.EMPTY_RESPONSE
        DeepSeekStoryParseResult.Reason.MARKDOWN_WRAPPED -> StoryImportFailure.MARKDOWN_RESPONSE
        DeepSeekStoryParseResult.Reason.NOT_JSON_OBJECT -> StoryImportFailure.NOT_JSON_OBJECT
        DeepSeekStoryParseResult.Reason.INVALID_JSON -> StoryImportFailure.INVALID_JSON
    }
}
