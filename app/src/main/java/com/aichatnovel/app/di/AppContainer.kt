package com.aichatnovel.app.di

import com.aichatnovel.app.data.parser.sample.SampleParseSources
import com.aichatnovel.app.data.remote.deepseek.DeepSeekLogger
import com.aichatnovel.app.data.remote.deepseek.OkHttpDeepSeekApiClient
import com.aichatnovel.app.data.repository.ImportedStory
import com.aichatnovel.app.data.repository.InMemoryCharacterRepository
import com.aichatnovel.app.data.repository.InMemoryPerformanceRepository
import com.aichatnovel.app.data.repository.InMemorySettingsRepository
import com.aichatnovel.app.data.repository.InMemoryStoryRepository
import com.aichatnovel.app.data.repository.LocalSampleStoryImportRepository
import com.aichatnovel.app.data.repository.RemoteStoryImportRepository
import com.aichatnovel.app.data.repository.StoryContentStore
import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.domain.model.StoryContent
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
    private val importIdGenerator: ImportIdGenerator = SequentialImportIdGenerator(),
) {

    val storyContentStore = StoryContentStore()

    val storyRepository: StoryRepository = InMemoryStoryRepository(storyContentStore)

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
     * 执行一次导入。
     *
     * 每次导入都取一对**全新**的 storyId / chapterId，避免两次导入落进同一个作品 / 章节。
     * 成功 / 部分成功时，把「作品 + 章节 + 内容」作为同一份快照一次性写入 [storyContentStore]；
     * 失败不写入，调用方必须能看到失败原因。
     */
    suspend fun importStory(mode: StoryImportMode, novelText: String): StoryImportResult {
        val request = StoryImportRequest(
            chapterId = importIdGenerator.newChapterId(),
            storyId = importIdGenerator.newStoryId(),
            novelText = novelText,
        )

        val result = importRepositoryFor(mode).importStory(request)

        when (result) {
            is StoryImportResult.Success -> storyContentStore.replace(result.content.toImportedStory(mode, request))
            is StoryImportResult.Partial -> storyContentStore.replace(result.content.toImportedStory(mode, request))
            is StoryImportResult.Failure -> Unit
        }
        return result
    }

    /** 启动预载：用内置样例原文按配置模式导入一次，走与用户导入完全相同的写入路径。 */
    suspend fun importStory(): StoryImportResult = importStory(config.storyImportMode, sampleNovelText)
}

/**
 * 把一次导入的产出组装成「当前导入快照」。
 *
 * **归属（Story / Chapter）由导入方决定，不由模型回显决定**：
 *
 * - 普通导入（[StoryImportMode.REMOTE_DEEPSEEK]）：本次请求生成的 `storyId` / `chapterId` 是
 *   唯一 authoritative ID。本阶段 Remote 导入的语义就是「一章原文」，因此快照固定为一个 Chapter，
 *   并把 content 的归属**无条件**归一到这两个 ID——模型回显的 `storyId` / `chapterId` 只属于
 *   解析协议，不作为 ownership 依据（即使模型返回了错误值，也不影响最终归属）。
 * - 本地样例（[StoryImportMode.LOCAL_SAMPLE]）：它是明确的 fixture，忽略请求参数，归属由样例自身声明。
 *
 * 不重建 `Scene.id`、不动 `beatsByScene` 的 key，也不改 `SourceSpan.chapterId`（那是原文溯源信息，
 * 不属于本次 ownership normalization）。
 *
 * ⚠️ title / author 目前是**明确的占位值**，不是小说的真实 metadata：当前 AI 输出契约
 * （`ParseResponseDto`）里没有这些字段，本阶段**不编造**。占位 metadata 将在 Phase 5B-2
 * 由用户显式提供的信息替换。
 */
private fun StoryContent.toImportedStory(
    mode: StoryImportMode,
    request: StoryImportRequest,
): ImportedStory {
    val storyId: String
    val chapters: List<Chapter>
    val normalized: StoryContent

    when (mode) {
        StoryImportMode.REMOTE_DEEPSEEK -> {
            storyId = request.storyId.orEmpty()
            chapters = listOf(
                Chapter(
                    id = request.chapterId,
                    storyId = storyId,
                    index = 1,
                    title = PLACEHOLDER_CHAPTER_TITLE,
                ),
            )
            normalized = copy(
                characters = characters.map { it.copy(storyId = storyId) },
                scenes = scenes.map { it.copy(chapterId = request.chapterId) },
            )
        }

        StoryImportMode.LOCAL_SAMPLE -> {
            storyId = characters.map { it.storyId }.firstOrNull { it.isNotBlank() }
                ?: request.storyId.orEmpty()
            chapters = scenes.map { it.chapterId }.distinct().mapIndexed { index, chapterId ->
                Chapter(
                    id = chapterId,
                    storyId = storyId,
                    index = index + 1,
                    title = PLACEHOLDER_CHAPTER_TITLE,
                )
            }
            // 样例自身的声明就是它的归属，无需再做归一化
            normalized = this
        }
    }

    return ImportedStory(
        story = Story(
            id = storyId,
            title = PLACEHOLDER_STORY_TITLE,
            author = PLACEHOLDER_STORY_AUTHOR,
            synopsis = "",
        ),
        chapters = chapters,
        content = normalized,
    )
}

private const val PLACEHOLDER_STORY_TITLE = "未命名作品"
private const val PLACEHOLDER_STORY_AUTHOR = "未命名作者"
private const val PLACEHOLDER_CHAPTER_TITLE = "未命名章节"
