package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.repository.StoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 基于内存的 [StoryRepository] 实现。
 *
 * 作品 / 章节 / 场景**全部**来自 [StoryContentStore] 的当前导入快照，不再有任何静态兜底：
 * 尚未导入时 `observeStories` / `observeChapters` 返回空，这是正确行为——
 * 不能用样例数据去补出一个「看起来有内容」的界面。
 */
class InMemoryStoryRepository(
    private val store: StoryContentStore,
) : StoryRepository {

    override fun observeStories(): Flow<List<Story>> =
        store.imported.map { imported -> listOfNotNull(imported?.story) }

    override fun observeChapters(storyId: String): Flow<List<Chapter>> =
        store.imported.map { imported ->
            imported?.chapters.orEmpty()
                .filter { it.storyId == storyId }
                .sortedBy { it.index }
        }

    override fun observeScenes(chapterId: String): Flow<List<Scene>> =
        store.content.map { content ->
            content.scenes.filter { it.chapterId == chapterId }.sortedBy { it.index }
        }

    override fun observeScene(sceneId: String): Flow<Scene?> =
        store.content.map { content -> content.scenes.firstOrNull { it.id == sceneId } }
}
