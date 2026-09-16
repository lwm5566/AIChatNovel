package com.aichatnovel.app

import android.app.Application
import android.util.Log
import com.aichatnovel.app.data.remote.deepseek.DeepSeekLogger
import com.aichatnovel.app.di.AppContainer
import com.aichatnovel.app.repository.StoryImportResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口，持有全局依赖容器，并在启动时触发一次剧情导入。
 *
 * 导入失败不会崩溃：只记录日志，UI 保持空状态。
 */
class AIChatNovelApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val container: AppContainer by lazy {
        AppContainer(
            logger = DeepSeekLogger { stage, message -> Log.d(LOG_TAG, "$stage $message") },
            context = applicationContext,
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            when (val result = container.importStory()) {
                is StoryImportResult.Success -> Log.i(
                    LOG_TAG,
                    "剧情导入成功：scenes=${result.content.scenes.size} characters=${result.content.characters.size} " +
                        "schema=${result.appliedSchemaVersion}",
                )

                is StoryImportResult.Partial -> Log.w(
                    LOG_TAG,
                    "剧情导入完成但存在告警：warnings=${result.validation.warnings.size}",
                )

                is StoryImportResult.Failure -> Log.e(
                    LOG_TAG,
                    "剧情导入失败 reason=${result.reason} message=${result.message}",
                )
            }
        }
    }

    private companion object {
        const val LOG_TAG = "AIChatNovel"
    }
}
