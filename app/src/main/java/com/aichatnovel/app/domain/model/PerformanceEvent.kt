package com.aichatnovel.app.domain.model

/**
 * 场景中的一个演出事件，是播放剧情时的最小单位。
 *
 * 它描述「舞台上发生了什么」，而不是一条聊天消息：
 * 只有当剧情本身明确发生在微信 / QQ / 短信等聊天软件里时，
 * 相关对白才应被呈现为聊天消息，其余情况一律按场景中的演出处理。
 *
 * 事件在所属 [Beat] 内的时间位置由 [timing] 决定，同一节拍内允许多个事件并发。
 */
sealed interface PerformanceEvent {
    val id: String

    /** 事件在所属 [Beat] 内的时间位置。 */
    val timing: Timing

    /**
     * 呈现介质覆盖。为空表示继承所属 [Scene.presentationMode]。
     * 只有剧情明确发生媒介切换时才允许覆盖。
     */
    val presentationOverride: PresentationMode?

    /** 原文溯源，允许为空（例如由演出编排额外补充的环境音、镜头）。 */
    val sourceSpan: SourceSpan?
}

/**
 * 剧情中的一句对白。
 *
 * 它是「某个角色在某个场景中说出的话」，是剧情层的事实，不是即时通讯消息，
 * 也不承载 TTS 参数（那些属于 [Utterance] / [SpeechParams]）。
 */
data class Dialogue(
    val characterId: String,
    val text: String,
    val emotion: String? = null,
)

/** 旁白：交代背景、心理活动或非角色直接说出的叙述文字。 */
data class Narration(
    val text: String,
)

/** 角色动作。[characterId] 为空表示群体动作或未指名的动作。 */
data class Action(
    val characterId: String? = null,
    val description: String,
)

/** 环境描述：场景的氛围、光线、天气等环境信息。 */
data class Environment(
    val description: String,
    val location: String? = null,
)

/**
 * 某个角色说话。
 *
 * 分层结构：
 * - [dialogue]：剧情层事实（谁、说了什么）
 * - [utterance]：演出层指令（怎么念、念给谁听）
 * - [speech]：这次发声的参数，为空则用音色档案的默认值
 * - [voiceOverride]：为空则继承该角色 [Character.voiceProfile] 的默认音色
 */
data class DialogueEvent(
    override val id: String,
    override val timing: Timing,
    val dialogue: Dialogue,
    val utterance: Utterance,
    val speech: SpeechParams? = null,
    val voiceOverride: VoiceProfile? = null,
    override val presentationOverride: PresentationMode? = null,
    override val sourceSpan: SourceSpan? = null,
) : PerformanceEvent

/** 旁白。 */
data class NarrationEvent(
    override val id: String,
    override val timing: Timing,
    val narration: Narration,
    override val presentationOverride: PresentationMode? = null,
    override val sourceSpan: SourceSpan? = null,
) : PerformanceEvent

/** 角色动作。 */
data class ActionEvent(
    override val id: String,
    override val timing: Timing,
    val action: Action,
    override val presentationOverride: PresentationMode? = null,
    override val sourceSpan: SourceSpan? = null,
) : PerformanceEvent

/** 环境描述。 */
data class EnvironmentEvent(
    override val id: String,
    override val timing: Timing,
    val environment: Environment,
    override val presentationOverride: PresentationMode? = null,
    override val sourceSpan: SourceSpan? = null,
) : PerformanceEvent

/** 环境音。[soundRef] 为空表示尚未绑定音频资源。 */
data class SoundEvent(
    override val id: String,
    override val timing: Timing,
    val description: String,
    val soundRef: AssetRef? = null,
    override val presentationOverride: PresentationMode? = null,
    override val sourceSpan: SourceSpan? = null,
) : PerformanceEvent

/** 镜头 / 画面变化。[shotRef] 为空表示尚未绑定画面资源。 */
data class CameraEvent(
    override val id: String,
    override val timing: Timing,
    val description: String,
    val shotRef: AssetRef? = null,
    override val presentationOverride: PresentationMode? = null,
    override val sourceSpan: SourceSpan? = null,
) : PerformanceEvent
