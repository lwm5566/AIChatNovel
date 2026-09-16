package com.aichatnovel.app.data.repository

import com.aichatnovel.app.data.parser.StoryParsePipeline
import com.aichatnovel.app.data.remote.deepseek.DeepSeekApiClient
import com.aichatnovel.app.data.remote.deepseek.DeepSeekApiResult
import com.aichatnovel.app.data.remote.deepseek.DeepSeekChatRequest
import com.aichatnovel.app.data.remote.deepseek.DeepSeekConfig
import com.aichatnovel.app.data.remote.deepseek.DeepSeekLogger
import com.aichatnovel.app.data.remote.deepseek.DeepSeekResponseFormat
import com.aichatnovel.app.data.remote.deepseek.DeepSeekStoryParseResult
import com.aichatnovel.app.data.remote.deepseek.DeepSeekStoryParser
import com.aichatnovel.app.data.remote.deepseek.PromptBuilder
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
 * 原文 → PromptBuilder → DeepSeekApiClient → 原始响应 → DeepSeekStoryParser（ParseJson → ParseResponseDto）
 *      → StoryParsePipeline（ParseValidator → AiParseMapper）→ StoryContent
 *
 * 网络层与 domain 之间始终隔着 DTO，DeepSeek 的任何对象都不会进入 domain。
 */
class RemoteStoryImportRepository(
    private val apiClient: DeepSeekApiClient,
    private val config: DeepSeekConfig,
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val storyParser: DeepSeekStoryParser = DeepSeekStoryParser(),
    private val pipeline: StoryParsePipeline = StoryParsePipeline(),
    private val logger: DeepSeekLogger = DeepSeekLogger.NoOp,
) : StoryImportRepository {

    override suspend fun importStory(request: StoryImportRequest): StoryImportResult {
        val apiRequest = DeepSeekChatRequest(
            model = config.model,
            messages = promptBuilder.buildMessages(
                chapterId = request.chapterId,
                storyId = request.storyId,
                novelText = request.novelText,
            ),
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            responseFormat = DeepSeekResponseFormat.JSON_OBJECT,
        )

        val parseResult = when (val apiResult = apiClient.completeChat(apiRequest)) {
            is DeepSeekApiResult.MissingApiKey -> return StoryImportResult.Failure(
                StoryImportFailure.MISSING_API_KEY,
                "未配置 DeepSeek API Key，未发起请求",
            )

            is DeepSeekApiResult.Timeout -> return StoryImportResult.Failure(
                StoryImportFailure.TIMEOUT,
                "请求 DeepSeek 超时：${apiResult.message}",
            )

            is DeepSeekApiResult.NetworkError -> return StoryImportResult.Failure(
                StoryImportFailure.NETWORK,
                "无法连接 DeepSeek：${apiResult.message}",
            )

            is DeepSeekApiResult.HttpError -> {
                logger.log(
                    DeepSeekLogger.STAGE_RAW_RESPONSE,
                    "HTTP 错误状态码=${apiResult.statusCode} body=${apiResult.body.orEmpty()}",
                )
                return StoryImportResult.Failure(
                    StoryImportFailure.HTTP_ERROR,
                    "DeepSeek 返回 HTTP ${apiResult.statusCode}",
                )
            }

            is DeepSeekApiResult.MalformedEnvelope -> return StoryImportResult.Failure(
                StoryImportFailure.MALFORMED_ENVELOPE,
                apiResult.message,
            )

            is DeepSeekApiResult.Success -> storyParser.parse(apiResult.response)
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

    private fun DeepSeekStoryParseResult.Reason.toImportFailure(): StoryImportFailure = when (this) {
        DeepSeekStoryParseResult.Reason.EMPTY_RESPONSE -> StoryImportFailure.EMPTY_RESPONSE
        DeepSeekStoryParseResult.Reason.MARKDOWN_WRAPPED -> StoryImportFailure.MARKDOWN_RESPONSE
        DeepSeekStoryParseResult.Reason.NOT_JSON_OBJECT -> StoryImportFailure.NOT_JSON_OBJECT
        DeepSeekStoryParseResult.Reason.INVALID_JSON -> StoryImportFailure.INVALID_JSON
    }
}
