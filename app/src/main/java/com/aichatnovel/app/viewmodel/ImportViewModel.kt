package com.aichatnovel.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aichatnovel.app.AIChatNovelApplication
import com.aichatnovel.app.di.StoryImportMode
import com.aichatnovel.app.repository.StoryImportFailure
import com.aichatnovel.app.repository.StoryImportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 一次导入的状态。刻意区分五态：
 * 失败绝不能被当作成功展示，部分成功也必须把告警带出来。
 */
sealed interface ImportStatus {

    data object Idle : ImportStatus

    data object Importing : ImportStatus

    data class Success(
        val sceneCount: Int,
        val characterCount: Int,
        val schemaVersion: String,
    ) : ImportStatus

    data class Partial(
        val sceneCount: Int,
        val characterCount: Int,
        val schemaVersion: String,
        val warnings: List<String>,
    ) : ImportStatus

    data class Failure(
        val reason: StoryImportFailure,
        val message: String,
    ) : ImportStatus
}

data class ImportUiState(
    val mode: StoryImportMode = StoryImportMode.LOCAL_SAMPLE,
    val novelText: String = "",
    val status: ImportStatus = ImportStatus.Idle,
)

/**
 * 导入页 ViewModel。
 *
 * 只负责「发起导入 + 表达状态」：它不解析 JSON、不接触 DTO、不调用 Validator / Mapper。
 * 真正的解析由 [com.aichatnovel.app.repository.StoryImportRepository] 完成，
 * 成功的结果由数据层写入内容仓库，本类只负责把结果如实转成 UI 状态。
 *
 * [importStory] 是一个 suspend 函数依赖（由容器提供实现），便于测试注入五态结果。
 */
class ImportViewModel(
    defaultMode: StoryImportMode,
    private val sampleText: String,
    private val importStory: suspend (StoryImportMode, String) -> StoryImportResult,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState(mode = defaultMode))
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun onModeChange(mode: StoryImportMode) {
        _uiState.update { it.copy(mode = mode, status = ImportStatus.Idle) }
    }

    fun onNovelTextChange(text: String) {
        _uiState.update { it.copy(novelText = text, status = ImportStatus.Idle) }
    }

    fun useSampleText() {
        _uiState.update { it.copy(novelText = sampleText, status = ImportStatus.Idle) }
    }

    fun import() {
        val current = _uiState.value
        if (current.status == ImportStatus.Importing) return
        // 远程模式必须有原文；本地样例模式使用内置样例，忽略输入。
        if (current.mode == StoryImportMode.REMOTE_DEEPSEEK && current.novelText.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(status = ImportStatus.Importing) }
            val result = importStory(current.mode, current.novelText)
            _uiState.update { it.copy(status = result.toStatus()) }
        }
    }

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AIChatNovelApplication
                ImportViewModel(
                    defaultMode = app.container.config.storyImportMode,
                    sampleText = app.container.sampleNovelText,
                    importStory = { mode, text -> app.container.importStory(mode, text) },
                )
            }
        }
    }
}

private fun StoryImportResult.toStatus(): ImportStatus = when (this) {
    is StoryImportResult.Success -> ImportStatus.Success(
        sceneCount = content.scenes.size,
        characterCount = content.characters.size,
        schemaVersion = appliedSchemaVersion,
    )

    is StoryImportResult.Partial -> ImportStatus.Partial(
        sceneCount = content.scenes.size,
        characterCount = content.characters.size,
        schemaVersion = appliedSchemaVersion,
        warnings = validation.warnings.map { "${it.code}@${it.path}：${it.message}" },
    )

    is StoryImportResult.Failure -> ImportStatus.Failure(reason = reason, message = message)
}
