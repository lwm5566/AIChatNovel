package com.aichatnovel.app.domain.model

/**
 * 场景设定：这个场景发生在哪里、什么时间、什么天气、什么光线，以及背景资源。
 *
 * 它是给视觉 / 视频模块用的舞台信息，与剧情事件本身分开描述。
 */
data class SceneSetting(
    val location: String? = null,
    val timeOfDay: String? = null,
    val weather: String? = null,
    val lighting: String? = null,
    val backgroundRef: AssetRef? = null,
)

/**
 * 某个 [Character] 在某个 [Scene] 中的状态。
 *
 * 同一角色在不同场景里的立绘、表情、服装、位置都可能不同，
 * 因此这些状态挂在「角色 × 场景」上，而不是角色本身。
 */
data class CharacterSceneState(
    val characterId: String,
    val position: String? = null,
    val facing: String? = null,
    val expression: String? = null,
    val costume: String? = null,
    val avatarRef: AssetRef? = null,
)
