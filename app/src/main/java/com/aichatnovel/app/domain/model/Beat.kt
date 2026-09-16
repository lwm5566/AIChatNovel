package com.aichatnovel.app.domain.model

/**
 * 一个演出节拍，是 [Scene] 与 [PerformanceEvent] 之间的中间层。
 *
 * 同一 [Beat] 内的多个事件**允许并发或时间重叠**，例如：
 * 角色说话的同时伴随一个动作、一段环境音和一个镜头变化。
 * 事件之间的先后由各自的 [Timing.startOffsetMillis] 决定，而不是列表顺序。
 *
 * 节拍之间则按 [order] 先后播放。
 */
data class Beat(
    val id: String,
    val order: Int,
    val events: List<PerformanceEvent> = emptyList(),
)
