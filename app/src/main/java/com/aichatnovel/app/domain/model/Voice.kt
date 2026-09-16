package com.aichatnovel.app.domain.model

/**
 * 一次发声的表演层描述：怎么把这句话「演」出来。
 *
 * 与 [Dialogue] 的分工：
 * - [Dialogue] 是剧情层的事实——原文里那句台词是什么。
 * - [Utterance] 是演出层的指令——这一次由谁、以什么情绪、什么风格、念给谁听。
 *
 * 两者默认一一对应（由 [Dialogue] 派生），但允许不同：例如旁白转述某人的话时，
 * 说话者与台词来源并不一致；口语化演出也可能对文本做轻微调整。
 */
data class Utterance(
    val speakerId: String? = null,
    val text: String,
    val emotion: String? = null,
    val speakingStyle: String? = null,
    val addressee: String? = null,
    val isInnerMonologue: Boolean = false,
    val language: String? = null,
)

/**
 * 发声参数。字段允许为空，表示使用默认值。不绑定任何具体 TTS 服务。
 */
data class SpeechParams(
    val rate: Float? = null,
    val pitch: Float? = null,
    val volume: Float? = null,
)

/**
 * 音色档案：描述某个角色的默认音色，以及默认的 [SpeechParams]。
 *
 * [voiceRef] 只是一个音色标识，未来由音频模块决定如何解析，领域层不关心。
 */
data class VoiceProfile(
    val voiceRef: String? = null,
    val defaultSpeech: SpeechParams = SpeechParams(),
)
