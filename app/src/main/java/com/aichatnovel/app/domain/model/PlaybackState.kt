package com.aichatnovel.app.domain.model

/** 播放状态机的状态。 */
enum class PlaybackStatus {
    /** 尚未开始播放，或已被重置。 */
    Idle,

    /** 正在播放，位置随时间推进。 */
    Playing,

    /** 已暂停，位置保留。 */
    Paused,

    /** 已播放到时间轴末端。 */
    Completed,
}

/**
 * 某一时刻正在演出的位置。两个字段都为空表示该时刻没有任何事件在演出
 * （例如时间轴空档期或者根本没有可执行内容）——此时**不伪造**事件。
 */
data class PlaybackCursor(
    val beatId: String? = null,
    val eventId: String? = null,
)

/**
 * 播放状态。所有转换都是纯函数：输入当前状态，输出新状态，
 * 不持有计时器、不依赖任何播放器实现，UI 只负责渲染与发出意图。
 */
data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val currentBeatId: String? = null,
    val currentEventId: String? = null,
) {

    /** 播放进度，供进度条使用；没有可播放内容时固定为 0。 */
    val progress: Float
        get() = if (durationMillis <= 0L) 0f
        else (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)

    /** 是否有可播放的内容。 */
    val hasPlayableContent: Boolean get() = durationMillis > 0L

    /**
     * 开始播放。没有可播放内容时保持初始状态；
     * 已播放完的内容不会自动重播，需要先 [reset]。
     */
    fun play(): PlaybackState = when {
        durationMillis <= 0L -> PlaybackState(durationMillis = durationMillis)
        status == PlaybackStatus.Completed -> this
        else -> copy(status = PlaybackStatus.Playing)
    }

    /** 暂停：位置保留。非播放状态下调用不产生变化。 */
    fun pause(): PlaybackState =
        if (status == PlaybackStatus.Playing) copy(status = PlaybackStatus.Paused) else this

    /** 重置：回到明确的初始位置，并清空当前定位。 */
    fun reset(): PlaybackState = PlaybackState(durationMillis = durationMillis)

    /**
     * 推进一个时间片。只有播放中才会前进，位置被限制在时长以内；
     * 到达末端即进入 [PlaybackStatus.Completed]，之后不再前进。
     */
    fun advanceBy(deltaMillis: Long): PlaybackState {
        if (status != PlaybackStatus.Playing || deltaMillis <= 0L) return this
        val next = (positionMillis + deltaMillis).coerceAtMost(durationMillis)
        return copy(
            positionMillis = next,
            status = if (next >= durationMillis) PlaybackStatus.Completed else PlaybackStatus.Playing,
        )
    }

    /**
     * 跳转到指定位置：超出范围会被收进 `[0, durationMillis]`。
     * 从「已播放完」跳回中间时会回到暂停态，以便继续播放。
     */
    fun seekTo(positionMillis: Long): PlaybackState {
        val clamped = positionMillis.coerceIn(0L, durationMillis)
        val nextStatus = if (status == PlaybackStatus.Completed && clamped < durationMillis) {
            PlaybackStatus.Paused
        } else {
            status
        }
        return copy(positionMillis = clamped, status = nextStatus)
    }

    /** 用外部真实时钟（音频播放器位置）同步播放位置；仅播放中有效。 */
    fun syncTo(positionMillis: Long): PlaybackState {
        if (status != PlaybackStatus.Playing) return this
        val next = positionMillis.coerceIn(0L, durationMillis)
        return copy(
            positionMillis = next,
            status = if (next >= durationMillis) PlaybackStatus.Completed else PlaybackStatus.Playing,
        )
    }

    /** 更新当前定位；不影响播放位置与状态。 */
    fun withCursor(cursor: PlaybackCursor): PlaybackState =
        copy(currentBeatId = cursor.beatId, currentEventId = cursor.eventId)
}
