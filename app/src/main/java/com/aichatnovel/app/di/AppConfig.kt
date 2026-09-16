package com.aichatnovel.app.di

import com.aichatnovel.app.BuildConfig
import com.aichatnovel.app.data.remote.deepseek.DeepSeekConfig

/** 剧情数据的来源模式。 */
enum class StoryImportMode {

    /** 内置样例（离线、回归测试、无 API Key 时的开发模式）。 */
    LOCAL_SAMPLE,

    /** 真实调用 DeepSeek。 */
    REMOTE_DEEPSEEK,
}

/**
 * 应用级配置。
 *
 * [storyImportMode] 等非敏感项来自 BuildConfig（可由 local.properties 或 -P 覆盖）；
 * **API Key 不在 BuildConfig 中**，只从环境变量/系统属性等运行时来源读取。
 */
data class AppConfig(
    val storyImportMode: StoryImportMode,
    val deepSeek: DeepSeekConfig,
) {
    companion object {

        fun fromBuildConfig(): AppConfig = AppConfig(
            storyImportMode = when (BuildConfig.STORY_IMPORT_MODE.trim().lowercase()) {
                "remote", "remote_deepseek", "deepseek" -> StoryImportMode.REMOTE_DEEPSEEK
                else -> StoryImportMode.LOCAL_SAMPLE
            },
            deepSeek = DeepSeekConfig.fromEnvironment(
                baseUrl = BuildConfig.DEEPSEEK_BASE_URL,
                model = BuildConfig.DEEPSEEK_MODEL,
            ),
        )
    }
}
