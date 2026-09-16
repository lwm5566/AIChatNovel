package com.aichatnovel.app.data.parser.dto

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * AI 解析出的演出事件。按 JSON 中的 `type` 字段选择具体子类型。
 *
 * 与 domain 的 PerformanceEvent 一一对应，但字段更宽容（大量可空），
 * 因为 AI 输出不可信，需要由校验层与 Mapper 决定如何归一化。
 */
@Serializable(with = PerformanceEventDtoSerializer::class)
sealed class PerformanceEventDto {
    abstract val id: String
    abstract val sourceSpan: SourceSpanDto?
    abstract val confidence: Double?
    abstract val timing: TimingDto?

    /** 呈现介质覆盖，允许为空（继承所属场景）。取值同 PresentationMode。 */
    abstract val presentationOverride: String?
}

@Serializable
data class DialogueEventDto(
    override val id: String,
    override val sourceSpan: SourceSpanDto? = null,
    override val confidence: Double? = null,
    override val timing: TimingDto? = null,
    override val presentationOverride: String? = null,
    val speakerTempId: String,
    val text: String,
    val emotion: String? = null,
    val speakingStyle: String? = null,
    val addresseeTempId: String? = null,
    val isInnerMonologue: Boolean = false,
    val language: String? = null,
    val speech: SpeechParamsDto? = null,
) : PerformanceEventDto()

@Serializable
data class NarrationEventDto(
    override val id: String,
    override val sourceSpan: SourceSpanDto? = null,
    override val confidence: Double? = null,
    override val timing: TimingDto? = null,
    override val presentationOverride: String? = null,
    val text: String,
) : PerformanceEventDto()

@Serializable
data class ActionEventDto(
    override val id: String,
    override val sourceSpan: SourceSpanDto? = null,
    override val confidence: Double? = null,
    override val timing: TimingDto? = null,
    override val presentationOverride: String? = null,
    val characterTempId: String? = null,
    val description: String,
) : PerformanceEventDto()

@Serializable
data class EnvironmentEventDto(
    override val id: String,
    override val sourceSpan: SourceSpanDto? = null,
    override val confidence: Double? = null,
    override val timing: TimingDto? = null,
    override val presentationOverride: String? = null,
    val text: String,
    val location: String? = null,
) : PerformanceEventDto()

@Serializable
data class SoundEventDto(
    override val id: String,
    override val sourceSpan: SourceSpanDto? = null,
    override val confidence: Double? = null,
    override val timing: TimingDto? = null,
    override val presentationOverride: String? = null,
    val description: String,
) : PerformanceEventDto()

@Serializable
data class CameraEventDto(
    override val id: String,
    override val sourceSpan: SourceSpanDto? = null,
    override val confidence: Double? = null,
    override val timing: TimingDto? = null,
    override val presentationOverride: String? = null,
    val description: String,
) : PerformanceEventDto()

/**
 * 未知事件类型的兜底。校验层会把它标记为错误，Mapper 不会把它映射成领域事件。
 */
@Serializable
data class UnknownEventDto(
    override val id: String = "",
    override val sourceSpan: SourceSpanDto? = null,
    override val confidence: Double? = null,
    override val timing: TimingDto? = null,
    override val presentationOverride: String? = null,
    val type: String? = null,
) : PerformanceEventDto()

object PerformanceEventDtoSerializer :
    JsonContentPolymorphicSerializer<PerformanceEventDto>(PerformanceEventDto::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<PerformanceEventDto> {
        val type = element.jsonObject["type"]?.jsonPrimitive?.contentOrNull
        return when (type) {
            "dialogue" -> DialogueEventDto.serializer()
            "narration" -> NarrationEventDto.serializer()
            "action" -> ActionEventDto.serializer()
            "environment" -> EnvironmentEventDto.serializer()
            "sound" -> SoundEventDto.serializer()
            "camera" -> CameraEventDto.serializer()
            else -> UnknownEventDto.serializer()
        }
    }
}
