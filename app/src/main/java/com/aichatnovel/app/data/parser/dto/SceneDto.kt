package com.aichatnovel.app.data.parser.dto

import kotlinx.serialization.Serializable

/** AI 解析出的场景设定。 */
@Serializable
data class SceneSettingDto(
    val location: String? = null,
    val timeOfDay: String? = null,
    val weather: String? = null,
    val lighting: String? = null,
    val backgroundRef: String? = null,
)

/**
 * AI 解析出的场景。
 *
 * [presentationMode] 与 [presentationEvidence] 是核心语义字段：
 * 非 LiveScene 的取值必须带证据，否则 Mapper 会降级为 LiveScene 并产生 warning。
 * [id] 与 [tempId] 至少提供一个。
 */
@Serializable
data class SceneDto(
    val id: String? = null,
    val tempId: String? = null,
    val title: String,
    val presentationMode: String? = null,
    val presentationEvidence: PresentationEvidenceDto? = null,
    val setting: SceneSettingDto? = null,
    val participants: List<String> = emptyList(),
    val sourceSpan: SourceSpanDto? = null,
    val confidence: Double? = null,
    val beats: List<BeatDto> = emptyList(),
)
