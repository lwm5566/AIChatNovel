package com.aichatnovel.app.data.remote.deepseek

import com.aichatnovel.app.data.parser.ParseJson
import com.aichatnovel.app.data.parser.dto.ParseResponseDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 从模型响应里提取正文并解析成 [ParseResponseDto] 的结果。 */
sealed interface DeepSeekStoryParseResult {

    /** [content] 是模型返回的原始 JSON 文本，保留下来便于调试与溯源。 */
    data class Success(val content: String, val dto: ParseResponseDto) : DeepSeekStoryParseResult

    data class Failure(val reason: Reason, val message: String) : DeepSeekStoryParseResult

    enum class Reason {
        EMPTY_RESPONSE,
        MARKDOWN_WRAPPED,
        NOT_JSON_OBJECT,
        INVALID_JSON,
    }
}

/**
 * 「Raw Response → ParseResponseDto」这一步。
 *
 * 严守契约：本策略**不接受** Markdown 包裹，也不会替模型剥离代码围栏——
 * 因为契约要求模型只输出 JSON，偷偷修补会让契约形同虚设。
 */
class DeepSeekStoryParser(
    private val json: Json = ParseJson.instance,
    private val logger: DeepSeekLogger = DeepSeekLogger.NoOp,
) {

    fun parse(response: DeepSeekChatResponse): DeepSeekStoryParseResult {
        val content = response.choices.firstOrNull()?.message?.content
        if (content.isNullOrBlank()) {
            logger.log(DeepSeekLogger.STAGE_JSON_PARSE, "结果：空响应")
            return DeepSeekStoryParseResult.Failure(
                DeepSeekStoryParseResult.Reason.EMPTY_RESPONSE,
                "模型没有返回任何内容（choices 为空或 content 为空）",
            )
        }

        val trimmed = content.trim()
        if (trimmed.contains(MARKDOWN_FENCE) || trimmed.startsWith("`")) {
            logger.log(DeepSeekLogger.STAGE_JSON_PARSE, "结果：Markdown 包裹（拒绝）")
            return DeepSeekStoryParseResult.Failure(
                DeepSeekStoryParseResult.Reason.MARKDOWN_WRAPPED,
                "模型返回了 Markdown / 代码围栏，违反「只输出 JSON」的契约，已拒绝",
            )
        }

        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            logger.log(DeepSeekLogger.STAGE_JSON_PARSE, "结果：不是 JSON 对象")
            return DeepSeekStoryParseResult.Failure(
                DeepSeekStoryParseResult.Reason.NOT_JSON_OBJECT,
                "模型返回的内容不是 JSON 对象",
            )
        }

        val dto = try {
            json.decodeFromString(ParseResponseDto.serializer(), trimmed)
        } catch (e: SerializationException) {
            logger.log(DeepSeekLogger.STAGE_JSON_PARSE, "结果：JSON 解析失败")
            return DeepSeekStoryParseResult.Failure(
                DeepSeekStoryParseResult.Reason.INVALID_JSON,
                "模型返回的 JSON 无法解析为 ParseResponseDto：${e.message}",
            )
        }

        logger.log(
            DeepSeekLogger.STAGE_JSON_PARSE,
            "结果：解析成功 schemaVersion=${dto.schemaVersion} characters=${dto.characters.size} scenes=${dto.scenes.size}",
        )
        return DeepSeekStoryParseResult.Success(content = trimmed, dto = dto)
    }

    private companion object {
        const val MARKDOWN_FENCE = "```"
    }
}
