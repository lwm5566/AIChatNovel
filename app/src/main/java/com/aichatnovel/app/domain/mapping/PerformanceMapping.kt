package com.aichatnovel.app.domain.mapping

import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.DialogueEvent
import com.aichatnovel.app.domain.model.PerformanceEvent
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.domain.model.VoiceProfile

/**
 * 事件的实际呈现介质：自身覆盖优先，否则继承所属场景。
 */
fun PerformanceEvent.effectivePresentationMode(sceneMode: PresentationMode): PresentationMode =
    presentationOverride ?: sceneMode

/**
 * 实际发声者：事件未指定时回落到对白本身的说话者。
 */
val DialogueEvent.effectiveSpeakerId: String
    get() = utterance.speakerId ?: dialogue.characterId

/**
 * 实际音色档案：事件覆盖优先，否则继承该角色的默认音色。
 */
fun DialogueEvent.effectiveVoiceProfile(characterProfile: VoiceProfile?): VoiceProfile? =
    voiceOverride ?: characterProfile

/**
 * 同一节拍内按时间偏移排序。
 * 并发事件只是起点相同或重叠，仍保留各自的位置。
 */
fun Beat.orderedEvents(): List<PerformanceEvent> =
    events.sortedBy { it.timing.startOffsetMillis }
