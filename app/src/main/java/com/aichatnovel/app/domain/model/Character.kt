package com.aichatnovel.app.domain.model

/**
 * 小说中的角色。
 *
 * [aliases] 是角色在原文中的别称 / 称谓，用于 AI 解析时的同一角色消歧。
 * [defaultAvatarRef] 是角色默认立绘；具体场景中的立绘见 [CharacterSceneState.avatarRef]。
 * [voiceProfile] 是角色默认音色档案；[DialogueEvent.voiceOverride] 为空时继承它。
 */
data class Character(
    val id: String,
    val storyId: String,
    val name: String,
    val description: String,
    val aliases: List<String> = emptyList(),
    val defaultAvatarRef: AssetRef? = null,
    val voiceProfile: VoiceProfile? = null,
)
