package com.aichatnovel.app.domain.mapping

import com.aichatnovel.app.domain.model.AudioAsset
import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.DurationSource
import com.aichatnovel.app.domain.model.EventPosition
import com.aichatnovel.app.domain.model.ExecutableTimeline
import com.aichatnovel.app.domain.model.PlaybackCursor
import com.aichatnovel.app.domain.model.Scene

/**
 * 事件没有给出时长时，本地模拟播放用来铺开时间轴的估算时长。
 *
 * 它只回答「这个事件在时间轴上大概占多久」，**不是**真实音频时长：
 * 由它得出的位置一律标记为 [DurationSource.Estimated]，
 * 只有 TTS 合成结果回填后才允许变成 [DurationSource.Audio]。
 */
const val PLAYBACK_ESTIMATED_EVENT_DURATION_MILLIS = 1_000L

/**
 * 把某个场景的节拍铺开成一条可按位置查询的可执行时间轴。
 *
 * 布局规则：
 * - 节拍按 [Beat.order] 升序依次排列；上一个节拍的结束位置就是下一个的起点；
 * - 事件位置 = 节拍起点 + [com.aichatnovel.app.domain.model.Timing.startOffsetMillis]（负数一律按 0 处理）；
 * - 事件时长为空时用 [PLAYBACK_ESTIMATED_EVENT_DURATION_MILLIS] 参与布局，来源标记为 [DurationSource.Estimated]；
 * - 若该事件已经有**真实音频**（[audioAssets] 里的 [AudioAsset]），则用它的 `durationMillis`
 *   并把来源标为 [DurationSource.Audio]；否则才回退到事件自身的时长 / 估算值。
 * - 总时长取「场景已知总时长」与「布局末端」的较大者，避免事件被已知时长截断；
 * - 没有任何节拍或事件时，得到一条时长为 0 的空时间轴。
 *
 * [audioAssets] 缺省为空：不传真实音频时，行为与 Phase 6B 完全一致。
 */
fun buildExecutableTimeline(
    scene: Scene,
    beats: List<Beat>,
    audioAssets: Map<String, AudioAsset> = emptyMap(),
): ExecutableTimeline {
    val positions = mutableListOf<EventPosition>()
    var beatStart = 0L

    beats.sortedBy { it.order }.forEach { beat ->
        var beatEnd = beatStart
        beat.orderedEvents().forEach { event ->
            val start = beatStart + event.timing.startOffsetMillis.coerceAtLeast(0L)
            val audio = audioAssets[event.id]
            val knownDuration = event.timing.durationMillis?.takeIf { it >= 0L }
            val duration = audio?.durationMillis
                ?: knownDuration
                ?: PLAYBACK_ESTIMATED_EVENT_DURATION_MILLIS
            positions += EventPosition(
                beatId = beat.id,
                eventId = event.id,
                startOffsetMillis = start,
                durationMillis = duration,
                durationSource = when {
                    // 只有真实产出的音频才能标成 Audio
                    audio != null -> DurationSource.Audio
                    knownDuration == null -> DurationSource.Estimated
                    else -> event.timing.durationSource ?: DurationSource.Estimated
                },
            )
            beatEnd = maxOf(beatEnd, start + duration)
        }
        beatStart = beatEnd
    }

    val layoutEnd = positions.maxOfOrNull { it.endOffsetMillis } ?: 0L
    val knownTotal = scene.timeline.totalDurationMillis?.coerceAtLeast(0L) ?: 0L

    return ExecutableTimeline(
        totalDurationMillis = maxOf(knownTotal, layoutEnd),
        durationSource = scene.timeline.durationSource ?: DurationSource.Estimated,
        positions = positions,
    )
}

/**
 * 定位到 [positionMillis] 时刻正在演出的事件。
 *
 * - 事件区间是闭区间 `[startOffsetMillis, endOffsetMillis]`，正好落在结束点也算命中；
 * - 同一时刻有多个事件时取「最近开始」的那个（起点最大者；起点相同则取稳定顺序中靠后的）；
 * - 没有任何事件在演出时返回空游标 —— 空档期不伪造事件。
 */
fun ExecutableTimeline.cursorAt(positionMillis: Long): PlaybackCursor {
    val position = positionMillis.coerceAtLeast(0L)
    val active = positions.lastOrNull {
        position >= it.startOffsetMillis && position <= it.endOffsetMillis
    }
    return PlaybackCursor(beatId = active?.beatId, eventId = active?.eventId)
}
