package com.aichatnovel.app.domain.mapping

import com.aichatnovel.app.domain.model.Character
import com.aichatnovel.app.domain.model.DialogueEvent
import com.aichatnovel.app.domain.model.NarrationEvent
import com.aichatnovel.app.domain.model.PerformanceEvent
import com.aichatnovel.app.domain.model.SpeechParams
import com.aichatnovel.app.domain.model.TtsRequest
import com.aichatnovel.app.domain.model.TtsRunConfig
import com.aichatnovel.app.domain.model.VoiceProfile

/**
 * 这次演出要用哪段文本发声。
 *
 * 只有**对白**与**旁白**会发声：动作 / 环境 / 环境音 / 镜头不是「被念出来的话」。
 * 对白优先用演出层文本（[com.aichatnovel.app.domain.model.Utterance.text]），
 * 它为空时回落剧情层台词。
 */
fun PerformanceEvent.speakableText(): String? = when (this) {
    is DialogueEvent -> utterance.text.ifBlank { dialogue.text }
    is NarrationEvent -> narration.text
    else -> null
}

/**
 * 这次发声用哪个音色。
 *
 * - 对白 → 角色音色档案（事件级 `voiceOverride` 优先），缺失时用运行配置的兜底音色；
 * - 旁白 → 运行配置里的旁白音色。**旁白没有角色，绝不虚构一个 Character 来承载它。**
 */
fun PerformanceEvent.speakableVoiceId(
    characterProfile: VoiceProfile?,
    config: TtsRunConfig,
): String? = when (this) {
    is DialogueEvent -> effectiveVoiceProfile(characterProfile)?.voiceRef ?: config.fallbackVoiceId
    is NarrationEvent -> config.narrationVoiceId ?: config.fallbackVoiceId
    else -> null
}

/** 本次发声的最终参数：角色（或事件）默认值被事件级 `speech` 覆盖。 */
fun PerformanceEvent.speakableSpeechParams(characterProfile: VoiceProfile?): SpeechParams = when (this) {
    is DialogueEvent -> (effectiveVoiceProfile(characterProfile)?.defaultSpeech ?: SpeechParams())
        .overriddenBy(speech)
    else -> SpeechParams()
}

/**
 * 把一次演出事件映射成统一的 [TtsRequest]。
 *
 * @return 不需要发声的事件（动作 / 环境 / 环境音 / 镜头）或文本为空时返回 null。
 */
fun PerformanceEvent.toTtsRequest(
    character: Character?,
    config: TtsRunConfig,
): TtsRequest? {
    val text = speakableText()?.takeIf { it.isNotBlank() } ?: return null
    return TtsRequest(
        text = text,
        voiceId = speakableVoiceId(character?.voiceProfile, config),
        locale = config.locale,
        speechParams = speakableSpeechParams(character?.voiceProfile),
        format = config.format,
    )
}

private fun SpeechParams.overriddenBy(override: SpeechParams?): SpeechParams =
    if (override == null) {
        this
    } else {
        SpeechParams(
            rate = override.rate ?: rate,
            pitch = override.pitch ?: pitch,
            volume = override.volume ?: volume,
        )
    }
