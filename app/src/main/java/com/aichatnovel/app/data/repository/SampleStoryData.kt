package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Story

/**
 * 内置样例的归属常量（**sample fixture**，不是生产数据）。
 *
 * 只用于说明内置样例 JSON 里写的 story / chapter id 是什么。普通导入（本地样例或 DeepSeek）
 * 都**不再**拿它当默认归属——那正是 Phase 5B-1 修掉的 M2 根因。保留它，是为了让样例的归属
 * 在一处可读、可供测试引用。
 */
object SampleStoryData {

    const val STORY_ID = "story-1"
    const val CHAPTER_ID_1 = "chapter-1"
    const val CHAPTER_ID_2 = "chapter-2"

    val stories: List<Story> = listOf(
        Story(
            id = STORY_ID,
            title = "星海回声",
            author = "示例作者",
            synopsis = "两个习惯了沉默的人，在城市边缘的黄昏里互相试探。",
        ),
    )

    val chapters: List<Chapter> = listOf(
        Chapter(id = CHAPTER_ID_1, storyId = STORY_ID, index = 1, title = "第一章 启程"),
        Chapter(id = CHAPTER_ID_2, storyId = STORY_ID, index = 2, title = "第二章 讯号"),
    )
}
