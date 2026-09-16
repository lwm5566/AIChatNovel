package com.aichatnovel.app.repository

import com.aichatnovel.app.data.parser.validation.ValidationResult
import com.aichatnovel.app.domain.model.StoryContent

/** 导入失败的原因，用于 UI 展示与测试断言。 */
enum class StoryImportFailure {
    MISSING_API_KEY,
    NETWORK,
    TIMEOUT,
    HTTP_ERROR,
    MALFORMED_ENVELOPE,
    EMPTY_RESPONSE,
    MARKDOWN_RESPONSE,
    NOT_JSON_OBJECT,
    INVALID_JSON,
    VALIDATION_ERROR,
}

/**
 * 导入结果。刻意区分三态：
 * - [Success]：产出内容且没有 warning；
 * - [Partial]：产出内容，但存在 warning（例如呈现介质被降级）；
 * - [Failure]：未产出内容，调用方必须能看到原因。
 */
sealed interface StoryImportResult {

    val validation: ValidationResult

    data class Success(
        override val validation: ValidationResult,
        val content: StoryContent,
        val appliedSchemaVersion: String,
    ) : StoryImportResult

    data class Partial(
        override val validation: ValidationResult,
        val content: StoryContent,
        val appliedSchemaVersion: String,
    ) : StoryImportResult

    data class Failure(
        val reason: StoryImportFailure,
        val message: String,
        override val validation: ValidationResult = ValidationResult(),
    ) : StoryImportResult
}
