package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.repository.PerformanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 基于内存的 [PerformanceRepository] 实现，数据来自 [StoryContentStore]。
 */
class InMemoryPerformanceRepository(
    private val store: StoryContentStore,
) : PerformanceRepository {

    override fun observeBeats(sceneId: String): Flow<List<Beat>> =
        store.content.map { content ->
            content.beatsByScene[sceneId].orEmpty().sortedBy { it.order }
        }
}
