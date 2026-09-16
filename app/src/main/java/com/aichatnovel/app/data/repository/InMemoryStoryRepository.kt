package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.repository.StoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * 基于内存的 [StoryRepository] 实现。
 *
 * 作品与章节元信息来自 [SampleStoryData]；场景来自 [StoryContentStore]（解析管线写入）。
 */
class InMemoryStoryRepository(
    private val store: StoryContentStore,
    private val stories: List<Story> = emptyList(),
    private val chapters: List<Chapter> = emptyList(),
) : StoryRepository {

    override fun observeStories(): Flow<List<Story>> = flowOf(stories)

    override fun observeChapters(storyId: String): Flow<List<Chapter>> =
        flowOf(chapters.filter { it.storyId == storyId }.sortedBy { it.index })

    override fun observeScenes(chapterId: String): Flow<List<Scene>> =
        store.content.map { content ->
            content.scenes.filter { it.chapterId == chapterId }.sortedBy { it.index }
        }

    override fun observeScene(sceneId: String): Flow<Scene?> =
        store.content.map { content -> content.scenes.firstOrNull { it.id == sceneId } }
}
