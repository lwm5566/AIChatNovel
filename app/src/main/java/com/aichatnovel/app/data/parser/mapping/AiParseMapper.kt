package com.aichatnovel.app.data.parser.mapping

import com.aichatnovel.app.data.parser.dto.ActionEventDto
import com.aichatnovel.app.data.parser.dto.BeatDto
import com.aichatnovel.app.data.parser.dto.CameraEventDto
import com.aichatnovel.app.data.parser.dto.CharacterDto
import com.aichatnovel.app.data.parser.dto.DialogueEventDto
import com.aichatnovel.app.data.parser.dto.EnvironmentEventDto
import com.aichatnovel.app.data.parser.dto.NarrationEventDto
import com.aichatnovel.app.data.parser.dto.ParseResponseDto
import com.aichatnovel.app.data.parser.dto.PerformanceEventDto
import com.aichatnovel.app.data.parser.dto.PresentationEvidenceDto
import com.aichatnovel.app.data.parser.dto.SceneDto
import com.aichatnovel.app.data.parser.dto.SceneSettingDto
import com.aichatnovel.app.data.parser.dto.SoundEventDto
import com.aichatnovel.app.data.parser.dto.SourceSpanDto
import com.aichatnovel.app.data.parser.dto.SpeechParamsDto
import com.aichatnovel.app.data.parser.dto.TimingDto
import com.aichatnovel.app.data.parser.dto.UnknownEventDto
import com.aichatnovel.app.data.parser.validation.ValidationCode
import com.aichatnovel.app.data.parser.validation.ValidationIssue
import com.aichatnovel.app.data.parser.validation.ValidationSeverity
import com.aichatnovel.app.domain.model.Action
import com.aichatnovel.app.domain.model.ActionEvent
import com.aichatnovel.app.domain.model.AssetRef
import com.aichatnovel.app.domain.model.AssetType
import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.CameraEvent
import com.aichatnovel.app.domain.model.Character
import com.aichatnovel.app.domain.model.CharacterSceneState
import com.aichatnovel.app.domain.model.Dialogue
import com.aichatnovel.app.domain.model.DialogueEvent
import com.aichatnovel.app.domain.model.DurationSource
import com.aichatnovel.app.domain.model.Environment
import com.aichatnovel.app.domain.model.EnvironmentEvent
import com.aichatnovel.app.domain.model.Narration
import com.aichatnovel.app.domain.model.NarrationEvent
import com.aichatnovel.app.domain.model.PerformanceEvent
import com.aichatnovel.app.domain.model.PresentationEvidence
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.SceneSetting
import com.aichatnovel.app.domain.model.SoundEvent
import com.aichatnovel.app.domain.model.SourceSpan
import com.aichatnovel.app.domain.model.SpeechParams
import com.aichatnovel.app.domain.model.StoryContent
import com.aichatnovel.app.domain.model.Timeline
import com.aichatnovel.app.domain.model.Timing
import com.aichatnovel.app.domain.model.Utterance

/**
 * 一次映射的结果。
 *
 * [appliedSchemaVersion] 记录本次实际按哪个 schema 版本做的归一化（用于溯源），
 * [warnings] 是映射过程中产生的降级/忽略说明。
 */
data class MappingOutcome(
    val content: StoryContent,
    val appliedSchemaVersion: String,
    val warnings: List<ValidationIssue>,
)

/**
 * AI DTO → Domain Model 的映射器。
 *
 * 关键约定：
 * - tempId 只用于本次映射，最终写成由角色名派生的稳定 id；
 * - 缺省 presentationMode 一律是 LiveScene；
 * - **非 LiveScene 但没有 presentationEvidence 时，不静默接受**，而是降级为 LiveScene 并产生 warning；
 * - AI 不提供音色，因此 voiceOverride 恒为 null，运行时由角色 VoiceProfile 继承；
 * - AI 不提供最终音频时长，duration 允许为空，来源标为 Estimated。
 */
class AiParseMapper {

    fun map(response: ParseResponseDto): MappingOutcome {
        val warnings = mutableListOf<ValidationIssue>()

        val characterIdByTempId: Map<String, String> =
            response.characters.associate { it.tempId to stableCharacterId(it.name) }
        val storyId = response.storyId.orEmpty()

        val characters = response.characters
            .map { it.toDomain(stableCharacterId(it.name), storyId) }
            .distinctBy { it.id }

        val scenes = mutableListOf<Scene>()
        val beatsByScene = mutableMapOf<String, List<Beat>>()

        response.scenes.forEachIndexed { sceneIndex, sceneDto ->
            // 场景 id 同样以章节限定：不同章节的 tempId 互相独立，不能直接当全局 id 用。
            val sceneId = "${response.chapterId}-${sceneDto.stableLocalId()}"
            val mode = sceneDto.resolvePresentationMode(sceneId, warnings)

            scenes += Scene(
                id = sceneId,
                chapterId = response.chapterId,
                index = sceneIndex + 1,
                title = sceneDto.title,
                presentationMode = mode,
                presentationEvidence = sceneDto.presentationEvidence?.toDomain(response.chapterId),
                setting = sceneDto.setting.toDomain(),
                timeline = sceneDto.beats.estimateTimeline(),
                characters = sceneDto.participants
                    .mapNotNull { characterIdByTempId[it] }
                    .distinct()
                    .map { CharacterSceneState(characterId = it) },
            )

            beatsByScene[sceneId] = sceneDto.beats.mapIndexed { beatIndex, beatDto ->
                beatDto.toDomain(
                    sceneId = sceneId,
                    index = beatIndex,
                    characterIdByTempId = characterIdByTempId,
                    fallbackChapterId = response.chapterId,
                    warnings = warnings,
                )
            }
        }

        return MappingOutcome(
            content = StoryContent(characters = characters, scenes = scenes, beatsByScene = beatsByScene),
            appliedSchemaVersion = response.schemaVersion,
            warnings = warnings,
        )
    }

    /**
     * 呈现介质归一化。这里是 §PresentationMode 安全规则的执行点：
     * 只认「取值合法 + 非 LiveScene 时带证据」的结论，否则降级。
     */
    private fun SceneDto.resolvePresentationMode(
        sceneId: String,
        warnings: MutableList<ValidationIssue>,
    ): PresentationMode {
        val raw = presentationMode ?: return PresentationMode.LiveScene
        val mode = PresentationMode.entries.firstOrNull { it.name == raw }
        if (mode == null) {
            warnings += ValidationIssue(
                ValidationCode.INVALID_PRESENTATION_MODE,
                ValidationSeverity.WARNING,
                "scenes[$sceneId].presentationMode",
                "无效的呈现介质：$raw，已降级为 ${PresentationMode.LiveScene.name}",
            )
            return PresentationMode.LiveScene
        }
        if (mode == PresentationMode.LiveScene) return PresentationMode.LiveScene
        if (presentationEvidence == null) {
            warnings += ValidationIssue(
                ValidationCode.DEGRADED_PRESENTATION_MODE,
                ValidationSeverity.WARNING,
                "scenes[$sceneId].presentationEvidence",
                "呈现介质为 $raw 但缺少 presentationEvidence，已降级为 ${PresentationMode.LiveScene.name}",
            )
            return PresentationMode.LiveScene
        }
        return mode
    }

    private fun SceneDto.stableLocalId(): String =
        id?.takeIf { it.isNotBlank() } ?: tempId.orEmpty()

    private fun CharacterDto.toDomain(stableId: String, storyId: String): Character = Character(
        id = stableId,
        storyId = storyId,
        name = name,
        description = description.orEmpty(),
        aliases = aliases,
        defaultAvatarRef = null,
        voiceProfile = null,
    )

    private fun SceneSettingDto?.toDomain(): SceneSetting = SceneSetting(
        location = this?.location,
        timeOfDay = this?.timeOfDay,
        weather = this?.weather,
        lighting = this?.lighting,
        backgroundRef = this?.backgroundRef?.let { AssetRef(id = it, type = AssetType.IMAGE) },
    )

    private fun List<BeatDto>.estimateTimeline(): Timeline {
        val hasAnyDuration = any { beat -> beat.events.any { it.timing?.duration != null } }
        if (!hasAnyDuration) return Timeline()

        val total = sumOf { beat ->
            beat.events.maxOfOrNull { event ->
                (event.timing?.startOffset ?: 0L) + (event.timing?.duration ?: 0L)
            } ?: 0L
        }
        return Timeline(totalDurationMillis = total, durationSource = DurationSource.Estimated)
    }

    private fun BeatDto.toDomain(
        sceneId: String,
        index: Int,
        characterIdByTempId: Map<String, String>,
        fallbackChapterId: String,
        warnings: MutableList<ValidationIssue>,
    ): Beat = Beat(
        id = id?.takeIf { it.isNotBlank() } ?: "$sceneId-beat-${index + 1}",
        order = order ?: (index + 1),
        events = events.mapNotNull { eventDto ->
            eventDto.toDomain(characterIdByTempId, fallbackChapterId, warnings)
        },
    )

    private fun PerformanceEventDto.toDomain(
        characterIdByTempId: Map<String, String>,
        fallbackChapterId: String,
        warnings: MutableList<ValidationIssue>,
    ): PerformanceEvent? = when (this) {
        is DialogueEventDto -> {
            val speakerId = characterIdByTempId[speakerTempId] ?: unresolvedCharacterId(speakerTempId)
            DialogueEvent(
                id = id,
                timing = timing.toDomain(),
                dialogue = Dialogue(characterId = speakerId, text = text, emotion = emotion),
                utterance = Utterance(
                    speakerId = speakerId,
                    text = text,
                    emotion = emotion,
                    speakingStyle = speakingStyle,
                    addressee = addresseeTempId?.let { characterIdByTempId[it] },
                    isInnerMonologue = isInnerMonologue,
                    language = language,
                ),
                speech = speech?.toDomain(),
                voiceOverride = null,
                presentationOverride = presentationOverride.toPresentationModeOrNull(warnings),
                sourceSpan = sourceSpan?.toDomain(fallbackChapterId),
            )
        }

        is NarrationEventDto -> NarrationEvent(
            id = id,
            timing = timing.toDomain(),
            narration = Narration(text),
            presentationOverride = presentationOverride.toPresentationModeOrNull(warnings),
            sourceSpan = sourceSpan?.toDomain(fallbackChapterId),
        )

        is ActionEventDto -> ActionEvent(
            id = id,
            timing = timing.toDomain(),
            action = Action(
                characterId = characterTempId?.let { characterIdByTempId[it] },
                description = description,
            ),
            presentationOverride = presentationOverride.toPresentationModeOrNull(warnings),
            sourceSpan = sourceSpan?.toDomain(fallbackChapterId),
        )

        is EnvironmentEventDto -> EnvironmentEvent(
            id = id,
            timing = timing.toDomain(),
            environment = Environment(description = text, location = location),
            presentationOverride = presentationOverride.toPresentationModeOrNull(warnings),
            sourceSpan = sourceSpan?.toDomain(fallbackChapterId),
        )

        is SoundEventDto -> SoundEvent(
            id = id,
            timing = timing.toDomain(),
            description = description,
            soundRef = null,
            presentationOverride = presentationOverride.toPresentationModeOrNull(warnings),
            sourceSpan = sourceSpan?.toDomain(fallbackChapterId),
        )

        is CameraEventDto -> CameraEvent(
            id = id,
            timing = timing.toDomain(),
            description = description,
            shotRef = null,
            presentationOverride = presentationOverride.toPresentationModeOrNull(warnings),
            sourceSpan = sourceSpan?.toDomain(fallbackChapterId),
        )

        is UnknownEventDto -> null
    }

    private fun TimingDto?.toDomain(): Timing {
        val duration = this?.duration
        val durationSource = this?.durationSource
            ?.let { raw -> DurationSource.entries.firstOrNull { it.name == raw } }
            ?: if (duration != null) DurationSource.Estimated else null

        return Timing(
            startOffsetMillis = this?.startOffset ?: 0L,
            durationMillis = duration,
            durationSource = durationSource,
        )
    }

    private fun SpeechParamsDto.toDomain(): SpeechParams = SpeechParams(
        rate = rate,
        pitch = pitch,
        volume = volume,
    )

    private fun SourceSpanDto.toDomain(fallbackChapterId: String): SourceSpan? {
        val start = startOffset ?: return null
        val end = endOffset ?: return null
        return SourceSpan(
            chapterId = chapterId?.takeIf { it.isNotBlank() } ?: fallbackChapterId,
            startOffset = start,
            endOffset = end,
            snippet = snippet.orEmpty(),
        )
    }

    private fun PresentationEvidenceDto.toDomain(
        fallbackChapterId: String,
    ): PresentationEvidence = PresentationEvidence(
        text = text.orEmpty(),
        sourceSpan = sourceSpan?.toDomain(fallbackChapterId),
    )

    private fun String?.toPresentationModeOrNull(warnings: MutableList<ValidationIssue>): PresentationMode? {
        if (this == null) return null
        val mode = PresentationMode.entries.firstOrNull { it.name == this }
        if (mode == null) {
            warnings += ValidationIssue(
                ValidationCode.INVALID_PRESENTATION_MODE,
                ValidationSeverity.WARNING,
                "presentationOverride",
                "无效的呈现介质覆盖：$this，已忽略该覆盖",
            )
        }
        return mode
    }

    private fun stableCharacterId(name: String): String =
        "char-" + name.trim().lowercase().replace(Regex("\\s+"), "-")

    private fun unresolvedCharacterId(tempId: String): String = "char-unresolved-$tempId"
}
