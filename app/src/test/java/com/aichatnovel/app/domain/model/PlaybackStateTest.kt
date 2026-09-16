package com.aichatnovel.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 播放状态机的契约。
 *
 * 这里只锁定纯函数转换的规则；真实播放器、计时器与音频都不在本阶段范围内。
 */
class PlaybackStateTest {

    @Test
    fun `a fresh state is idle at position zero`() {
        val state = PlaybackState(durationMillis = 3_000L)

        assertEquals(PlaybackStatus.Idle, state.status)
        assertEquals(0L, state.positionMillis)
        assertEquals(3_000L, state.durationMillis)
        assertNull(state.currentBeatId)
        assertNull(state.currentEventId)
    }

    @Test
    fun `play from idle starts playing`() {
        val state = PlaybackState(durationMillis = 3_000L).play()

        assertEquals(PlaybackStatus.Playing, state.status)
        assertEquals(0L, state.positionMillis)
    }

    @Test
    fun `play does nothing without any playable content`() {
        val state = PlaybackState().play()

        assertEquals(PlaybackStatus.Idle, state.status)
        assertEquals(0L, state.positionMillis)
    }

    @Test
    fun `play does not restart an already completed state`() {
        val completed = PlaybackState(durationMillis = 1_000L, positionMillis = 1_000L, status = PlaybackStatus.Completed)

        val state = completed.play()

        assertEquals(PlaybackStatus.Completed, state.status)
        assertEquals(1_000L, state.positionMillis)
    }

    @Test
    fun `pause keeps the position`() {
        val state = PlaybackState(durationMillis = 3_000L, positionMillis = 1_200L, status = PlaybackStatus.Playing)
            .pause()

        assertEquals(PlaybackStatus.Paused, state.status)
        assertEquals(1_200L, state.positionMillis)
    }

    @Test
    fun `pause on an idle state changes nothing`() {
        val idle = PlaybackState(durationMillis = 3_000L)

        assertEquals(idle, idle.pause())
    }

    @Test
    fun `resume continues from the paused position`() {
        val paused = PlaybackState(durationMillis = 3_000L, positionMillis = 1_200L, status = PlaybackStatus.Paused)

        val state = paused.play()

        assertEquals(PlaybackStatus.Playing, state.status)
        assertEquals(1_200L, state.positionMillis)
    }

    @Test
    fun `reset returns to the initial position and clears the cursor`() {
        val playing = PlaybackState(
            status = PlaybackStatus.Playing,
            positionMillis = 1_200L,
            durationMillis = 3_000L,
            currentBeatId = "beat-1",
            currentEventId = "e1",
        )

        val state = playing.reset()

        assertEquals(PlaybackStatus.Idle, state.status)
        assertEquals(0L, state.positionMillis)
        assertEquals(3_000L, state.durationMillis)
        assertNull(state.currentBeatId)
        assertNull(state.currentEventId)
    }

    @Test
    fun `advance moves the position while playing`() {
        val state = PlaybackState(durationMillis = 3_000L, status = PlaybackStatus.Playing).advanceBy(500L)

        assertEquals(PlaybackStatus.Playing, state.status)
        assertEquals(500L, state.positionMillis)
    }

    @Test
    fun `advance stops at the end and completes`() {
        val state = PlaybackState(durationMillis = 600L, positionMillis = 500L, status = PlaybackStatus.Playing)
            .advanceBy(500L)

        assertEquals(PlaybackStatus.Completed, state.status)
        assertEquals(600L, state.positionMillis)
    }

    @Test
    fun `advance does nothing once completed`() {
        val completed = PlaybackState(durationMillis = 600L, positionMillis = 600L, status = PlaybackStatus.Completed)

        assertEquals(completed, completed.advanceBy(500L))
    }

    @Test
    fun `advance does nothing while paused`() {
        val paused = PlaybackState(durationMillis = 600L, positionMillis = 200L, status = PlaybackStatus.Paused)

        assertEquals(paused, paused.advanceBy(500L))
    }

    @Test
    fun `advance ignores a non positive delta`() {
        val playing = PlaybackState(durationMillis = 600L, status = PlaybackStatus.Playing)

        assertEquals(playing, playing.advanceBy(0L))
        assertEquals(playing, playing.advanceBy(-100L))
    }

    @Test
    fun `seek clamps below zero`() {
        val state = PlaybackState(durationMillis = 3_000L).seekTo(-1_000L)

        assertEquals(0L, state.positionMillis)
    }

    @Test
    fun `seek clamps beyond the duration`() {
        val state = PlaybackState(durationMillis = 3_000L).seekTo(9_000L)

        assertEquals(3_000L, state.positionMillis)
    }

    @Test
    fun `seek from completed returns to paused so playback can continue`() {
        val completed = PlaybackState(durationMillis = 3_000L, positionMillis = 3_000L, status = PlaybackStatus.Completed)

        val state = completed.seekTo(1_000L)

        assertEquals(PlaybackStatus.Paused, state.status)
        assertEquals(1_000L, state.positionMillis)
    }

    @Test
    fun `seek to the very end of a completed track stays completed`() {
        val completed = PlaybackState(durationMillis = 3_000L, positionMillis = 3_000L, status = PlaybackStatus.Completed)

        val state = completed.seekTo(3_000L)

        assertEquals(PlaybackStatus.Completed, state.status)
        assertEquals(3_000L, state.positionMillis)
    }

    @Test
    fun `seek keeps the status while playing`() {
        val playing = PlaybackState(durationMillis = 3_000L, status = PlaybackStatus.Playing)

        val state = playing.seekTo(1_000L)

        assertEquals(PlaybackStatus.Playing, state.status)
        assertEquals(1_000L, state.positionMillis)
    }

    @Test
    fun `a seek without playable content stays at zero`() {
        val state = PlaybackState().seekTo(1_000L)

        assertEquals(0L, state.positionMillis)
        assertEquals(PlaybackStatus.Idle, state.status)
    }

    @Test
    fun `with cursor only updates the location`() {
        val playing = PlaybackState(durationMillis = 3_000L, positionMillis = 1_000L, status = PlaybackStatus.Playing)

        val state = playing.withCursor(PlaybackCursor(beatId = "beat-2", eventId = "e9"))

        assertEquals("beat-2", state.currentBeatId)
        assertEquals("e9", state.currentEventId)
        assertEquals(1_000L, state.positionMillis)
        assertEquals(PlaybackStatus.Playing, state.status)
    }

    @Test
    fun `progress reflects the position`() {
        val half = PlaybackState(durationMillis = 2_000L, positionMillis = 1_000L)

        assertEquals(0.5f, half.progress, 0.0001f)
    }

    @Test
    fun `progress is zero without any playable content`() {
        assertEquals(0f, PlaybackState().progress, 0.0001f)
    }
}
