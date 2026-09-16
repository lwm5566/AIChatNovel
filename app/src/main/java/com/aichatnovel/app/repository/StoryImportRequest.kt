package com.aichatnovel.app.repository

/**
 * 一次导入请求。
 *
 * [novelText] 是待解析的小说原文；本地样例实现会忽略它并使用内置样例。
 *
 * [storyTitle] / [author] / [synopsis] / [chapterTitle] 是**用户在导入时显式提供**的作品与章节
 * 元信息（Phase 5B-2）。它们只决定最终快照里的展示字段，**不参与** storyId / chapterId 的生成、
 * 选择、查找或合并——归属仍然完全由 [chapterId] / [storyId] 决定。未填写（trim 后为空）时由
 * 协调层回退到明确的占位值。
 *
 * 本地样例实现会忽略整个 request（含这些字段），继续使用自身 fixture 的归属与元信息。
 */
data class StoryImportRequest(
    val chapterId: String,
    val novelText: String,
    val storyId: String? = null,
    val storyTitle: String? = null,
    val author: String? = null,
    val synopsis: String? = null,
    val chapterTitle: String? = null,
)
