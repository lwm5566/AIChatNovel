package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.domain.model.StoryContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * [imported] 与 [content] 只在 [replace] 中被同步更新，二者永远来自同一次导入。
 */
class StoryContentStore(initial: ImportedStory? = null) {

    private val importedState = MutableStateFlow(initial)

    private val contentState = MutableStateFlow(initial?.content ?: StoryContent())

    /** 当前导入快照；尚未导入过任何内容时为 null（此时 Story / Chapter 都应为空）。 */
    val imported: StateFlow<ImportedStory?> = importedState.asStateFlow()

    /** 当前内容，供按场景 / 角色 / 节拍取数的 Repository 使用。 */
    val content: StateFlow<StoryContent> = contentState.asStateFlow()

    fun replace(imported: ImportedStory) {
        importedState.value = imported
        contentState.value = imported.content
    }
}
