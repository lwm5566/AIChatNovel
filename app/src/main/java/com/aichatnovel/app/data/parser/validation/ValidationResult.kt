package com.aichatnovel.app.data.parser.validation

enum class ValidationSeverity {
    ERROR,
    WARNING,
}

/** 校验项编码，便于测试与后续按编码展示提示。 */
enum class ValidationCode {
    UNPARSEABLE_RESPONSE,
    UNSUPPORTED_SCHEMA_VERSION,
    BLANK_CHAPTER_ID,
    DUPLICATE_CHARACTER_TEMP_ID,
    DUPLICATE_SCENE_ID,
    MISSING_SCENE_ID,
    UNKNOWN_PARTICIPANT,
    UNKNOWN_SPEAKER,
    UNKNOWN_ADDRESSEE,
    UNKNOWN_ACTOR,
    INVALID_SOURCE_SPAN_RANGE,
    NEGATIVE_START_OFFSET,
    NEGATIVE_DURATION,
    INVALID_DURATION_SOURCE,
    SNIPPET_MISMATCH,
    NON_MONOTONIC_BEAT_ORDER,
    UNKNOWN_EVENT_TYPE,
    INVALID_PRESENTATION_MODE,
    MISSING_PRESENTATION_EVIDENCE,
    DEGRADED_PRESENTATION_MODE,
}

data class ValidationIssue(
    val code: ValidationCode,
    val severity: ValidationSeverity,
    val path: String,
    val message: String,
)

/**
 * 校验结果。校验层只报告问题，**不修改 DTO**。
 */
data class ValidationResult(
    val issues: List<ValidationIssue> = emptyList(),
) {
    val errors: List<ValidationIssue> get() = issues.filter { it.severity == ValidationSeverity.ERROR }

    val warnings: List<ValidationIssue> get() = issues.filter { it.severity == ValidationSeverity.WARNING }

    val isValid: Boolean get() = errors.isEmpty()

    operator fun plus(other: ValidationResult): ValidationResult =
        ValidationResult(issues + other.issues)
}
