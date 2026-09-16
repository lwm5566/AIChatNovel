package com.aichatnovel.app.domain.model

/**
 * 一次导入（解析）产出的领域内容。
 *
 * 它是解析管线的输出单位，也是 Repository 与 UI 之间的交换单位：
 * 角色、场景、以及按场景组织的演出节拍。作品与章节的元信息由 Story / Chapter 表达。
 */
data class StoryContent(
    val characters: List<Character> = emptyList(),
    val scenes: List<Scene> = emptyList(),
    val beatsByScene: Map<String, List<Beat>> = emptyMap(),
)
