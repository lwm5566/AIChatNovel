package com.aichatnovel.app.di

import com.aichatnovel.app.data.parser.sample.SampleParseSources
import com.aichatnovel.app.data.remote.deepseek.DeepSeekLogger
import com.aichatnovel.app.data.remote.deepseek.OkHttpDeepSeekApiClient
import com.aichatnovel.app.data.repository.InMemoryCharacterRepository
import com.aichatnovel.app.data.repository.InMemoryPerformanceRepository
import com.aichatnovel.app.data.repository.InMemorySettingsRepository
import com.aichatnovel.app.data.repository.InMemoryStoryRepository
import com.aichatnovel.app.data.repository.LocalSampleStoryImportRepository
import com.aichatnovel.app.data.repository.RemoteStoryImportRepository
import com.aichatnovel.app.data.repository.SampleStoryData
import com.aichatnovel.app.data.repository.StoryContentStore
import com.aichatnovel.app.repository.CharacterRepository
import com.aichatnovel.app.repository.PerformanceRepository
import com.aichatnovel.app.repository.SettingsRepository
import com.aichatnovel.app.repository.StoryImportRepository
import com.aichatnovel.app.repository.StoryImportRequest
import com.aichatnovel.app.repository.StoryImportResult
import com.aichatnovel.app.repository.StoryRepository

/**
 * 手工依赖容器。
 *
 * 导入实现由 [AppConfig.storyImportMode] 选择：
 * - [StoryImportMode.LOCAL_SAMPLE] → 读内置样例；
 * - [StoryImportMode.REMOTE_DEEPSEEK] → 真实调用 DeepSeek。
 *
 * 两种模式共用同一套校验/映射管线，也共用同一套下游 Repository 与 UI。
 */
class AppContainer(
    val config: AppConfig = AppConfig.fromBuildConfig(),
    logger: DeepSeekLogger = DeepSeekLogger.NoOp,
) {

    val storyContentStore = StoryContentStore()

    val storyRepository: StoryRepository = InMemoryStoryRepository(
        store = storyContentStore,
        stories = SampleStoryData.stories,
        chapters = SampleStoryData.chapters,
    )

    val characterRepository: CharacterRepository = InMemoryCharacterRepository(storyContentStore)

    val performanceRepository: PerformanceRepository = InMemoryPerformanceRepository(storyContentStore)

    val settingsRepository: SettingsRepository = InMemorySettingsRepository()

    private val localSampleImportRepository: StoryImportRepository = LocalSampleStoryImportRepository()

    private val remoteImportRepository: StoryImportRepository = RemoteStoryImportRepository(
        apiClient = OkHttpDeepSeekApiClient(config = config.deepSeek, logger = logger),
        config = config.deepSeek,
        logger = logger,
    )

    /** 内置样例原文，供导入界面「填充示例原文」使用。 */
    val sampleNovelText: String = SampleParseSources.liveScene.chapterText

    private fun importRepositoryFor(mode: StoryImportMode): StoryImportRepository = when (mode) {
        StoryImportMode.LOCAL_SAMPLE -> localSampleImportRepository
        StoryImportMode.REMOTE_DEEPSEEK -> remoteImportRepository
    }

    /**
     * 执行一次导入，成功 / 部分成功时写入 [storyContentStore]（UI 会随之刷新）。
     *
     * 两种模式共用同一套校验 / 映射管线与同一个下游内容仓库，调用方只选择模式。
     * 失败时不写入，调用方必须能看到失败原因。
     */
    suspend fun importStory(mode: StoryImportMode, novelText: String): StoryImportResult {
        val result = importRepositoryFor(mode).importStory(
            StoryImportRequest(
                chapterId = SampleStoryData.CHAPTER_ID_1,
                storyId = SampleStoryData.STORY_ID,
                novelText = novelText,
            ),
        )

        when (result) {
            is StoryImportResult.Success -> storyContentStore.replace(result.content)
            is StoryImportResult.Partial -> storyContentStore.replace(result.content)
            is StoryImportResult.Failure -> Unit
        }
        return result
    }

    /** 启动预载：用内置样例原文按配置模式导入一次。 */
    suspend fun importStory(): StoryImportResult = importStory(config.storyImportMode, sampleNovelText)
}
