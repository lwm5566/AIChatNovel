package com.aichatnovel.app.data.parser.dto

import kotlinx.serialization.Serializable

/**
 * AI 解析响应的根 DTO，对应「一章原文的结构化结果」。
 *
 * [schemaVersion] 是必须字段：校验层根据它决定是否支持，
 * 支持的版本集合集中定义在 [com.aichatnovel.app.data.parser.ParseSchema]，不散落在各处。
 */
@Serializable
data class ParseResponseDto(
    val schemaVersion: String,
    val storyId: String? = null,
    val chapterId: String,
    val characters: List<CharacterDto> = emptyList(),
    val scenes: List<SceneDto> = emptyList(),
)
