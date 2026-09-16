package com.aichatnovel.app.data.parser.dto

import kotlinx.serialization.Serializable

/**
 * AI 解析出的角色。
 *
 * [tempId] 只是本次响应内的临时 ID，Mapper 负责把它换算成 App 内稳定的 characterId，
 * 不会把 tempId 当作最终业务 ID 使用。
 */
@Serializable
data class CharacterDto(
    val tempId: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String? = null,
    val confidence: Double? = null,
    val sourceSpan: SourceSpanDto? = null,
)
