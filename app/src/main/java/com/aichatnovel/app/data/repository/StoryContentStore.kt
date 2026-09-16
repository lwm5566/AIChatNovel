package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.domain.model.StoryContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * 一次导入的完整快照：作品 / 章节 / 内容必须来自**同一次**导入。
 *
 * 它只是「当前导入」的载体，不是数据库：没有历史列表、没有多作品管理。
 * 三者一起替换，因此不会出现「A 的 metadata + B 的 content」这种混合状态。
 */
data class ImportedStory(
    val story: Story,
    val chapters: List<Chapter>,
    val content: StoryContent,
)

/**
 * 进程内的「当前导入」仓库。
 *
 * 导入（本地样例或远程 DeepSeek）完成后由 `AppContainer` 通过 [replace] 一次性写入，
 * 各 InMemory Repository 从它派生 Flow，UI 会随数据到达自动刷新。
 *
 * 只有 [imported] 是发布源，[content] 是它的**派生视图**。因此
 * 「快照与内容来自同一次导入」不靠调用顺序维持，而是结构上的必然：
 * 只有一条发布通道，就不存在「快照已换新、内容还是上一次」的中间窗口。
 */
class StoryContentStore(initial: ImportedStory? = null) {

    private val importedState = MutableStateFlow(initial)

    /** 当前导入快照；尚未导入过任何内容时为 null（此时 Story / Chapter 都应为空）。 */
    val imported: StateFlow<ImportedStory?> = importedState.asStateFlow()

    /** 当前内容，供按场景 / 角色 / 节拍取数的 Repository 使用。 */
    val content: Flow<StoryContent> = importedState.map { imported -> imported?.content ?: EMPTY_CONTENT }

    fun replace(imported: ImportedStory) {
        importedState.value = imported
    }

    private companion object {
        val EMPTY_CONTENT = StoryContent()
    }
}
