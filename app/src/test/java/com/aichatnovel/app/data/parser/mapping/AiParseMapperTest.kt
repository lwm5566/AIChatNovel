package com.aichatnovel.app.data.parser.mapping

import com.aichatnovel.app.data.parser.ParseSchema
import com.aichatnovel.app.data.parser.dto.BeatDto
import com.aichatnovel.app.data.parser.dto.CharacterDto
import com.aichatnovel.app.data.parser.dto.NarrationEventDto
import com.aichatnovel.app.data.parser.dto.ParseResponseDto
import com.aichatnovel.app.data.parser.dto.SceneDto
import com.aichatnovel.app.data.parser.dto.SceneSettingDto
import com.aichatnovel.app.data.parser.dto.TimingDto
import com.aichatnovel.app.domain.model.AssetRef
import com.aichatnovel.app.domain.model.AssetType
import com.aichatnovel.app.domain.model.DurationSource
import com.aichatnovel.app.domain.model.StoryContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mapper 的边界规则：AI 未显式给出的字段如何被归一化。
 *
 * 只锁定既有行为，不改动映射实现。
 */
class AiParseMapperTest {

    private val mapper = AiParseMapper()

    @Test
    fun `a duration without an explicit source is inferred as estimated`() {
        val content = mappedContent(
            scenes = listOf(
                scene(beats = listOf(beat(TimingDto(startOffset = 0L, duration = 1_000L)))),
            ),
        )

        val event = content.beatsByScene.getValue("chapter-1-s1").single().events.single()
        assertEquals(1_000L, event.timing.durationMillis)
        assertEquals(DurationSource.Estimated, event.timing.durationSource)
    }

    @Test
    fun `a missing duration keeps both the duration and the source unknown`() {
        val content = mappedContent(
            scenes = listOf(scene(beats = listOf(beat(TimingDto(startOffset = 0L))))),
        )

        val event = content.beatsByScene.getValue("chapter-1-s1").single().events.single()
        assertNull(event.timing.durationMillis)
        assertNull(event.timing.durationSource)
    }

    @Test
    fun `a missing timing falls back to a zero offset without a duration`() {
        val content = mappedContent(
            scenes = listOf(scene(beats = listOf(beat()))),
        )

        val event = content.beatsByScene.getValue("chapter-1-s1").single().events.single()
        assertEquals(0L, event.timing.startOffsetMillis)
        assertNull(event.timing.durationMillis)
        assertNull(event.timing.durationSource)
    }

    @Test
    fun `a background reference becomes an image asset`() {
        val content = mappedContent(
            scenes = listOf(scene(setting = SceneSettingDto(backgroundRef = "bg/classroom.png"))),
        )

        val setting = content.scenes.single().setting
        assertEquals(AssetRef(id = "bg/classroom.png", type = AssetType.IMAGE), setting.backgroundRef)
        assertNull(setting.backgroundRef?.source)
    }

    @Test
    fun `a scene without a background reference has none`() {
        val content = mappedContent(scenes = listOf(scene(setting = SceneSettingDto(location = "教室"))))

        assertNull(content.scenes.single().setting.backgroundRef)
    }

    @Test
    fun `participants become scene characters and duplicates collapse`() {
        val content = mappedContent(scenes = listOf(scene(participants = listOf("c2", "c1", "c2"))))

        assertEquals(
            listOf("char-陆沉", "char-林晚"),
            content.scenes.single().characters.map { it.characterId },
        )
    }

    @Test
    fun `an undeclared participant is dropped instead of invented`() {
        val content = mappedContent(scenes = listOf(scene(participants = listOf("c1", "nobody"))))

        assertEquals(listOf("char-林晚"), content.scenes.single().characters.map { it.characterId })
    }

    @Test
    fun `scene characters only carry the character id`() {
        val content = mappedContent(scenes = listOf(scene(participants = listOf("c1"))))

        val state = content.scenes.single().characters.single()
        assertEquals("char-林晚", state.characterId)
        assertNull(state.position)
        assertNull(state.facing)
        assertNull(state.expression)
        assertNull(state.costume)
        assertNull(state.avatarRef)
    }

    @Test
    fun `character description and aliases are mapped`() {
        val content = mappedContent(
            characters = listOf(
                CharacterDto(
                    tempId = "c1",
                    name = "林晚",
                    aliases = listOf("小晚", "转学生"),
                    description = "高二学生",
                ),
            ),
        )

        val character = content.characters.single()
        assertEquals("char-林晚", character.id)
        assertEquals("高二学生", character.description)
        assertEquals(listOf("小晚", "转学生"), character.aliases)
    }

    @Test
    fun `a character without description or aliases maps to empty values`() {
        val content = mappedContent(characters = listOf(CharacterDto(tempId = "c1", name = "林晚")))

        val character = content.characters.single()
        assertEquals("", character.description)
        assertEquals(emptyList<String>(), character.aliases)
    }

    @Test
    fun `characters never receive a voice profile or avatar from the parser`() {
        val content = mappedContent(characters = listOf(CharacterDto(tempId = "c1", name = "林晚")))

        val character = content.characters.single()
        assertNull(character.voiceProfile)
        assertNull(character.defaultAvatarRef)
    }

    private fun mappedContent(
        characters: List<CharacterDto> = defaultCharacters(),
        scenes: List<SceneDto> = listOf(scene()),
    ): StoryContent = mapper.map(
        ParseResponseDto(
            schemaVersion = ParseSchema.CURRENT,
            chapterId = "chapter-1",
            characters = characters,
            scenes = scenes,
        ),
    ).content

    private fun defaultCharacters(): List<CharacterDto> = listOf(
        CharacterDto(tempId = "c1", name = "林晚"),
        CharacterDto(tempId = "c2", name = "陆沉"),
    )

    private fun beat(timing: TimingDto? = null): BeatDto = BeatDto(
        id = "b1",
        order = 1,
        events = listOf(NarrationEventDto(id = "e1", text = "旁白", timing = timing)),
    )

    private fun scene(
        tempId: String = "s1",
        participants: List<String> = emptyList(),
        setting: SceneSettingDto? = null,
        beats: List<BeatDto> = emptyList(),
    ): SceneDto = SceneDto(
        tempId = tempId,
        title = "场景",
        participants = participants,
        setting = setting,
        beats = beats,
    )
}
