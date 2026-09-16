package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.domain.model.StoryContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「当前导入」快照与内容必须始终来自同一次导入。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryContentStoreTest {

    @Test
    fun `nothing is exposed before the first import`() = runTest {
        val store = StoryContentStore()

        assertNull(store.imported.value)
        assertEquals(StoryContent(), store.content.first())
    }

    @Test
    fun `an initial snapshot is visible through both views`() = runTest {
        val snapshot = importedStory(storyId = "story-initial", chapterId = "chapter-initial")
        val store = StoryContentStore(snapshot)

        assertEquals(snapshot, store.imported.value)
        assertEquals(snapshot.content, store.content.first())
    }

    @Test
    fun `replacing the snapshot replaces the content in the same step`() = runTest {
        val store = StoryContentStore()
        val snapshot = importedStory(storyId = "story-1", chapterId = "chapter-1")

        store.replace(snapshot)

        assertEquals(snapshot, store.imported.value)
        assertEquals(snapshot.content, store.content.first())
    }

    /**
     * 原子性回归：一旦观察到 imported 已换新，content 就必须已经是同一份快照的内容。
     * 若两者是两条独立发布通道，这里会观察到「新快照 + 旧内容」的中间窗口。
     */
    @Test
    fun `content never lags behind the imported snapshot`() = runTest {
        val store = StoryContentStore()
        val first = importedStory(storyId = "story-1", chapterId = "chapter-1")
        val second = importedStory(storyId = "story-2", chapterId = "chapter-2")

        val observed = mutableListOf<Pair<ImportedStory?, StoryContent>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            store.imported.collect { imported ->
                observed += imported to store.content.first()
            }
        }

        store.replace(first)
        store.replace(second)
        job.cancel()

        assertEquals(listOf(null, first, second), observed.map { it.first })
        assertEquals(
            "观察到的内容必须与当时的快照同源",
            listOf(StoryContent(), first.content, second.content),
            observed.map { it.second },
        )
    }

    private fun importedStory(storyId: String, chapterId: String): ImportedStory {
        val story = Story(id = storyId, title = "作品", author = "作者", synopsis = "")
        return ImportedStory(
            story = story,
            chapters = listOf(Chapter(id = chapterId, storyId = storyId, index = 1, title = "章节")),
            content = StoryContent(
                scenes = listOf(
                    Scene(id = "$chapterId-scene-1", chapterId = chapterId, index = 1, title = "场景"),
                ),
            ),
        )
    }
}
