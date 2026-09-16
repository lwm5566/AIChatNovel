package com.aichatnovel.app.di

import android.content.Context
import com.aichatnovel.app.data.ai.DeepSeekTextProvider
import com.aichatnovel.app.data.audio.AndroidAudioDurationProbe
import com.aichatnovel.app.data.audio.DefaultSceneAudioGenerator
import com.aichatnovel.app.data.audio.FileAudioStorage
import com.aichatnovel.app.data.audio.Media3AudioPlayer
import com.aichatnovel.app.data.audio.NoOpAudioPlayer
import com.aichatnovel.app.data.parser.sample.SampleParseSources
import com.aichatnovel.app.data.remote.deepseek.DeepSeekLogger
import com.aichatnovel.app.data.remote.deepseek.OkHttpDeepSeekApiClient
import com.aichatnovel.app.data.repository.ImportedStory
import com.aichatnovel.app.data.repository.InMemoryAudioAssetRepository
import com.aichatnovel.app.data.repository.InMemoryCharacterRepository
import com.aichatnovel.app.data.repository.InMemoryPerformanceRepository
import com.aichatnovel.app.data.repository.InMemoryProviderCredentialStore
import com.aichatnovel.app.data.repository.InMemorySettingsRepository
import com.aichatnovel.app.data.repository.InMemoryStoryRepository
import com.aichatnovel.app.data.repository.LocalSampleStoryImportRepository
import com.aichatnovel.app.data.repository.RemoteStoryImportRepository
import com.aichatnovel.app.data.repository.StoryContentStore
import com.aichatnovel.app.data.tts.MicrosoftAzureTtsProvider
import com.aichatnovel.app.data.tts.VolcengineTtsProvider
import com.aichatnovel.app.data.tts.XiaomiMiMoTtsProvider
import com.aichatnovel.app.domain.model.Chapter
import com.aichatnovel.app.domain.model.Story
import com.aichatnovel.app.domain.model.StoryContent
import com.aichatnovel.app.domain.model.TtsProviderId
import com.aichatnovel.app.repository.AudioAssetRepository
import com.aichatnovel.app.repository.AudioDurationProbe
import com.aichatnovel.app.repository.AudioPlayer
import com.aichatnovel.app.repository.AudioStorage
import com.aichatnovel.app.repository.CharacterRepository
import com.aichatnovel.app.repository.PerformanceRepository
import com.aichatnovel.app.repository.ProviderCredentialStore
import com.aichatnovel.app.repository.SceneAudioGenerator
import com.aichatnovel.app.repository.SettingsRepository
import com.aichatnovel.app.repository.StoryImportRepository
import com.aichatnovel.app.repository.StoryImportRequest
import com.aichatnovel.app.repository.StoryImportResult
import com.aichatnovel.app.repository.StoryRepository
import com.aichatnovel.app.repository.TtsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

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
    private val context: Context? = null,
) {

    val storyContentStore = StoryContentStore()

    val storyRepository: StoryRepository = InMemoryStoryRepository(storyContentStore)

    val characterRepository: CharacterRepository = InMemoryCharacterRepository(storyContentStore)

    val performanceRepository: PerformanceRepository = InMemoryPerformanceRepository(storyContentStore)

    val settingsRepository: SettingsRepository = InMemorySettingsRepository()

    // ---- Phase 6C：语音与音频 ----

    /** 已生成音频的索引（eventId → AudioAsset）。 */
    val audioAssetRepository: AudioAssetRepository = InMemoryAudioAssetRepository()

    /** 运行时凭据（内存，不落盘）。 */
    val credentialStore: ProviderCredentialStore = InMemoryProviderCredentialStore()

    private val audioStorage: AudioStorage = context
        ?.let { FileAudioStorage(File(it.filesDir, AUDIO_DIR_NAME)) }
        ?: UnavailableAudioStorage

    private val durationProbe: AudioDurationProbe = context
        ?.let { AndroidAudioDurationProbe() }
        ?: UnavailableAudioDurationProbe

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(AUDIO_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(AUDIO_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** 三个 TTS 供应商共用同一个 HTTP 栈，只有各自的 DTO 与认证分开。 */
    private val ttsProviders: Map<TtsProviderId, TtsProvider> = mapOf(
        TtsProviderId.AZURE to MicrosoftAzureTtsProvider(credentialStore, httpClient),
        TtsProviderId.VOLCENGINE to VolcengineTtsProvider(credentialStore, httpClient),
        TtsProviderId.XIAOMI_MIMO to XiaomiMiMoTtsProvider(credentialStore, httpClient),
    )

    /** 场景语音生成编排：ViewModel 与 UI 都不直接碰 provider。 */
    val sceneAudioGenerator: SceneAudioGenerator = DefaultSceneAudioGenerator(
        providers = ttsProviders,
        credentialStore = credentialStore,
        audioStorage = audioStorage,
        durationProbe = durationProbe,
        audioAssetRepository = audioAssetRepository,
    )

    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 真实音频播放器；未注入 Android Context 时退化为不做任何事的占位实现。 */
    val audioPlayer: AudioPlayer by lazy {
        context?.let { Media3AudioPlayer(it, audioScope) } ?: NoOpAudioPlayer()
    }

    private val localSampleImportRepository: StoryImportRepository = LocalSampleStoryImportRepository()

    private val remoteImportRepository: StoryImportRepository = RemoteStoryImportRepository(
        textProvider = DeepSeekTextProvider(
            apiClient = OkHttpDeepSeekApiClient(config = config.deepSeek, logger = logger),
            config = config.deepSeek,
        ),
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
     * [storyTitle] / [author] / [synopsis] / [chapterTitle] 是用户显式提供的作品与章节元信息，
     * 只决定快照里的展示字段，**不参与**归属 ID 的生成；留空（trim 后为空）时回退到占位值。
     * 成功 / 部分成功时，把「作品 + 章节 + 内容」作为同一份快照一次性写入 [storyContentStore]；
     * 失败不写入，调用方必须能看到失败原因。
     */
    suspend fun importStory(
        mode: StoryImportMode,
        novelText: String,
        storyTitle: String? = null,
        author: String? = null,
        synopsis: String? = null,
        chapterTitle: String? = null,
    ): StoryImportResult {
        val request = StoryImportRequest(
            chapterId = importIdGenerator.newChapterId(),
            storyId = importIdGenerator.newStoryId(),
            novelText = novelText,
            storyTitle = storyTitle,
            author = author,
            synopsis = synopsis,
            chapterTitle = chapterTitle,
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
 * **元信息（metadata）与归属（ownership）严格分离**：metadata（`storyTitle` / `author` / `synopsis` /
 * `chapterTitle`）来自用户导入时的显式输入，只决定展示字段，**不参与** id 的生成、选择、查找或合并：
 * - Remote：使用用户输入；未填写（trim 后为空）时回退到占位值（`未命名作品` / `未命名作者` /
 *   `未命名章节`，synopsis 为空串）。**从不**把空白字符串写进 Domain。
 * - 本地样例：fixture 语义，**忽略** request 携带的 metadata，继续使用占位值。
 */
private fun StoryContent.toImportedStory(
    mode: StoryImportMode,
    request: StoryImportRequest,
): ImportedStory = when (mode) {
    StoryImportMode.REMOTE_DEEPSEEK -> {
        val storyId = request.storyId.orEmpty()
        val chapterId = request.chapterId

        ImportedStory(
            story = Story(
                id = storyId,
                title = request.storyTitle.orPlaceholder(PLACEHOLDER_STORY_TITLE),
                author = request.author.orPlaceholder(PLACEHOLDER_STORY_AUTHOR),
                synopsis = request.synopsis.orEmpty().trim(),
            ),
            chapters = listOf(
                Chapter(
                    id = chapterId,
                    storyId = storyId,
                    index = 1,
                    title = request.chapterTitle.orPlaceholder(PLACEHOLDER_CHAPTER_TITLE),
                ),
            ),
            content = copy(
                characters = characters.map { it.copy(storyId = storyId) },
                scenes = scenes.map { it.copy(chapterId = chapterId) },
            ),
        )
    }

    StoryImportMode.LOCAL_SAMPLE -> {
        // 本地样例是 fixture：**忽略 request 携带的元信息**，继续使用自身声明的归属与占位元信息。
        val storyId = characters.map { it.storyId }.firstOrNull { it.isNotBlank() }
            ?: request.storyId.orEmpty()

        ImportedStory(
            story = Story(
                id = storyId,
                title = PLACEHOLDER_STORY_TITLE,
                author = PLACEHOLDER_STORY_AUTHOR,
                synopsis = "",
            ),
            chapters = scenes.map { it.chapterId }.distinct().mapIndexed { index, chapterId ->
                Chapter(
                    id = chapterId,
                    storyId = storyId,
                    index = index + 1,
                    title = PLACEHOLDER_CHAPTER_TITLE,
                )
            },
            content = this,
        )
    }
}

/** 用户未填写（null / 空白）时回退到明确的占位值；**从不**把空白字符串写进 Domain。 */
private fun String?.orPlaceholder(placeholder: String): String =
    this?.trim().orEmpty().ifBlank { placeholder }

private const val AUDIO_DIR_NAME = "audio"
private const val AUDIO_CONNECT_TIMEOUT_SECONDS = 20L
private const val AUDIO_READ_TIMEOUT_SECONDS = 120L

/** 未注入 Android Context 时（例如单元测试）的音频存储：明确失败，不静默写到错误位置。 */
private object UnavailableAudioStorage : AudioStorage {

    override suspend fun save(fileName: String, bytes: ByteArray): String =
        throw IOException("当前平台没有可用的音频存储")

    override suspend fun delete(reference: String) = Unit

    override suspend fun deleteAll() = Unit
}

/** 未注入 Android Context 时无法探测真实时长：一律返回 null，不伪造。 */
private object UnavailableAudioDurationProbe : AudioDurationProbe {

    override suspend fun probe(reference: String): Long? = null
}

private const val PLACEHOLDER_STORY_TITLE = "未命名作品"
private const val PLACEHOLDER_STORY_AUTHOR = "未命名作者"
private const val PLACEHOLDER_CHAPTER_TITLE = "未命名章节"
