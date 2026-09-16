package com.aichatnovel.app.ui.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 解析结果页的 LazyColumn key。
 *
 * Domain 里 Beat / Event 的 id 直接来自模型输出、未按场景限定，不同 Scene 可能给出相同 id；
 * 这些 key 必须仍然全树唯一，否则 Compose 会抛 duplicate key。
 */
class StoryExplorerKeysTest {

    @Test
    fun `same beat id in different scenes yields distinct keys`() {
        val keys = listOf("chapter-1-s1", "chapter-2-s1").map { sceneId -> beatUiKey(sceneId, "b1") }

        assertNotEquals(keys[0], keys[1])
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `same event id in different scenes yields distinct keys`() {
        val keys = listOf("chapter-1-s1", "chapter-2-s1").flatMap { sceneId ->
            listOf(eventUiKey(sceneId, "b1", "e1"), eventUiKey(sceneId, "b1", "e2"))
        }

        assertEquals(4, keys.size)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `reused beat and event ids across scenes keep the whole key set unique`() {
        // 最坏情况：两个场景给出完全相同的 beat / event id
        val scenes = listOf("chapter-1-s1", "chapter-2-s1")
        val keys = scenes.flatMap { sceneId ->
            listOf(beatUiKey(sceneId, "b1"), beatUiKey(sceneId, "b2")) +
                listOf(
                    eventUiKey(sceneId, "b1", "e1"),
                    eventUiKey(sceneId, "b1", "e2"),
                    eventUiKey(sceneId, "b2", "e1"),
                )
        }

        assertEquals(10, keys.size)
        assertEquals(keys.size, keys.toSet().size)
    }
}
