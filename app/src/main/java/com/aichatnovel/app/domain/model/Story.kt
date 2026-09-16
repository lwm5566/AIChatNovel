package com.aichatnovel.app.domain.model

/**
 * 一部小说作品，是内容组织的根节点。
 *
 * 层级关系为 [Story] -> [Chapter] -> [Scene] -> [Beat] -> [PerformanceEvent]。
 */
data class Story(
    val id: String,
    val title: String,
    val author: String,
    val synopsis: String,
)

/**
 * 章节，是 [Scene] 的容器。
 */
data class Chapter(
    val id: String,
    val storyId: String,
    val index: Int,
    val title: String,
)

/**
 * 场景，是剧情发生的一个时空单位，例如「黄昏的教室」「雨夜的街道」。
 *
 * 场景是角色对白与演出事件发生的舞台，而不是一个聊天会话：
 * [presentationMode] 说明这段剧情以什么方式发生，默认 [PresentationMode.LiveScene]；
 * 只有当原文明确写出角色通过即时通讯工具交流时，才是 [PresentationMode.InstantMessaging]。
 *
 * 场景本身只承载舞台信息（[setting]、[characters]、[timeline]），
 * 具体的演出节拍由 [Beat] 承载，可按需单独加载。
 */
data class Scene(
    val id: String,
    val chapterId: String,
    val index: Int,
    val title: String,
    val presentationMode: PresentationMode = PresentationMode.LiveScene,
    val presentationEvidence: PresentationEvidence? = null,
    val setting: SceneSetting = SceneSetting(),
    val timeline: Timeline = Timeline(),
    val characters: List<CharacterSceneState> = emptyList(),
)
