package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Story

/**
 * 作品与章节的元信息。
 *
 * 角色、场景、演出节拍都改由解析管线产出（见 `LocalSampleStoryImportRepository`），
 * 这里只保留「作品 / 章节」这类不属于 AI 解析产物的信息。
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
