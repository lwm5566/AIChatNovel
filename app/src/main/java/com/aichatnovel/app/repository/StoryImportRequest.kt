package com.aichatnovel.app.repository

/**
 * 一次导入请求。
 *
 * [novelText] 是待解析的小说原文；本地样例实现会忽略它并使用内置样例。
 */
data class StoryImportRequest(
    val chapterId: String,
    val novelText: String,
    val storyId: String? = null,
)
