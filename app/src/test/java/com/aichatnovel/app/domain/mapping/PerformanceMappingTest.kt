package com.aichatnovel.app.domain.mapping

import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.Dialogue
import com.aichatnovel.app.domain.model.DialogueEvent
import com.aichatnovel.app.domain.model.Narration
import com.aichatnovel.app.domain.model.NarrationEvent
import com.aichatnovel.app.domain.model.PerformanceEvent
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.domain.model.Timing
import com.aichatnovel.app.domain.model.Utterance
import com.aichatnovel.app.domain.model.VoiceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 演出映射规则的契约：同一 Beat 内的事件顺序、呈现介质与说话者 / 音色的派生优先级。
 *
 * 这些规则被 StoryExplorer 与 StoryPlay 共同消费，但不属于 DTO 校验或映射，因此在这里单独锁定。
 */
class PerformanceMappingTest {

    @Test
    fun `ordered events sorts by start offset`() {
        val beat = Beat(
            id = "beat-1",
            order = 1,
            events = listOf(
                narration("late", startOffsetMillis = 600L),
                narration("first", startOffsetMillis = 0L),
                narration("middle", startOffsetMillis = 300L),
            ),
        )

        assertEquals(listOf("first", "middle", "late"), beat.orderedEvents().map { it.id })
        assertEquals(listOf(0L, 300L, 600L), beat.orderedEvents().map { it.timing.startOffsetMillis })
    }

    @Test
    fun `ordered events keeps the relative order of events sharing the same offset`() {
        val beat = Beat(
            id = "beat-1",
            order = 1,
            events = listOf(
                narration("a", startOffsetMillis = 0L),
                narration("b", startOffsetMillis = 0L),
                narration("c", startOffsetMillis = 0L),
            ),
        )

        // 并发事件保留各自位置：起点相同不重排，只保证按起点分组有序
        assertEquals(listOf("a", "b", "c"), beat.orderedEvents().map { it.id })
    }

    @Test
    fun `ordered events leaves an already ordered beat untouched`() {
        val beat = Beat(
            id = "beat-1",
            order = 1,
            events = listOf(
                narration("a", startOffsetMillis = 0L),
                narration("b", startOffsetMillis = 100L),
                narration("c", startOffsetMillis = 200L),
            ),
        )

        assertEquals(listOf("a", "b", "c"), beat.orderedEvents().map { it.id })
    }

    @Test
    fun `effective presentation mode prefers the event override`() {
        val event = narration(
            id = "event-1",
            startOffsetMillis = 0L,
            presentationOverride = PresentationMode.PhoneCall,
        )

        assertEquals(PresentationMode.PhoneCall, event.effectivePresentationMode(PresentationMode.LiveScene))
    }

    @Test
    fun `effective presentation mode falls back to the scene mode`() {
        val event = narration(id = "event-1", startOffsetMillis = 0L)

        assertEquals(PresentationMode.InstantMessaging, event.effectivePresentationMode(PresentationMode.InstantMessaging))
        assertEquals(PresentationMode.LiveScene, event.effectivePresentationMode(PresentationMode.LiveScene))
    }

    @Test
    fun `effective presentation mode keeps the override even when it equals the scene mode`() {
        val event = narration(
            id = "event-1",
            startOffsetMillis = 0L,
            presentationOverride = PresentationMode.LiveScene,
        )

        assertEquals(PresentationMode.LiveScene, event.effectivePresentationMode(PresentationMode.LiveScene))
    }

    @Test
    fun `effective speaker prefers the utterance speaker`() {
        val event = dialogue(
            utteranceSpeakerId = "char-乙",
            dialogueCharacterId = "char-甲",
        )

        assertEquals("char-乙", event.effectiveSpeakerId)
    }

    @Test
    fun `effective speaker falls back to the dialogue character`() {
        val event = dialogue(
            utteranceSpeakerId = null,
            dialogueCharacterId = "char-甲",
        )

        assertEquals("char-甲", event.effectiveSpeakerId)
    }

    @Test
    fun `effective voice profile prefers the event override`() {
        val override = VoiceProfile(voiceRef = "voice-override")
        val characterProfile = VoiceProfile(voiceRef = "voice-character")

        assertSame(override, dialogue(voiceOverride = override).effectiveVoiceProfile(characterProfile))
    }

    @Test
    fun `effective voice profile falls back to the character profile`() {
        val characterProfile = VoiceProfile(voiceRef = "voice-character")

        assertSame(characterProfile, dialogue(voiceOverride = null).effectiveVoiceProfile(characterProfile))
    }

    @Test
    fun `effective voice profile is null when neither side defines one`() {
        // 当前导入链路不产生任何 VoiceProfile（AI 不提供音色），因此这里必须是 null 而不是兜底值
        assertNull(dialogue(voiceOverride = null).effectiveVoiceProfile(null))
    }

    private fun narration(
        id: String,
        startOffsetMillis: Long,
        presentationOverride: PresentationMode? = null,
    ): PerformanceEvent = NarrationEvent(
        id = id,
        timing = Timing(startOffsetMillis = startOffsetMillis),
        narration = Narration(text = id),
        presentationOverride = presentationOverride,
    )

    private fun dialogue(
        utteranceSpeakerId: String? = null,
        dialogueCharacterId: String = "char-甲",
        voiceOverride: VoiceProfile? = null,
    ): DialogueEvent = DialogueEvent(
        id = "event-dialogue",
        timing = Timing(startOffsetMillis = 0L),
        dialogue = Dialogue(characterId = dialogueCharacterId, text = "台词"),
        utterance = Utterance(speakerId = utteranceSpeakerId, text = "台词"),
        voiceOverride = voiceOverride,
    )
}
