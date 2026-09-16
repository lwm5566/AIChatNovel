package com.aichatnovel.app.domain.audio

import com.aichatnovel.app.data.audio.DefaultSceneAudioGenerator
import com.aichatnovel.app.data.repository.InMemoryAudioAssetRepository
import com.aichatnovel.app.data.repository.InMemoryProviderCredentialStore
import com.aichatnovel.app.domain.mapping.buildExecutableTimeline
import com.aichatnovel.app.domain.mapping.speakableVoiceId
import com.aichatnovel.app.domain.mapping.toTtsRequest
import com.aichatnovel.app.domain.model.AudioAsset
import com.aichatnovel.app.domain.model.AudioFormat
import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.Character
import com.aichatnovel.app.domain.model.Dialogue
import com.aichatnovel.app.domain.model.DialogueEvent
import com.aichatnovel.app.domain.model.DurationSource
import com.aichatnovel.app.domain.model.Narration
import com.aichatnovel.app.domain.model.NarrationEvent
import com.aichatnovel.app.domain.model.Scene
import com.aichatnovel.app.domain.model.SpeechParams
import com.aichatnovel.app.domain.model.Timing
import com.aichatnovel.app.domain.model.TtsProviderId
import com.aichatnovel.app.domain.model.TtsRequest
import com.aichatnovel.app.domain.model.TtsRunConfig
import com.aichatnovel.app.domain.model.Utterance
import com.aichatnovel.app.domain.model.VoiceProfile
import com.aichatnovel.app.repository.AudioDurationProbe
import com.aichatnovel.app.repository.AudioStorage
import com.aichatnovel.app.repository.ProviderCredentials
import com.aichatnovel.app.repository.SceneAudioResult
import com.aichatnovel.app.repository.TtsFailure
import com.aichatnovel.app.repository.TtsProvider
import com.aichatnovel.app.repository.TtsResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6C 的语音契约：TtsRequest 映射、AudioAsset 约束、Timeline 真实时长回填、多角色编排。
 *
 * 这些测试全部使用 fake provider / fake storage / fake probe，**不访问真实供应商**。
 */
class TtsAudioPipelineTest {

    // ---------------- TtsMapping ----------------

    @Test
    fun `a dialogue speaks with the character voice profile`() {
        val event = dialogue("d1", speaker = "char-a", text = "你好", voiceRef = null)
        val character = Character(id = "char-a", storyId = "s", name = "甲", description = "", voiceProfile = VoiceProfile("voice-a"))

        val request = event.toTtsRequest(character, TtsRunConfig(providerId = TtsProviderId.AZURE))

        assertEquals("你好", request?.text)
        assertEquals("voice-a", request?.voiceId)
    }

    @Test
    fun `an event level voice override wins over the character profile`() {
        val event = dialogue("d1", speaker = "char-a", text = "你好", voiceRef = "voice-override")
        val character = Character(id = "char-a", storyId = "s", name = "甲", description = "", voiceProfile = VoiceProfile("voice-a"))

        val request = event.toTtsRequest(character, TtsRunConfig(providerId = TtsProviderId.AZURE))

        assertEquals("voice-override", request?.voiceId)
    }

    @Test
    fun `speech params from the event override the profile defaults`() {
        val event = dialogue(
            "d1",
            speaker = "char-a",
            text = "你好",
            voiceRef = null,
            speech = SpeechParams(rate = 1.4f),
        )
        val character = Character(
            id = "char-a",
            storyId = "s",
            name = "甲",
            description = "",
            voiceProfile = VoiceProfile("voice-a", defaultSpeech = SpeechParams(rate = 1.0f, pitch = 2.0f)),
        )

        val request = event.toTtsRequest(character, TtsRunConfig(providerId = TtsProviderId.AZURE))

        assertEquals(1.4f, request?.speechParams?.rate)
        assertEquals(2.0f, request?.speechParams?.pitch)
    }

    @Test
    fun `narration uses the configured narration voice and never invents a character`() {
        val event = NarrationEvent(id = "n1", timing = Timing(), narration = Narration("夜色渐深"))
        val config = TtsRunConfig(providerId = TtsProviderId.AZURE, narrationVoiceId = "voice-narration")

        val request = event.toTtsRequest(character = null, config = config)

        assertEquals("夜色渐深", request?.text)
        assertEquals("voice-narration", request?.voiceId)
        assertNull(event.speakableVoiceId(characterProfile = null, config = TtsRunConfig(TtsProviderId.AZURE)))
    }

    // ---------------- AudioAsset ----------------

    @Test
    fun `an audio asset requires a positive real duration`() {
        AudioAsset("e1", "/tmp/a.mp3", 1_200L, AudioFormat.MP3, TtsProviderId.AZURE)

        val zero = runCatching { AudioAsset("e1", "/tmp/a.mp3", 0L, AudioFormat.MP3, null) }
        val negative = runCatching { AudioAsset("e1", "/tmp/a.mp3", -5L, AudioFormat.MP3, null) }

        assertTrue(zero.isFailure)
        assertTrue(negative.isFailure)
    }

    @Test
    fun `a blank reference is rejected`() {
        assertTrue(runCatching { AudioAsset("e1", " ", 10L, AudioFormat.MP3, null) }.isFailure)
    }

    @Test
    fun `audio format is parsed from provider encoding names`() {
        assertEquals(AudioFormat.MP3, AudioFormat.fromName("mp3"))
        assertEquals(AudioFormat.WAV, AudioFormat.fromName("riff"))
        assertEquals(AudioFormat.UNKNOWN, AudioFormat.fromName("opus"))
    }

    // ---------------- Timeline backfill ----------------

    @Test
    fun `a real audio asset backfills the timeline as Audio while others stay Estimated`() {
        val scene = Scene(id = "scene-1", chapterId = "c1", index = 1, title = "教室")
        val beats = listOf(
            Beat("beat-1", 1, listOf(narrationEvent("n1"), narrationEvent("n2"))),
        )
        val assets = mapOf(
            "n1" to AudioAsset("n1", "/tmp/n1.mp3", 2_500L, AudioFormat.MP3, TtsProviderId.AZURE),
        )

        val timeline = buildExecutableTimeline(scene, beats, assets)

        assertEquals(listOf(2_500L, 1_000L), timeline.positions.map { it.durationMillis })
        assertEquals(
            listOf(DurationSource.Audio, DurationSource.Estimated),
            timeline.positions.map { it.durationSource },
        )
        // 两个事件都从 0 开始（同一节拍内并发），所以节拍末端是较长的那一个
        assertEquals(2_500L, timeline.totalDurationMillis)
        // 布局顺序不变，cursor 语义保持
        assertEquals(listOf(0L, 0L), timeline.positions.map { it.startOffsetMillis })
    }

    @Test
    fun `without audio assets the timeline is unchanged from phase 6b`() {
        val scene = Scene(id = "scene-1", chapterId = "c1", index = 1, title = "教室")
        val beats = listOf(Beat("beat-1", 1, listOf(narrationEvent("n1"))))

        val timeline = buildExecutableTimeline(scene, beats)

        assertEquals(listOf(DurationSource.Estimated), timeline.positions.map { it.durationSource })
    }

    // ---------------- 编排：多角色 / 失败 / 取消 ----------------

    @Test
    fun `each character speaks with its own voice and narration uses the narration voice`() = runTest {
        val provider = RecordingProvider()
        val generator = generator(provider)
        val characters = listOf(
            Character("char-a", "s", "甲", "", voiceProfile = VoiceProfile("voice-a")),
            Character("char-b", "s", "乙", "", voiceProfile = VoiceProfile("voice-b")),
        )
        val beats = listOf(
            Beat(
                "beat-1", 1,
                listOf(
                    dialogue("d-a", speaker = "char-a", text = "甲的话", voiceRef = null),
                    dialogue("d-b", speaker = "char-b", text = "乙的话", voiceRef = null),
                    NarrationEvent("n-1", Timing(), Narration("旁白")),
                ),
            ),
        )

        val result = generator.generate(
            beats, characters,
            TtsRunConfig(providerId = TtsProviderId.AZURE, narrationVoiceId = "voice-narration"),
        )

        assertTrue(result is SceneAudioResult.Success)
        assertEquals(listOf("voice-a", "voice-b", "voice-narration"), provider.requests.map { it.voiceId })
    }

    @Test
    fun `a provider failure never fabricates an audio asset`() = runTest {
        val provider = RecordingProvider(failOn = "d-b")
        val generator = generator(provider)
        val characters = listOf(Character("char-a", "s", "甲", "", voiceProfile = VoiceProfile("voice-a")), Character("char-b", "s", "乙", "", voiceProfile = VoiceProfile("voice-b")))
        val beats = listOf(
            Beat("beat-1", 1, listOf(dialogue("d-a", "char-a", "好的", null), dialogue("d-b", "char-b", "坏的", null))),
        )

        val result = generator.generate(beats, characters, TtsRunConfig(providerId = TtsProviderId.AZURE))

        val partial = result as SceneAudioResult.Partial
        assertEquals(1, partial.generated)
        assertEquals("d-b", partial.failures.single().eventId)
        assertEquals(TtsFailure.HTTP_ERROR, partial.failures.single().reason)
    }

    @Test
    fun `a duration probe failure discards the audio instead of using an estimate`() = runTest {
        val generator = generator(RecordingProvider(), probeDuration = null)
        val characters = listOf(Character("char-a", "s", "甲", "", voiceProfile = VoiceProfile("voice-a")))
        val beats = listOf(Beat("beat-1", 1, listOf(dialogue("d-a", "char-a", "好的", null))))

        val result = generator.generate(beats, characters, TtsRunConfig(providerId = TtsProviderId.AZURE))

        assertTrue(result is SceneAudioResult.Failure)
        assertEquals(TtsFailure.STORAGE_FAILURE, (result as SceneAudioResult.Failure).reason)
    }

    @Test
    fun `missing credentials stop the run before any provider call`() = runTest {
        val provider = RecordingProvider()
        val store = InMemoryProviderCredentialStore()
        val generator = DefaultSceneAudioGenerator(
            providers = mapOf(TtsProviderId.AZURE to provider),
            credentialStore = store,
            audioStorage = FakeAudioStorage(),
            durationProbe = FakeProbe(1_000L),
            audioAssetRepository = InMemoryAudioAssetRepository(),
        )

        val result = generator.generate(
            listOf(Beat("beat-1", 1, listOf(narrationEvent("n1")))),
            emptyList(),
            TtsRunConfig(providerId = TtsProviderId.AZURE),
        )

        assertEquals(TtsFailure.MISSING_CREDENTIALS, (result as SceneAudioResult.Failure).reason)
        assertTrue(provider.requests.isEmpty())
    }

    @Test
    fun `a scene with nothing to speak reports it instead of faking audio`() = runTest {
        val generator = generator(RecordingProvider())
        val beats = listOf(Beat("beat-1", 1, emptyList()))

        val result = generator.generate(beats, emptyList(), TtsRunConfig(providerId = TtsProviderId.AZURE))

        assertEquals(SceneAudioResult.NothingToSynthesize, result)
    }

    // ---------------- fakes ----------------

    private suspend fun generator(
        provider: TtsProvider,
        probeDuration: Long? = 1_500L,
    ): DefaultSceneAudioGenerator {
        val store = InMemoryProviderCredentialStore()
        store.put(ProviderCredentials(TtsProviderId.AZURE, secret = "fake-key", endpoint = "https://example.invalid"))
        return DefaultSceneAudioGenerator(
            providers = mapOf(TtsProviderId.AZURE to provider),
            credentialStore = store,
            audioStorage = FakeAudioStorage(),
            durationProbe = FakeProbe(probeDuration),
            audioAssetRepository = InMemoryAudioAssetRepository(),
        )
    }

    /** 真实的 probe 需要 Android 解码器；这里用固定值验证编排逻辑。 */
    private class FakeProbe(private val duration: Long?) : AudioDurationProbe {
        override suspend fun probe(reference: String): Long? = duration
    }

    private class FakeAudioStorage : AudioStorage {
        override suspend fun save(fileName: String, bytes: ByteArray): String = "/fake/$fileName"
        override suspend fun delete(reference: String) = Unit
        override suspend fun deleteAll() = Unit
    }

    private class RecordingProvider(private val failOn: String? = null) : TtsProvider {
        val requests = mutableListOf<TtsRequest>()
        override val id: TtsProviderId = TtsProviderId.AZURE

        override suspend fun synthesize(request: TtsRequest): TtsResult {
            requests += request
            return if (failOn != null && request.text.contains("坏的")) {
                TtsResult.Failure(TtsFailure.HTTP_ERROR, "boom", 500)
            } else {
                TtsResult.Success(byteArrayOf(1, 2), AudioFormat.MP3)
            }
        }
    }

    private fun dialogue(
        id: String,
        speaker: String,
        text: String,
        voiceRef: String?,
        speech: SpeechParams? = null,
    ) = DialogueEvent(
        id = id,
        timing = Timing(),
        dialogue = Dialogue(characterId = speaker, text = text),
        utterance = Utterance(speakerId = speaker, text = text),
        speech = speech,
        voiceOverride = voiceRef?.let { VoiceProfile(it) },
    )

    private fun narrationEvent(id: String) = NarrationEvent(id = id, timing = Timing(), narration = Narration(id))
}
