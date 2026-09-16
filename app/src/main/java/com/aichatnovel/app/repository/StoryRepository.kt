package com.aichatnovel.app.repository

import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.Story
import kotlinx.coroutines.flow.Flow

/**
 * 小说内容（作品 / 章节 / 场景）的数据入口。
 *
 * 仅定义接口，具体实现位于 data 层，后续可替换为本地数据库或远端 API。
 */
interface StoryRepository {

    fun observeStories(): Flow<List<Story>>

    fun observeChapters(storyId: String): Flow<List<Chapter>>

    fun observeScenes(chapterId: String): Flow<List<Scene>>

    fun observeScene(sceneId: String): Flow<Scene?>
}
