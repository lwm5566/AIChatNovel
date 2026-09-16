package com.aichatnovel.app.domain.model

/**
 * 时长来源。用于说明某个 [Timing.durationMillis] 是怎么得到的，
 * 不绑定任何具体的 TTS 或音频服务。
 */
enum class DurationSource {

    /** 估算值：解析或排版阶段给出的估计时长。 */
    Estimated,

    /** 已由音频合成结果回填的真实时长。 */
    Audio,

    /** 人工指定的时长。 */
    Manual,
}

/**
 * 一个 [Scene] 的整体时间轴。
 *
 * [totalDurationMillis] 允许为空：解析阶段尚不知道最终时长，
 * 真实的音频时长要等 TTS 合成后回填。
 */
data class Timeline(
    val totalDurationMillis: Long? = null,
    val durationSource: DurationSource? = null,
)

/**
 * 某个演出事件在所属 [Beat] 内的时间位置。
 *
 * [startOffsetMillis] 相对所属 [Beat] 的起点；[durationMillis] 允许为空，
 * 表示「还不知道要持续多久」（例如尚未合成的对白）。
 */
data class Timing(
    val startOffsetMillis: Long = 0L,
    val durationMillis: Long? = null,
    val durationSource: DurationSource? = null,
)
