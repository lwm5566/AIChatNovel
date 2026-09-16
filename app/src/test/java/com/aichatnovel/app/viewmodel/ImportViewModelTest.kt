package com.aichatnovel.app.viewmodel

import com.aichatnovel.app.data.parser.validation.ValidationCode
import com.aichatnovel.app.data.parser.validation.ValidationIssue
import com.aichatnovel.app.data.parser.validation.ValidationResult
import com.aichatnovel.app.data.parser.validation.ValidationSeverity
import com.aichatnovel.app.di.StoryImportMode
import com.aichatnovel.app.domain.model.Character
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.StoryContent
import com.aichatnovel.app.repository.StoryImportFailure
import com.aichatnovel.app.repository.StoryImportResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 导入状态机：五态必须如实表达，失败不得被当作成功。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is idle with the configured default mode`() = runTest(dispatcher) {
        val viewModel = viewModel { _, _ -> success() }

        assertEquals(ImportStatus.Idle, viewModel.uiState.value.status)
        assertEquals(StoryImportMode.LOCAL_SAMPLE, viewModel.uiState.value.mode)
        assertEquals("", viewModel.uiState.value.novelText)
    }

    @Test
    fun `successful import exposes counts and schema version`() = runTest(dispatcher) {
        val viewModel = viewModel { _, _ -> success() }
        viewModel.useSampleText()

        viewModel.import()

        val status = viewModel.uiState.value.status
        assertTrue("期望 Success，实际 $status", status is ImportStatus.Success)
        status as ImportStatus.Success
        assertEquals(2, status.sceneCount)
        assertEquals(1, status.characterCount)
        assertEquals("1.0", status.schemaVersion)
    }

    @Test
    fun `partial import keeps the warnings visible`() = runTest(dispatcher) {
        val viewModel = viewModel { _, _ -> partial() }

        viewModel.import()

        val status = viewModel.uiState.value.status
        assertTrue("期望 Partial，实际 $status", status is ImportStatus.Partial)
        status as ImportStatus.Partial
        assertEquals(2, status.sceneCount)
        assertEquals(1, status.warnings.size)
        assertTrue(status.warnings.single().contains("MISSING_PRESENTATION_EVIDENCE"))
    }

    @Test
    fun `failed import is never reported as success`() = runTest(dispatcher) {
        val viewModel = viewModel { _, _ ->
            StoryImportResult.Failure(StoryImportFailure.HTTP_ERROR, "DeepSeek 返回 HTTP 500")
        }

        viewModel.import()

        val status = viewModel.uiState.value.status
        assertTrue("期望 Failure，实际 $status", status is ImportStatus.Failure)
        status as ImportStatus.Failure
        assertEquals(StoryImportFailure.HTTP_ERROR, status.reason)
        assertEquals("DeepSeek 返回 HTTP 500", status.message)
    }

    @Test
    fun `deepseek without credential surfaces a missing api key failure`() = runTest(dispatcher) {
        val viewModel = viewModel { mode, _ ->
            if (mode == StoryImportMode.REMOTE_DEEPSEEK) {
                StoryImportResult.Failure(
                    StoryImportFailure.MISSING_API_KEY,
                    "未配置 DeepSeek API Key，未发起请求",
                )
            } else {
                success()
            }
        }

        viewModel.onModeChange(StoryImportMode.REMOTE_DEEPSEEK)
        viewModel.onNovelTextChange("黄昏时分，林晚独自站在教室窗边。")
        viewModel.import()

        val status = viewModel.uiState.value.status
        assertTrue("期望 Failure，实际 $status", status is ImportStatus.Failure)
        assertEquals(StoryImportFailure.MISSING_API_KEY, (status as ImportStatus.Failure).reason)
    }

    @Test
    fun `status is importing while the import is still in flight`() = runTest(dispatcher) {
        val gate = CompletableDeferred<StoryImportResult>()
        val viewModel = viewModel { _, _ -> gate.await() }

        viewModel.import()
        assertEquals(ImportStatus.Importing, viewModel.uiState.value.status)

        gate.complete(success())
        assertTrue(viewModel.uiState.value.status is ImportStatus.Success)
    }

    @Test
    fun `remote mode with blank text does not call the repository`() = runTest(dispatcher) {
        var calls = 0
        val viewModel = viewModel { _, _ ->
            calls++
            success()
        }

        viewModel.onModeChange(StoryImportMode.REMOTE_DEEPSEEK)
        viewModel.import()

        assertEquals(0, calls)
        assertEquals(ImportStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `filling the sample text clears a previous failure`() = runTest(dispatcher) {
        val viewModel = viewModel { _, _ -> StoryImportResult.Failure(StoryImportFailure.NETWORK, "无法连接") }

        viewModel.import()
        assertTrue(viewModel.uiState.value.status is ImportStatus.Failure)

        viewModel.useSampleText()

        assertEquals(ImportStatus.Idle, viewModel.uiState.value.status)
        assertEquals("黄昏的样例原文", viewModel.uiState.value.novelText)
    }

    private fun viewModel(
        importStory: suspend (StoryImportMode, String) -> StoryImportResult,
    ): ImportViewModel = ImportViewModel(
        defaultMode = StoryImportMode.LOCAL_SAMPLE,
        sampleText = "黄昏的样例原文",
        importStory = importStory,
    )

    private fun success(): StoryImportResult = StoryImportResult.Success(
        validation = ValidationResult(),
        content = content(),
        appliedSchemaVersion = "1.0",
    )

    private fun partial(): StoryImportResult = StoryImportResult.Partial(
        validation = ValidationResult(
            listOf(
                ValidationIssue(
                    code = ValidationCode.MISSING_PRESENTATION_EVIDENCE,
                    severity = ValidationSeverity.WARNING,
                    path = "$.scenes[0]",
                    message = "缺少呈现介质依据，已降级为 LiveScene",
                ),
            ),
        ),
        content = content(),
        appliedSchemaVersion = "1.0",
    )

    private fun content(): StoryContent = StoryContent(
        characters = listOf(
            Character(id = "char-林晚", storyId = "story-1", name = "林晚", description = ""),
        ),
        scenes = listOf(
            Scene(id = "chapter-1-s1", chapterId = "chapter-1", index = 1, title = "黄昏的教室"),
            Scene(id = "chapter-1-s2", chapterId = "chapter-1", index = 2, title = "走廊"),
        ),
    )
}
