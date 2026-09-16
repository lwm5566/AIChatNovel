package com.aichatnovel.app.data.audio

import com.aichatnovel.app.domain.mapping.effectiveSpeakerId
import com.aichatnovel.app.domain.mapping.orderedEvents
import com.aichatnovel.app.domain.mapping.speakableText
import com.aichatnovel.app.domain.mapping.toTtsRequest
import com.aichatnovel.app.domain.model.AudioAsset
import com.aichatnovel.app.domain.model.AudioFormat
import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.Character
import com.aichatnovel.app.domain.model.DialogueEvent
import com.aichatnovel.app.domain.model.PerformanceEvent
import com.aichatnovel.app.domain.model.TtsProviderId
import com.aichatnovel.app.domain.model.TtsRequest
import com.aichatnovel.app.domain.model.TtsRunConfig
import com.aichatnovel.app.repository.AudioAssetRepository
import com.aichatnovel.app.repository.AudioDurationProbe
import com.aichatnovel.app.repository.AudioStorage
import com.aichatnovel.app.repository.ProviderCredentialStore
import com.aichatnovel.app.repository.SceneAudioFailure
import com.aichatnovel.app.repository.SceneAudioGenerator
import com.aichatnovel.app.repository.SceneAudioResult
import com.aichatnovel.app.repository.TtsFailure
import com.aichatnovel.app.repository.TtsProvider
import com.aichatnovel.app.repository.TtsResult
import kotlinx.coroutines.ensureActive
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * 「场景 → 语音」的默认编排实现。
 *
 * 流程：
 * `beat（order 升序）→ event（有序）→ 可朗读事件 → 角色/旁白音色 → TtsRequest`
 * `→ TtsProvider → AudioStorage → AudioDurationProbe → AudioAsset → AudioAssetRepository`
 *
 * 并发策略：**顺序**生成。多角色 ≠ 必须同时打多个 TTS 请求；顺序换来稳定顺序、
 * 可定位的错误与可靠的取消，本阶段不做无限并发。
 *
 * 失败处理：单个事件失败**不伪造 AudioAsset**，只记录 [SceneAudioFailure]，
 * 已成功的事件照常写入仓库，整体返回 [SceneAudioResult.Partial]。
 */
class DefaultSceneAudioGenerator(
    private val providers: Map<TtsProviderId, TtsProvider>,
    private val credentialStore: ProviderCredentialStore,
    private val audioStorage: AudioStorage,
    private val durationProbe: AudioDurationProbe,
    private val audioAssetRepository: AudioAssetRepository,
) : SceneAudioGenerator {

    override fun isProviderConfigured(providerId: TtsProviderId): Boolean =
        providers.containsKey(providerId) &&
            credentialStore.credentialsFor(providerId)?.isConfigured == true

    override suspend fun generate(
        beats: List<Beat>,
        characters: List<Character>,
        config: TtsRunConfig,
    ): SceneAudioResult {
        val provider = providers[config.providerId]
            ?: return SceneAudioResult.Failure(
                TtsFailure.MISSING_CREDENTIALS,
                "没有可用的 ${config.providerId.displayName} Provider",
            )
        if (!isProviderConfigured(config.providerId)) {
            return SceneAudioResult.Failure(
                TtsFailure.MISSING_CREDENTIALS,
                "${config.providerId.displayName} 未配置凭据，未发起请求",
            )
        }

        val charactersById = characters.associateBy { it.id }
        val targets = beats.sortedBy { it.order }
            .flatMap { it.orderedEvents() }
            .filter { it.speakableText()?.isNotBlank() == true }

        if (targets.isEmpty()) return SceneAudioResult.NothingToSynthesize

        val generated = mutableMapOf<String, AudioAsset>()
        val failures = mutableListOf<SceneAudioFailure>()

        for (event in targets) {
            // 用户离开 / ViewModel 销毁时立刻停止继续请求，不再写入结果。
            coroutineContext.ensureActive()

            when (val outcome = synthesizeEvent(provider, event, charactersById, config)) {
                is EventSynthesis.Generated -> generated[event.id] = outcome.asset
                is EventSynthesis.Failed -> failures += outcome.failure
            }
        }

        if (generated.isNotEmpty()) audioAssetRepository.putAll(generated)

        return when {
            failures.isEmpty() -> SceneAudioResult.Success(generated.size)
            generated.isEmpty() -> SceneAudioResult.Failure(
                reason = failures.first().reason,
                message = failures.first().message ?: "语音生成失败",
            )
            else -> SceneAudioResult.Partial(generated.size, failures)
        }
    }

    private suspend fun synthesizeEvent(
        provider: TtsProvider,
        event: PerformanceEvent,
        charactersById: Map<String, Character>,
        config: TtsRunConfig,
    ): EventSynthesis {
        val character = event.speakerCharacterId()?.let { charactersById[it] }
        val request = event.toTtsRequest(character, config)
            ?: return EventSynthesis.Failed(
                SceneAudioFailure(event.id, TtsFailure.TEXT_REJECTED, "事件没有可合成的文本"),
            )

        return when (val result = provider.synthesize(request)) {
            is TtsResult.Failure -> EventSynthesis.Failed(
                SceneAudioFailure(event.id, result.reason, result.message),
            )

            is TtsResult.Success -> storeAndMeasure(provider, event, request, result)
        }
    }

    /** 落盘 → 读真实时长 → 造 AudioAsset。任一步失败都丢弃音频，绝不返回伪造的资产。 */
    private suspend fun storeAndMeasure(
        provider: TtsProvider,
        event: PerformanceEvent,
        request: TtsRequest,
        result: TtsResult.Success,
    ): EventSynthesis {
        val reference = try {
            audioStorage.save(fileName = fileNameFor(event, request), bytes = result.audio)
        } catch (e: IOException) {
            return EventSynthesis.Failed(
                SceneAudioFailure(event.id, TtsFailure.STORAGE_FAILURE, "音频落盘失败：${e.message}"),
            )
        }

        val duration = durationProbe.probe(reference)
        if (duration == null || duration <= 0L) {
            audioStorage.delete(reference)
            return EventSynthesis.Failed(
                SceneAudioFailure(
                    event.id,
                    TtsFailure.STORAGE_FAILURE,
                    "无法从生成的音频读出真实时长，已丢弃该音频（不会用估算值顶替）",
                ),
            )
        }

        return EventSynthesis.Generated(
            AudioAsset(
                id = event.id,
                reference = reference,
                durationMillis = duration,
                format = result.format,
                source = provider.id,
            ),
        )
    }

    private fun fileNameFor(event: PerformanceEvent, request: TtsRequest): String =
        "${event.id}.${request.format.fileExtension()}"

    private fun PerformanceEvent.speakerCharacterId(): String? =
        if (this is DialogueEvent) effectiveSpeakerId else null

    private fun AudioFormat.fileExtension(): String = when (this) {
        AudioFormat.MP3 -> "mp3"
        AudioFormat.WAV -> "wav"
        AudioFormat.AAC -> "aac"
        AudioFormat.PCM -> "pcm"
        AudioFormat.UNKNOWN -> "bin"
    }

    private sealed interface EventSynthesis {

        data class Generated(val asset: AudioAsset) : EventSynthesis

        data class Failed(val failure: SceneAudioFailure) : EventSynthesis
    }
}
