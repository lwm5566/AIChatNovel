package com.aichatnovel.app.data.parser.dto

import kotlinx.serialization.Serializable

/**
 * AI 响应中的原文溯源。
 *
 * 注意这里的字段名（startOffset / endOffset）与 domain 一致，但 [chapterId] 允许为空，
 * 缺省时由 [ParseResponseDto.chapterId] 补齐。
 */
@Serializable
data class SourceSpanDto(
    val chapterId: String? = null,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
    val snippet: String? = null,
)

/** AI 给出的时间信息。[duration] 允许为空：AI 不负责生成最终 TTS 时长。 */
@Serializable
data class TimingDto(
    val startOffset: Long? = null,
    val duration: Long? = null,
    val durationSource: String? = null,
)

/** AI 给出的发声参数，全部允许为空（表示使用默认值）。 */
@Serializable
data class SpeechParamsDto(
    val rate: Float? = null,
    val pitch: Float? = null,
    val volume: Float? = null,
)

/** 呈现介质的判定依据。非 LiveScene 的场景必须提供。 */
@Serializable
data class PresentationEvidenceDto(
    val text: String? = null,
    val sourceSpan: SourceSpanDto? = null,
)
