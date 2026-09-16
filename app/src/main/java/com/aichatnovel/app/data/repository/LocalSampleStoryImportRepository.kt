package com.aichatnovel.app.data.repository

import com.aichatnovel.app.data.parser.ParseSchema
import com.aichatnovel.app.data.parser.StoryParsePipeline
import com.aichatnovel.app.data.parser.sample.SampleParseSource
import com.aichatnovel.app.data.parser.sample.SampleParseSources
import com.aichatnovel.app.data.parser.validation.ValidationResult
import com.aichatnovel.app.domain.model.StoryContent
import com.aichatnovel.app.repository.StoryImportFailure
import com.aichatnovel.app.repository.StoryImportRepository
import com.aichatnovel.app.repository.StoryImportRequest
import com.aichatnovel.app.repository.StoryImportResult

/**
 * 读取内置样例（假 AI 响应 + 原文）并跑完整解析管线的实现。
 *
 * 全程本地、离线可用：读 classpath 资源 → JSON → DTO → 校验 → 归一化 → Domain Model。
 * [StoryImportRequest] 会被忽略——样例自带章节归属，此处只是沿用同一套接口。
 */
class LocalSampleStoryImportRepository(
    private val sources: List<SampleParseSource> = SampleParseSources.all,
    private val pipeline: StoryParsePipeline = StoryParsePipeline(),
) : StoryImportRepository {

    override suspend fun importStory(request: StoryImportRequest): StoryImportResult {
        val results = sources.map { pipeline.parse(it.responseJson, it.chapterText) }
        val validation = results.fold(ValidationResult()) { acc, result -> acc + result.validation }
        val contents = results.mapNotNull { it.content }

        if (contents.size != results.size) {
            val detail = validation.errors.joinToString { "${it.code}@${it.path}" }
            return StoryImportResult.Failure(
                StoryImportFailure.VALIDATION_ERROR,
                "内置样例未通过校验：$detail",
                validation,
            )
        }

        val content = StoryContent(
            characters = contents.flatMap { it.characters }.distinctBy { it.id },
            scenes = contents.flatMap { it.scenes },
            beatsByScene = contents.flatMap { it.beatsByScene.entries }.associate { it.key to it.value },
        )

        return if (validation.warnings.isEmpty()) {
            StoryImportResult.Success(validation, content, ParseSchema.CURRENT)
        } else {
            StoryImportResult.Partial(validation, content, ParseSchema.CURRENT)
        }
    }
}
