package com.aichatnovel.app.ui.components

import com.aichatnovel.app.domain.model.DurationSource
import com.aichatnovel.app.domain.model.EventPosition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 播放时间的展示契约。
 *
 * 重点是「预计时长」与「实际音频时长」在界面上必须能被区分：
 * 估算值不得被渲染成看起来像最终音频长度的纯数字。
 */
class PlaybackFormattingTest {

    @Test
    fun `playback time is rendered as minutes and seconds`() {
        assertEquals("0:00", formatPlaybackTime(0L))
        assertEquals("0:09", formatPlaybackTime(9_900L))
        assertEquals("1:00", formatPlaybackTime(60_000L))
        assertEquals("59:59", formatPlaybackTime(3_599_000L))
        assertEquals("1:01:01", formatPlaybackTime(3_661_000L))
    }

    @Test
    fun `negative position is clamped to zero`() {
        assertEquals("0:00", formatPlaybackTime(-500L))
    }

    @Test
    fun `duration source keeps estimated and audio apart`() {
        assertEquals("预计时长", durationSourceLabel(DurationSource.Estimated))
        assertEquals("实际时长", durationSourceLabel(DurationSource.Audio))
        assertEquals("指定时长", durationSourceLabel(DurationSource.Manual))
    }

    @Test
    fun `duration label always carries its source`() {
        assertEquals("预计时长 0:10", formatDurationWithSource(10_000L, DurationSource.Estimated))
        assertEquals("实际时长 0:08", formatDurationWithSource(8_700L, DurationSource.Audio))
    }

    @Test
    fun `an empty timeline is never reported as actual audio duration`() {
        assertEquals(DurationSource.Estimated, timelineDurationSource(emptyList()))
    }

    @Test
    fun `a single estimated position keeps the whole timeline estimated`() {
        val positions = listOf(
            position(DurationSource.Audio),
            position(DurationSource.Estimated),
        )

        assertEquals(DurationSource.Estimated, timelineDurationSource(positions))
    }

    @Test
    fun `a manual position keeps the timeline estimated`() {
        assertEquals(
            DurationSource.Estimated,
            timelineDurationSource(listOf(position(DurationSource.Manual))),
        )
    }

    @Test
    fun `every position from audio makes the timeline actual`() {
        val positions = listOf(
            position(DurationSource.Audio),
            position(DurationSource.Audio),
        )

        assertEquals(DurationSource.Audio, timelineDurationSource(positions))
    }

    private fun position(source: DurationSource) = EventPosition(
        beatId = "beat-1",
        eventId = "event-1",
        startOffsetMillis = 0L,
        durationMillis = 1_000L,
        durationSource = source,
    )
}
