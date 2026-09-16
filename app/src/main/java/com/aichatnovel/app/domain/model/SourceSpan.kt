package com.aichatnovel.app.domain.model

/**
 * 原文溯源：把解析出来的内容指回原小说文本的位置。
 *
 * 无论 AI 如何改写或结构化，都必须保留它对应的原文位置，
 * 否则无法审校、无法增量重解析，也无法向用户展示依据。
 */
data class SourceSpan(
    val chapterId: String,
    val startOffset: Int,
    val endOffset: Int,
    val snippet: String,
)
