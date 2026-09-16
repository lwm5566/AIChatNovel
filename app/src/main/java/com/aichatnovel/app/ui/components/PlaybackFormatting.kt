package com.aichatnovel.app.ui.components

import com.aichatnovel.app.domain.model.DurationSource
import com.aichatnovel.app.domain.model.EventPosition

/**
 * 播放时间的显示格式：`m:ss`，超过一小时为 `h:mm:ss`。
 *
 * 只做展示格式化，不参与任何时间计算。
 */
fun formatPlaybackTime(millis: Long): String {
    val safeMillis = if (millis < 0L) 0L else millis
    val totalSeconds = safeMillis / 1000L
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3600L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * 时长来源的用户可读名称。
 *
 * [DurationSource.Audio] 表示这段时长来自**真实音频**，其余一律是估算或人工指定——
 * 两者在界面上必须能一眼区分，估算值不得被当成最终音频长度展示。
 */
fun durationSourceLabel(source: DurationSource): String = when (source) {
    DurationSource.Audio -> "实际时长"
    DurationSource.Estimated -> "预计时长"
    DurationSource.Manual -> "指定时长"
}

/**
 * 带来源标注的时长文本，例如「预计时长 0:10」或「实际时长 0:08」。
 */
fun formatDurationWithSource(millis: Long, source: DurationSource): String =
    "${durationSourceLabel(source)} ${formatPlaybackTime(millis)}"

/**
 * 整条时间轴的时长来源。
 *
 * 只有当**所有**事件位置都来自真实音频时才返回 [DurationSource.Audio]；
 * 只要有一段是估算，整条时间轴就按「预计时长」展示 ——
 * 不允许把估算值当成最终音频长度。
 */
fun timelineDurationSource(positions: List<EventPosition>): DurationSource =
    if (positions.isNotEmpty() && positions.all { it.durationSource == DurationSource.Audio }) {
        DurationSource.Audio
    } else {
        DurationSource.Estimated
    }
