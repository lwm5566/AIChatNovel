package com.aichatnovel.app.data.parser

import com.aichatnovel.app.data.parser.dto.ParseResponseDto
import com.aichatnovel.app.data.parser.mapping.AiParseMapper
import com.aichatnovel.app.data.parser.validation.ParseValidator
import com.aichatnovel.app.data.parser.validation.ValidationCode
import com.aichatnovel.app.data.parser.validation.ValidationIssue
import com.aichatnovel.app.data.parser.validation.ValidationResult
import com.aichatnovel.app.data.parser.validation.ValidationSeverity
import com.aichatnovel.app.domain.model.StoryContent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 解析管线结果。[content] 为 null 表示存在阻断性错误，未产出领域模型。
 */
data class ParsePipelineResult(
    val content: StoryContent?,
    val validation: ValidationResult,
)

/**
 * 本地解析管线：AI 响应 JSON → DTO → 校验 → 归一化 → Domain Model。
 *
 * 存在阻断性错误时不进行映射，确保错误的 AI 输出不会污染领域模型；
 * 只有 warning 时继续映射，并把降级说明合并进校验结果。
 */
class StoryParsePipeline(
    private val validator: ParseValidator = ParseValidator(),
    private val mapper: AiParseMapper = AiParseMapper(),
    private val json: Json = ParseJson.instance,
) {

    fun parse(rawJson: String, chapterText: String? = null): ParsePipelineResult {
        val response = try {
            json.decodeFromString(ParseResponseDto.serializer(), rawJson)
        } catch (e: SerializationException) {
            return ParsePipelineResult(
                content = null,
                validation = ValidationResult(
                    listOf(
                        ValidationIssue(
                            ValidationCode.UNPARSEABLE_RESPONSE,
                            ValidationSeverity.ERROR,
                            "$",
                            "AI 响应无法解析：${e.message}",
                        ),
                    ),
                ),
            )
        }

        return parse(response, chapterText)
    }

    /**
     * 已是 DTO 时直接进入校验 + 归一化。
     * 远程链路（DeepSeek 响应已经过一次 JSON 解析）复用它，确保仍然只有一条校验/映射路径。
     */
    fun parse(response: ParseResponseDto, chapterText: String? = null): ParsePipelineResult {
        val validation = validator.validate(response, chapterText)
        if (!validation.isValid) {
            return ParsePipelineResult(content = null, validation = validation)
        }

        val mapping = mapper.map(response)
        return ParsePipelineResult(
            content = mapping.content,
            validation = validation + ValidationResult(mapping.warnings),
        )
    }
}
