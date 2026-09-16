package com.aichatnovel.app.domain.mapping

import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.DurationSource
import com.aichatnovel.app.domain.model.ExecutableTimeline
import com.aichatnovel.app.domain.model.Narration
import com.aichatnovel.app.domain.model.NarrationEvent
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.Timeline
import com.aichatnovel.app.domain.model.Timing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 可执行时间轴与播放游标的契约。
 *
 * 锁定的是「节拍如何铺开到场景时间轴」以及「给定位置如何定位事件」这两件纯计算，
 * 不涉及任何 UI、播放器或音频实现。
 */
class TimelineMappingTest {

    // ---------- 布局 ----------

    @Test
    fun `events of one beat are laid out by their start offset`() {
        val timeline = buildExecutableTimeline(
            scene = scene(),
            beats = listOf(
                beat(
                    "beat-1", 1,
                    narration("a", startOffsetMillis = 0L, durationMillis = 500L),
                    narration("b", startOffsetMillis = 1_000L, durationMillis = 500L),
                ),
            ),
        )

        assertEquals(listOf("a", "b"), timeline.positions.map { it.eventId })
        assertEquals(listOf(0L, 1_000L), timeline.positions.map { it.startOffsetMillis })
        assertEquals(listOf(500L, 1_500L), timeline.positions.map { it.endOffsetMillis })
        assertEquals(1_500L, timeline.totalDurationMillis)
    }

    @Test
    fun `beats are laid out in order and the next beat starts where the previous ends`() {
        // 夹具刻意把 order 2 放在前面
        val timeline = buildExecutableTimeline(
            scene = scene(),
            beats = listOf(
                beat("beat-2", 2, narration("second", startOffsetMillis = 0L, durationMillis = 400L)),
                beat("beat-1", 1, narration("first", startOffsetMillis = 0L, durationMillis = 600L)),
            ),
        )

        assertEquals(listOf("first", "second"), timeline.positions.map { it.eventId })
        assertEquals(listOf(0L, 600L), timeline.positions.map { it.startOffsetMillis })
        assertEquals(listOf("beat-1", "beat-2"), timeline.positions.map { it.beatId })
        assertEquals(1_000L, timeline.totalDurationMillis)
    }

    @Test
    fun `an event without a duration falls back to the playback estimate`() {
        val timeline = buildExecutableTimeline(
            scene = scene(),
            beats = listOf(beat("beat-1", 1, narration("a", startOffsetMillis = 0L))),
        )

        val position = timeline.positions.single()
        assertEquals(PLAYBACK_ESTIMATED_EVENT_DURATION_MILLIS, position.durationMillis)
        assertEquals(PLAYBACK_ESTIMATED_EVENT_DURATION_MILLIS, position.endOffsetMillis)
        assertEquals(DurationSource.Estimated, position.durationSource)
    }

    @Test
    fun `a missing duration is never labelled as audio`() {
        val timeline = buildExecutableTimeline(
            scene = scene(),
            beats = listOf(beat("beat-1", 1, narration("a", startOffsetMillis = 0L, durationMillis = null))),
        )

        // 没有真实时长时，来源只能是 Estimated —— 估算不是「音频真实时长」
        assertEquals(DurationSource.Estimated, timeline.positions.single().durationSource)
    }

    @Test
    fun `an explicit duration source is preserved`() {
        val timeline = buildExecutableTimeline(
            scene = scene(),
            beats = listOf(
                beat(
                    "beat-1", 1,
                    narration("audio", 0L, durationMillis = 800L, durationSource = DurationSource.Audio),
                    narration("manual", 0L, durationMillis = 200L, durationSource = DurationSource.Manual),
                    narration("unsourced", 0L, durationMillis = 300L),
                ),
            ),
        )

        assertEquals(
            listOf(DurationSource.Audio, DurationSource.Manual, DurationSource.Estimated),
            timeline.positions.map { it.durationSource },
        )
    }

    @Test
    fun `a negative start offset is clamped to zero`() {
        val timeline = buildExecutableTimeline(
            scene = scene(),
            beats = listOf(beat("beat-1", 1, narration("a", startOffsetMillis = -500L, durationMillis = 300L))),
        )

        assertEquals(0L, timeline.positions.single().startOffsetMillis)
    }

    @Test
    fun `a negative duration is treated as unknown`() {
        val timeline = buildExecutableTimeline(
            scene = scene(),
            beats = listOf(beat("beat-1", 1, narration("a", startOffsetMillis = 0L, durationMillis = -1L))),
        )

        assertEquals(PLAYBACK_ESTIMATED_EVENT_DURATION_MILLIS, timeline.positions.single().durationMillis)
        assertEquals(DurationSource.Estimated, timeline.positions.single().durationSource)
    }

    @Test
    fun `a beat without events does not shift the following beat`() {
        val timeline = buildExecutableTimeline(
            scene = scene(),
            beats = listOf(
                beat("beat-1", 1),
                beat("beat-2", 2, narration("a", startOffsetMillis = 0L, durationMillis = 300L)),
            ),
        )

        assertEquals(0L, timeline.positions.single().startOffsetMillis)
        assertEquals(300L, timeline.totalDurationMillis)
    }

    @Test
    fun `an empty scene yields a zero length timeline`() {
        val timeline = buildExecutableTimeline(scene = scene(), beats = emptyList())

        assertTrue(timeline.positions.isEmpty())
        assertEquals(0L, timeline.totalDurationMillis)
    }

    @Test
    fun `a scene without beats yields a zero length timeline`() {
        val timeline = buildExecutableTimeline(
            scene = scene(timeline = Timeline(totalDurationMillis = 4_000L, durationSource = DurationSource.Audio)),
            beats = emptyList(),
        )

        // 没有事件可铺开时，仍如实反映场景已知的总时长
        assertTrue(timeline.positions.isEmpty())
        assertEquals(4_000L, timeline.totalDurationMillis)
        assertEquals(DurationSource.Audio, timeline.durationSource)
    }

    @Test
    fun `the longer of the known duration and the layout end wins`() {
        val known = buildExecutableTimeline(
            scene = scene(timeline = Timeline(totalDurationMillis = 5_000L)),
            beats = listOf(beat("beat-1", 1, narration("a", 0L, durationMillis = 400L))),
        )
        assertEquals(5_000L, known.totalDurationMillis)

        val laid = buildExecutableTimeline(
            scene = scene(timeline = Timeline(totalDurationMillis = 100L)),
            beats = listOf(beat("beat-1", 1, narration("a", 0L, durationMillis = 900L))),
        )
        assertEquals(900L, laid.totalDurationMillis)
    }

    @Test
    fun `events sharing an offset keep their relative order`() {
        val timeline = buildExecutableTimeline(
            scene = scene(),
            beats = listOf(
                beat(
                    "beat-1", 1,
                    narration("a", 0L, durationMillis = 100L),
                    narration("b", 0L, durationMillis = 100L),
                    narration("c", 0L, durationMillis = 100L),
                ),
            ),
        )

        assertEquals(listOf("a", "b", "c"), timeline.positions.map { it.eventId })
    }

    // ---------- 游标 ----------

    @Test
    fun `position zero picks the event starting at zero`() {
        val cursor = cursor(singleEventTimeline(start = 0L, duration = 1_000L), 0L)

        assertEquals("beat-1", cursor.beatId)
        assertEquals("a", cursor.eventId)
    }

    @Test
    fun `a position before the first event has no cursor`() {
        val cursor = cursor(singleEventTimeline(start = 500L, duration = 500L), 200L)

        assertNull(cursor.beatId)
        assertNull(cursor.eventId)
    }

    @Test
    fun `a position exactly on the start of an event picks that event`() {
        val cursor = cursor(singleEventTimeline(start = 400L, duration = 600L), 400L)

        assertEquals("a", cursor.eventId)
    }

    @Test
    fun `a position in the middle of an event picks that event`() {
        val cursor = cursor(singleEventTimeline(start = 400L, duration = 600L), 700L)

        assertEquals("a", cursor.eventId)
    }

    @Test
    fun `a position exactly on the end of an event still counts as inside`() {
        val cursor = cursor(singleEventTimeline(start = 400L, duration = 600L), 1_000L)

        assertEquals("a", cursor.eventId)
    }

    @Test
    fun `a position in a gap between events has no cursor`() {
        val timeline = buildExecutableTimeline(
            scene = scene(),
            beats = listOf(
                beat(
                    "beat-1", 1,
                    narration("a", startOffsetMillis = 0L, durationMillis = 200L),
                    narration("b", startOffsetMillis = 800L, durationMillis = 200L),
                ),
            ),
        )

        assertEquals("a", cursor(timeline, 100L).eventId)
        assertNull(cursor(timeline, 500L).eventId)
        assertEquals("b", cursor(timeline, 900L).eventId)
    }

    @Test
    fun `a position beyond the timeline has no cursor`() {
        val cursor = cursor(singleEventTimeline(start = 0L, duration = 500L), 5_000L)

        assertNull(cursor.eventId)
    }

    @Test
    fun `a negative position is clamped to the start`() {
        val cursor = cursor(singleEventTimeline(start = 0L, duration = 500L), -300L)

        assertEquals("a", cursor.eventId)
    }

    @Test
    fun `an empty timeline has no cursor`() {
        val cursor = cursor(ExecutableTimeline(), 0L)

        assertNull(cursor.beatId)
        assertNull(cursor.eventId)
    }

    @Test
    fun `events sharing an offset resolve to the last one in stable order`() {
        val timeline = buildExecutableTimeline(
            scene = scene(),
            beats = listOf(
                beat(
                    "beat-1", 1,
                    narration("a", 0L, durationMillis = 1_000L),
                    narration("b", 0L, durationMillis = 1_000L),
                ),
            ),
        )

        // 并发事件取「最近开始」的一个：起点相同则取稳定顺序中靠后的
        assertEquals("b", cursor(timeline, 500L).eventId)
    }

    @Test
    fun `a later beat takes over the cursor once it starts`() {
        val timeline = buildExecutableTimeline(
            scene = scene(),
            beats = listOf(
                beat("beat-1", 1, narration("first", 0L, durationMillis = 300L)),
                beat("beat-2", 2, narration("second", 0L, durationMillis = 300L)),
            ),
        )

        assertEquals("beat-1", cursor(timeline, 100L).beatId)
        assertEquals("first", cursor(timeline, 100L).eventId)
        assertEquals("beat-2", cursor(timeline, 400L).beatId)
        assertEquals("second", cursor(timeline, 400L).eventId)
    }

    // ---------- helpers ----------

    private fun cursor(timeline: ExecutableTimeline, positionMillis: Long) =
        timeline.cursorAt(positionMillis)

    private fun singleEventTimeline(start: Long, duration: Long): ExecutableTimeline =
        buildExecutableTimeline(
            scene = scene(),
            beats = listOf(beat("beat-1", 1, narration("a", start, durationMillis = duration))),
        )

    private fun scene(timeline: Timeline = Timeline()): Scene = Scene(
        id = "scene-1",
        chapterId = "chapter-1",
        index = 1,
        title = "教室",
        timeline = timeline,
    )

    private fun beat(id: String, order: Int, vararg events: NarrationEvent): Beat =
        Beat(id = id, order = order, events = events.toList())

    private fun narration(
        id: String,
        startOffsetMillis: Long,
        durationMillis: Long? = null,
        durationSource: DurationSource? = null,
    ): NarrationEvent = NarrationEvent(
        id = id,
        timing = Timing(
            startOffsetMillis = startOffsetMillis,
            durationMillis = durationMillis,
            durationSource = durationSource,
        ),
        narration = Narration(text = id),
    )
}
