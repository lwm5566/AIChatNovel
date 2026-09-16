package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.StoryContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程内的剧情内容仓库。
 *
 * 导入（本地样例或远程 DeepSeek）完成后由 `AppContainer` 写入，
 * 各 InMemory Repository 从它派生 Flow，UI 会随数据到达自动刷新。
 */
class StoryContentStore(initial: StoryContent = StoryContent()) {

    private val state = MutableStateFlow(initial)

    val content: StateFlow<StoryContent> = state.asStateFlow()

    fun replace(content: StoryContent) {
        state.value = content
    }
}
