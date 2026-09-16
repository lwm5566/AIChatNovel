package com.aichatnovel.app.domain.model

/**
 * 一个演出事件在**场景时间轴**上的位置。
 *
 * 与 [Timing] 的区别：[Timing] 是事件在**所属节拍内**的偏移，
 * 这里已经铺开到场景级——节拍的先后被折算成时间，因此可以直接按位置查询。
 *
 * [durationSource] 说明这段时长是怎么来的。当事件本身没有真实时长时，
 * 这里会是 [DurationSource.Estimated]（本地模拟播放的估算），**不会**被标成
 * [DurationSource.Audio]——真实音频时长要等 TTS 合成后回填。
 */
data class EventPosition(
    val beatId: String,
    val eventId: String,
    val startOffsetMillis: Long,
    val durationMillis: Long,
    val durationSource: DurationSource,
) {

    /** 事件在场景时间轴上的结束位置。 */
    val endOffsetMillis: Long get() = startOffsetMillis + durationMillis
}

/**
 * 可执行时间轴：某个 [Scene] 下所有节拍与事件铺开后的结果，可按时间位置查询。
 *
 * 它是**派生数据**，不替代 [Scene.timeline]（那是解析阶段已知的时间元信息）：
 * [Scene.timeline] 的时长允许为空，而可执行时间轴必须给出一个确定的播放入口，
 * 因此这里的总时长总是有值。
 */
data class ExecutableTimeline(
    val totalDurationMillis: Long = 0L,
    val durationSource: DurationSource = DurationSource.Estimated,
    val positions: List<EventPosition> = emptyList(),
)
