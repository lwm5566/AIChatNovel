package com.aichatnovel.app.data.parser.validation

import com.aichatnovel.app.data.parser.ParseSchema
import com.aichatnovel.app.data.parser.dto.ActionEventDto
import com.aichatnovel.app.data.parser.dto.DialogueEventDto
import com.aichatnovel.app.data.parser.dto.ParseResponseDto
import com.aichatnovel.app.data.parser.dto.PerformanceEventDto
import com.aichatnovel.app.data.parser.dto.SourceSpanDto
import com.aichatnovel.app.data.parser.dto.UnknownEventDto
import com.aichatnovel.app.domain.model.DurationSource
import com.aichatnovel.app.domain.model.PresentationMode

/**
 * AI 响应校验器。
 *
 * 只检查、只报告，不修改 DTO、不做降级——降级由 Mapper 负责。
 *
 * [chapterText] 可选：传入对应章节原文时，会额外校验 sourceSpan 是否越界、
 * snippet 是否与原文一致（用于确认偏移是真实字符位置，而不是编造的片段）。
 */
class ParseValidator(
    private val supportedSchemaVersions: Set<String> = ParseSchema.SUPPORTED,
) {

    private val validPresentationModes: Set<String> = PresentationMode.entries.map { it.name }.toSet()
    private val validDurationSources: Set<String> = DurationSource.entries.map { it.name }.toSet()

    fun validate(
        response: ParseResponseDto,
        chapterText: String? = null,
    ): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        if (response.schemaVersion !in supportedSchemaVersions) {
            issues += ValidationIssue(
                code = ValidationCode.UNSUPPORTED_SCHEMA_VERSION,
                severity = ValidationSeverity.ERROR,
                path = "schemaVersion",
                message = "不支持的 schemaVersion：${response.schemaVersion}（支持：${supportedSchemaVersions.sorted().joinToString()}）",
            )
        }

        if (response.chapterId.isBlank()) {
            issues += ValidationIssue(
                ValidationCode.BLANK_CHAPTER_ID,
                ValidationSeverity.ERROR,
                "chapterId",
                "chapterId 不能为空",
            )
        }

        val knownCharacterIds = response.characters.map { it.tempId }.toSet()
        response.characters
            .groupingBy { it.tempId }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .forEach { duplicate ->
                issues += ValidationIssue(
                    ValidationCode.DUPLICATE_CHARACTER_TEMP_ID,
                    ValidationSeverity.ERROR,
                    "characters[$duplicate]",
                    "角色 tempId 重复：$duplicate",
                )
            }

        response.scenes
            .mapNotNull { it.id ?: it.tempId }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .forEach { duplicate ->
                issues += ValidationIssue(
                    ValidationCode.DUPLICATE_SCENE_ID,
                    ValidationSeverity.ERROR,
                    "scenes[$duplicate]",
                    "场景 id 重复：$duplicate",
                )
            }

        response.scenes.forEachIndexed { sceneIndex, scene ->
            val path = "scenes[$sceneIndex]"

            if (scene.id.isNullOrBlank() && scene.tempId.isNullOrBlank()) {
                issues += ValidationIssue(
                    ValidationCode.MISSING_SCENE_ID,
                    ValidationSeverity.ERROR,
                    path,
                    "场景缺少 id / tempId",
                )
            }

            val mode = scene.presentationMode
            if (mode != null && mode !in validPresentationModes) {
                issues += ValidationIssue(
                    ValidationCode.INVALID_PRESENTATION_MODE,
                    ValidationSeverity.ERROR,
                    "$path.presentationMode",
                    "无效的呈现介质：$mode",
                )
            } else if (mode != null && mode != PresentationMode.LiveScene.name && scene.presentationEvidence == null) {
                issues += ValidationIssue(
                    ValidationCode.MISSING_PRESENTATION_EVIDENCE,
                    ValidationSeverity.WARNING,
                    "$path.presentationEvidence",
                    "呈现介质为 $mode 但缺少 presentationEvidence，Mapper 将降级为 ${PresentationMode.LiveScene.name}",
                )
            }

            scene.participants.forEach { participant ->
                if (participant !in knownCharacterIds) {
                    issues += ValidationIssue(
                        ValidationCode.UNKNOWN_PARTICIPANT,
                        ValidationSeverity.WARNING,
                        "$path.participants",
                        "participants 引用了未声明的角色：$participant",
                    )
                }
            }

            checkSourceSpan(scene.sourceSpan, "$path.sourceSpan", chapterText, issues)

            val orders = scene.beats.mapNotNull { it.order }
            if (orders.size != orders.distinct().size || orders != orders.sorted()) {
                issues += ValidationIssue(
                    ValidationCode.NON_MONOTONIC_BEAT_ORDER,
                    ValidationSeverity.WARNING,
                    "$path.beats",
                    "节拍 order 未按严格递增给出：$orders",
                )
            }

            scene.beats.forEachIndexed { beatIndex, beat ->
                beat.events.forEachIndexed { eventIndex, event ->
                    checkEvent(
                        event = event,
                        path = "$path.beats[$beatIndex].events[$eventIndex]",
                        knownCharacterIds = knownCharacterIds,
                        chapterText = chapterText,
                        issues = issues,
                    )
                }
            }
        }

        return ValidationResult(issues)
    }

    private fun checkEvent(
        event: PerformanceEventDto,
        path: String,
        knownCharacterIds: Set<String>,
        chapterText: String?,
        issues: MutableList<ValidationIssue>,
    ) {
        if (event is UnknownEventDto) {
            issues += ValidationIssue(
                ValidationCode.UNKNOWN_EVENT_TYPE,
                ValidationSeverity.ERROR,
                path,
                "未知的事件类型：${event.type ?: "(缺少 type)"}",
            )
            return
        }

        event.presentationOverride?.let { override ->
            if (override !in validPresentationModes) {
                issues += ValidationIssue(
                    ValidationCode.INVALID_PRESENTATION_MODE,
                    ValidationSeverity.ERROR,
                    "$path.presentationOverride",
                    "无效的呈现介质覆盖：$override",
                )
            }
        }

        event.timing?.let { timing ->
            if ((timing.startOffset ?: 0L) < 0L) {
                issues += ValidationIssue(
                    ValidationCode.NEGATIVE_START_OFFSET,
                    ValidationSeverity.ERROR,
                    "$path.timing.startOffset",
                    "startOffset 不能为负：${timing.startOffset}",
                )
            }
            val duration = timing.duration
            if (duration != null && duration < 0L) {
                issues += ValidationIssue(
                    ValidationCode.NEGATIVE_DURATION,
                    ValidationSeverity.ERROR,
                    "$path.timing.duration",
                    "duration 不能为负：$duration",
                )
            }
            val source = timing.durationSource
            if (source != null && source !in validDurationSources) {
                issues += ValidationIssue(
                    ValidationCode.INVALID_DURATION_SOURCE,
                    ValidationSeverity.ERROR,
                    "$path.timing.durationSource",
                    "无效的 durationSource：$source",
                )
            }
        }

        checkSourceSpan(event.sourceSpan, "$path.sourceSpan", chapterText, issues)

        when (event) {
            is DialogueEventDto -> {
                if (event.speakerTempId !in knownCharacterIds) {
                    issues += ValidationIssue(
                        ValidationCode.UNKNOWN_SPEAKER,
                        ValidationSeverity.WARNING,
                        "$path.speakerTempId",
                        "speakerTempId 未在 characters 中声明：${event.speakerTempId}",
                    )
                }
                val addressee = event.addresseeTempId
                if (addressee != null && addressee !in knownCharacterIds) {
                    issues += ValidationIssue(
                        ValidationCode.UNKNOWN_ADDRESSEE,
                        ValidationSeverity.WARNING,
                        "$path.addresseeTempId",
                        "addresseeTempId 未在 characters 中声明：$addressee",
                    )
                }
            }

            is ActionEventDto -> {
                val actor = event.characterTempId
                if (actor != null && actor !in knownCharacterIds) {
                    issues += ValidationIssue(
                        ValidationCode.UNKNOWN_ACTOR,
                        ValidationSeverity.WARNING,
                        "$path.characterTempId",
                        "characterTempId 未在 characters 中声明：$actor",
                    )
                }
            }

            else -> Unit
        }
    }

    private fun checkSourceSpan(
        span: SourceSpanDto?,
        path: String,
        chapterText: String?,
        issues: MutableList<ValidationIssue>,
    ) {
        if (span == null) return

        val start = span.startOffset
        val end = span.endOffset

        if (start == null || end == null) {
            issues += ValidationIssue(
                ValidationCode.INVALID_SOURCE_SPAN_RANGE,
                ValidationSeverity.ERROR,
                path,
                "sourceSpan 缺少 startOffset / endOffset",
            )
            return
        }

        if (start < 0) {
            issues += ValidationIssue(
                ValidationCode.NEGATIVE_START_OFFSET,
                ValidationSeverity.ERROR,
                "$path.startOffset",
                "startOffset 不能为负：$start",
            )
            return
        }

        if (end < start) {
            issues += ValidationIssue(
                ValidationCode.INVALID_SOURCE_SPAN_RANGE,
                ValidationSeverity.ERROR,
                path,
                "endOffset($end) 小于 startOffset($start)",
            )
            return
        }

        if (chapterText == null) return

        if (end > chapterText.length) {
            issues += ValidationIssue(
                ValidationCode.INVALID_SOURCE_SPAN_RANGE,
                ValidationSeverity.ERROR,
                path,
                "endOffset($end) 超出原文长度(${chapterText.length})",
            )
            return
        }

        val snippet = span.snippet
        if (snippet != null && chapterText.substring(start, end) != snippet) {
            issues += ValidationIssue(
                ValidationCode.SNIPPET_MISMATCH,
                ValidationSeverity.WARNING,
                path,
                "snippet 与原文 [$start,$end) 不一致，snippet「$snippet」",
            )
        }
    }
}
