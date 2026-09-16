package com.aichatnovel.app.data.parser.validation

import com.aichatnovel.app.data.parser.dto.BeatDto
import com.aichatnovel.app.data.parser.dto.CharacterDto
import com.aichatnovel.app.data.parser.dto.DialogueEventDto
import com.aichatnovel.app.data.parser.dto.ParseResponseDto
import com.aichatnovel.app.data.parser.dto.PresentationEvidenceDto
import com.aichatnovel.app.data.parser.dto.SceneDto
import com.aichatnovel.app.data.parser.dto.SourceSpanDto
import com.aichatnovel.app.data.parser.dto.TimingDto
import com.aichatnovel.app.data.parser.dto.UnknownEventDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseValidatorTest {

    private val validator = ParseValidator()

    @Test
    fun `unsupported schema version is an error`() {
        val result = validator.validate(response(schemaVersion = "2.0"))

        assertEquals(1, result.errors.size)
        assertEquals(ValidationCode.UNSUPPORTED_SCHEMA_VERSION, result.errors.single().code)
    }

    @Test
    fun `duplicate character tempId is an error`() {
        val result = validator.validate(
            response(
                characters = listOf(
                    CharacterDto(tempId = "c1", name = "林晚"),
                    CharacterDto(tempId = "c1", name = "陆沉"),
                ),
            ),
        )

        assertTrue(result.errors.any { it.code == ValidationCode.DUPLICATE_CHARACTER_TEMP_ID })
    }

    @Test
    fun `duplicate scene id is an error`() {
        val result = validator.validate(
            response(scenes = listOf(scene(tempId = "s1"), scene(tempId = "s1"))),
        )

        assertTrue(result.errors.any { it.code == ValidationCode.DUPLICATE_SCENE_ID })
    }

    @Test
    fun `participant referencing unknown character is a warning`() {
        val result = validator.validate(
            response(scenes = listOf(scene(participants = listOf("nobody")))),
        )

        assertTrue(result.warnings.any { it.code == ValidationCode.UNKNOWN_PARTICIPANT })
        assertTrue(result.isValid)
    }

    @Test
    fun `unknown speaker and addressee are warnings`() {
        val result = validator.validate(
            response(
                characters = listOf(CharacterDto(tempId = "c1", name = "林晚")),
                scenes = listOf(
                    scene(
                        participants = listOf("c1"),
                        beats = listOf(
                            BeatDto(
                                order = 1,
                                events = listOf(
                                    DialogueEventDto(
                                        id = "e1",
                                        speakerTempId = "ghost",
                                        addresseeTempId = "nobody",
                                        text = "喂",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(result.warnings.any { it.code == ValidationCode.UNKNOWN_SPEAKER })
        assertTrue(result.warnings.any { it.code == ValidationCode.UNKNOWN_ADDRESSEE })
    }

    @Test
    fun `negative start offset and duration are errors`() {
        val result = validator.validate(
            response(
                scenes = listOf(
                    scene(
                        beats = listOf(
                            BeatDto(
                                order = 1,
                                events = listOf(
                                    DialogueEventDto(
                                        id = "e1",
                                        timing = TimingDto(startOffset = -5, duration = -100),
                                        speakerTempId = "c1",
                                        text = "喂",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(result.errors.any { it.code == ValidationCode.NEGATIVE_START_OFFSET })
        assertTrue(result.errors.any { it.code == ValidationCode.NEGATIVE_DURATION })
    }

    @Test
    fun `source span out of chapter text range is an error`() {
        val result = validator.validate(
            response(
                scenes = listOf(
                    scene(
                        sourceSpan = SourceSpanDto(
                            chapterId = "chapter-1",
                            startOffset = 0,
                            endOffset = 999,
                            snippet = "超出范围",
                        ),
                    ),
                ),
            ),
            chapterText = "很短的原文",
        )

        assertTrue(result.errors.any { it.code == ValidationCode.INVALID_SOURCE_SPAN_RANGE })
    }

    @Test
    fun `snippet mismatching the chapter text is a warning`() {
        val text = "黄昏把教室染成暖橘色。"
        val result = validator.validate(
            response(
                scenes = listOf(
                    scene(
                        sourceSpan = SourceSpanDto(
                            chapterId = "chapter-1",
                            startOffset = 0,
                            endOffset = 5,
                            snippet = "完全不一样的片段",
                        ),
                    ),
                ),
            ),
            chapterText = text,
        )

        assertTrue(result.warnings.any { it.code == ValidationCode.SNIPPET_MISMATCH })
        assertTrue(result.isValid)
    }

    @Test
    fun `non monotonic beat order is a warning`() {
        val result = validator.validate(
            response(
                scenes = listOf(
                    scene(beats = listOf(BeatDto(order = 2), BeatDto(order = 1))),
                ),
            ),
        )

        assertTrue(result.warnings.any { it.code == ValidationCode.NON_MONOTONIC_BEAT_ORDER })
    }

    @Test
    fun `unknown event type is an error`() {
        val result = validator.validate(
            response(
                scenes = listOf(
                    scene(
                        beats = listOf(
                            BeatDto(order = 1, events = listOf(UnknownEventDto(id = "e1", type = "singing"))),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(result.errors.any { it.code == ValidationCode.UNKNOWN_EVENT_TYPE })
    }

    @Test
    fun `invalid presentation mode is an error`() {
        val result = validator.validate(
            response(scenes = listOf(scene(presentationMode = "GroupChat"))),
        )

        assertTrue(result.errors.any { it.code == ValidationCode.INVALID_PRESENTATION_MODE })
    }

    @Test
    fun `non live scene without evidence is a warning`() {
        val result = validator.validate(
            response(scenes = listOf(scene(presentationMode = "PhoneCall"))),
        )

        assertTrue(result.warnings.any { it.code == ValidationCode.MISSING_PRESENTATION_EVIDENCE })
        assertTrue(result.isValid)
    }

    @Test
    fun `non live scene with evidence passes`() {
        val result = validator.validate(
            response(
                scenes = listOf(
                    scene(
                        presentationMode = "PhoneCall",
                        evidence = PresentationEvidenceDto(
                            text = "他拨通了电话",
                            sourceSpan = SourceSpanDto(
                                chapterId = "chapter-1",
                                startOffset = 0,
                                endOffset = 2,
                                snippet = "他拨",
                            ),
                        ),
                    ),
                ),
            ),
            chapterText = "他拨通了电话。",
        )

        assertEquals(emptyList<Any>(), result.errors)
        assertTrue(result.warnings.isEmpty())
    }

    private fun response(
        schemaVersion: String = "1.0",
        chapterId: String = "chapter-1",
        characters: List<CharacterDto> = emptyList(),
        scenes: List<SceneDto> = emptyList(),
    ) = ParseResponseDto(
        schemaVersion = schemaVersion,
        storyId = "story-1",
        chapterId = chapterId,
        characters = characters,
        scenes = scenes,
    )

    private fun scene(
        id: String? = null,
        tempId: String? = "s1",
        presentationMode: String? = null,
        evidence: PresentationEvidenceDto? = null,
        participants: List<String> = emptyList(),
        sourceSpan: SourceSpanDto? = null,
        beats: List<BeatDto> = emptyList(),
    ) = SceneDto(
        id = id,
        tempId = tempId,
        title = "场景",
        presentationMode = presentationMode,
        presentationEvidence = evidence,
        setting = null,
        participants = participants,
        sourceSpan = sourceSpan,
        confidence = null,
        beats = beats,
    )
}
