package com.aichatnovel.app.data.parser.dto

import kotlinx.serialization.Serializable

/**
 * AI 解析出的演出节拍。同一节拍内的事件允许并发或时间重叠。
 * [order] 为空时 Mapper 按数组顺序补全。
 */
@Serializable
data class BeatDto(
    val id: String? = null,
    val order: Int? = null,
    val events: List<PerformanceEventDto> = emptyList(),
)
