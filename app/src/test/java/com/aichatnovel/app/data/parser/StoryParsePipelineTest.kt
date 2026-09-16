package com.aichatnovel.app.data.parser

import com.aichatnovel.app.data.parser.sample.SampleParseSources
import com.aichatnovel.app.data.parser.validation.ValidationCode
import com.aichatnovel.app.data.parser.validation.ValidationSeverity
import com.aichatnovel.app.domain.model.ActionEvent
import com.aichatnovel.app.domain.model.CameraEvent
import com.aichatnovel.app.domain.model.DialogueEvent
import com.aichatnovel.app.domain.model.DurationSource
import com.aichatnovel.app.domain.model.EnvironmentEvent
import com.aichatnovel.app.domain.model.NarrationEvent
import com.aichatnovel.app.domain.model.PresentationMode
import com.aichatnovel.app.domain.model.SoundEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryParsePipelineTest {

    private val pipeline = StoryParsePipeline()

    private val liveScene get() = SampleParseSources.liveScene
    private val instantMessaging get() = SampleParseSources.instantMessaging

    @Test
    fun `normal sample json maps to domain model`() {
        val source = liveScene
        val result = pipeline.parse(source.responseJson, source.chapterText)

        assertEquals(emptyList<Any>(), result.validation.errors)
        val content = requireNotNull(result.content)

        assertEquals(listOf("char-林晚", "char-陆沉"), content.characters.map { it.id })
        assertEquals(listOf("小晚", "转学生"), content.characters[0].aliases)

        val scene = content.scenes.single()
        assertEquals("chapter-1-s1", scene.id)
        assertEquals("chapter-1", scene.chapterId)
        assertEquals("黄昏的教室", scene.title)
        assertEquals(PresentationMode.LiveScene, scene.presentationMode)
        assertEquals("城南高中 三年二班", scene.setting.location)
        assertEquals(DurationSource.Estimated, scene.timeline.durationSource)
    }

    @Test
    fun `tempId is replaced by stable character id`() {
        val source = liveScene
        val content = requireNotNull(pipeline.parse(source.responseJson, source.chapterText).content)

        val dialogues = content.beatsByScene.values.flatten()
            .flatMap { it.events }
            .filterIsInstance<DialogueEvent>()

        assertTrue(dialogues.isNotEmpty())
        assertTrue(dialogues.all { it.dialogue.characterId.startsWith("char-") })
        assertTrue(dialogues.none { it.dialogue.characterId == "c1" || it.dialogue.characterId == "c2" })

        val first = dialogues.first()
        assertEquals("char-林晚", first.dialogue.characterId)
        assertEquals("char-林晚", first.utterance.speakerId)
        assertEquals("char-陆沉", first.utterance.addressee)
    }

    @Test
    fun `live scene keeps LiveScene without degradation warning`() {
        val source = liveScene
        val result = pipeline.parse(source.responseJson, source.chapterText)
        val scene = requireNotNull(result.content).scenes.single()

        assertEquals(PresentationMode.LiveScene, scene.presentationMode)
        assertEquals("放学后的教室里只剩下两个人。", scene.presentationEvidence?.sourceSpan?.snippet)
        assertTrue(result.validation.warnings.none { it.code == ValidationCode.DEGRADED_PRESENTATION_MODE })
    }

    @Test
    fun `instant messaging scene keeps InstantMessaging and evidence`() {
        val source = instantMessaging
        val result = pipeline.parse(source.responseJson, source.chapterText)

        assertEquals(emptyList<Any>(), result.validation.errors)
        val scene = requireNotNull(result.content).scenes.single()

        assertEquals(PresentationMode.InstantMessaging, scene.presentationMode)
        assertEquals("林晚拿起手机，打开微信，给陆沉发消息", scene.presentationEvidence?.text)
        assertEquals(3, scene.presentationEvidence?.sourceSpan?.startOffset)

        val dialogues = requireNotNull(result.content).beatsByScene.getValue("chapter-2-s1")
            .flatMap { it.events }
            .filterIsInstance<DialogueEvent>()
        assertTrue("即时通讯场景至少要有三条聊天内容", dialogues.size >= 3)
    }

    @Test
    fun `instant messaging without evidence degrades to LiveScene with warnings`() {
        val json = """
            {
              "schemaVersion": "1.0",
              "storyId": "story-1",
              "chapterId": "chapter-x",
              "characters": [{ "tempId": "a1", "name": "甲" }],
              "scenes": [
                {
                  "tempId": "s1",
                  "title": "没有证据的聊天",
                  "presentationMode": "InstantMessaging",
                  "participants": ["a1"],
                  "beats": [
                    { "order": 1, "events": [
                      { "type": "dialogue", "id": "e1", "speakerTempId": "a1", "text": "在吗？" }
                    ] }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = pipeline.parse(json)

        assertEquals(emptyList<Any>(), result.validation.errors)

        val codes = result.validation.warnings.map { it.code }
        assertTrue(codes.contains(ValidationCode.MISSING_PRESENTATION_EVIDENCE))
        assertTrue(codes.contains(ValidationCode.DEGRADED_PRESENTATION_MODE))
        assertTrue(result.validation.warnings.all { it.severity == ValidationSeverity.WARNING })

        val scene = requireNotNull(result.content).scenes.single()
        assertEquals(PresentationMode.LiveScene, scene.presentationMode)
        assertNull(scene.presentationEvidence)
    }

    @Test
    fun `missing presentationMode defaults to LiveScene`() {
        val json = """
            {
              "schemaVersion": "1.0",
              "chapterId": "chapter-y",
              "characters": [{ "tempId": "a1", "name": "甲" }],
              "scenes": [
                {
                  "tempId": "s1",
                  "title": "未标注呈现方式的场景",
                  "beats": []
                }
              ]
            }
        """.trimIndent()

        val result = pipeline.parse(json)

        assertTrue(result.validation.isValid)
        val scene = requireNotNull(result.content).scenes.single()
        assertEquals(PresentationMode.LiveScene, scene.presentationMode)
        assertTrue(result.validation.warnings.isEmpty())
    }

    @Test
    fun `beats and events map by order and event type`() {
        val source = liveScene
        val content = requireNotNull(pipeline.parse(source.responseJson, source.chapterText).content)
        val beats = content.beatsByScene.getValue("chapter-1-s1")

        assertEquals(listOf(1, 2, 3, 4), beats.map { it.order })
        assertEquals(listOf("b1", "b2", "b3", "b4"), beats.map { it.id })

        // 节拍 1：环境描述 + 环境音并发（同一 Beat 内起点相同）
        assertEquals(2, beats[0].events.size)
        assertTrue(beats[0].events[0] is EnvironmentEvent)
        assertTrue(beats[0].events[1] is SoundEvent)
        assertEquals(listOf(0L, 0L), beats[0].events.map { it.timing.startOffsetMillis })

        // 节拍 3：对白先起，镜头延后 600ms
        assertEquals(listOf(0L, 600L), beats[2].events.map { it.timing.startOffsetMillis })
        assertTrue(beats[2].events[1] is CameraEvent)

        val allEvents = beats.flatMap { it.events }
        assertTrue(allEvents.any { it is DialogueEvent })
        assertTrue(allEvents.any { it is ActionEvent })
        assertTrue(allEvents.any { it is NarrationEvent })
        assertTrue(allEvents.all { it.timing.durationSource == DurationSource.Estimated })
        assertEquals(9_900L, content.scenes.single().timeline.totalDurationMillis)
    }

    @Test
    fun `speech params and utterance are mapped`() {
        val source = liveScene
        val content = requireNotNull(pipeline.parse(source.responseJson, source.chapterText).content)
        val dialogue = content.beatsByScene.getValue("chapter-1-s1")
            .flatMap { it.events }
            .filterIsInstance<DialogueEvent>()
            .first { it.dialogue.text == "你还没走？" }

        assertEquals(0.95f, requireNotNull(dialogue.speech).rate!!, 0.0001f)
        assertEquals(1.0f, dialogue.speech!!.pitch!!, 0.0001f)
        assertEquals(1.0f, dialogue.speech!!.volume!!, 0.0001f)

        assertEquals("迟疑", dialogue.utterance.speakingStyle)
        assertEquals("zh-CN", dialogue.utterance.language)
        assertEquals("平静", dialogue.utterance.emotion)
        assertTrue(!dialogue.utterance.isInnerMonologue)

        // AI 不提供音色，因此不覆盖，运行时继承角色 VoiceProfile
        assertNull(dialogue.voiceOverride)
        assertNull(dialogue.presentationOverride)
    }

    @Test
    fun `source spans are mapped and match the original chapter text`() {
        val source = liveScene
        val content = requireNotNull(pipeline.parse(source.responseJson, source.chapterText).content)
        val dialogue = content.beatsByScene.getValue("chapter-1-s1")
            .flatMap { it.events }
            .filterIsInstance<DialogueEvent>()
            .first { it.dialogue.text == "你还没走？" }

        val span = requireNotNull(dialogue.sourceSpan)
        assertEquals("chapter-1", span.chapterId)
        assertEquals(88, span.startOffset)
        assertEquals(95, span.endOffset)
        assertEquals("「你还没走？」", span.snippet)
        assertEquals(span.snippet, source.chapterText.substring(span.startOffset, span.endOffset))
    }

    @Test
    fun `unparseable response is reported as error without domain output`() {
        val result = pipeline.parse("{ this is not json }")

        assertNull(result.content)
        assertTrue(result.validation.errors.any { it.code == ValidationCode.UNPARSEABLE_RESPONSE })
    }
}
