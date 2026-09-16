package com.aichatnovel.app.repository

import com.aichatnovel.app.domain.model.Beat
import com.aichatnovel.app.domain.model.Character
import com.aichatnovel.app.domain.model.TtsProviderId
import com.aichatnovel.app.domain.model.TtsRunConfig

/** 单个事件的语音生成失败。UI 据此能把失败定位到具体事件，而不是整场景一句「失败」。 */
data class SceneAudioFailure(
    val eventId: String,
    val reason: TtsFailure,
    val message: String?,
)

sealed interface SceneAudioResult {

    /** 需要发声的事件全部生成成功。 */
    data class Success(val generated: Int) : SceneAudioResult

    /** 场景里没有任何需要发声的事件（例如只有动作 / 环境 / 镜头）。 */
    data object NothingToSynthesize : SceneAudioResult

    /** 部分成功：[generated] 条已写入，[failures] 列出失败事件。**不伪造缺失的音频。** */
    data class Partial(val generated: Int, val failures: List<SceneAudioFailure>) : SceneAudioResult

    /** 全部失败或前置条件不满足（未配置凭据、没有该 provider 等）。 */
    data class Failure(val reason: TtsFailure, val message: String?) : SceneAudioResult
}

/**
 * 「场景 → 语音」的编排入口。
 *
 * 它把「哪些事件要发声 / 用谁的声音 / 怎么落盘 / 真实时长」串起来；
 * UI 与 ViewModel 都不直接接触 [TtsProvider]，也不自己拼接请求。
 */
interface SceneAudioGenerator {

    /** 该 provider 当前是否具备可用凭据。UI 据此显示「未配置」，而不是假装可用。 */
    fun isProviderConfigured(providerId: TtsProviderId): Boolean

    suspend fun generate(
        beats: List<Beat>,
        characters: List<Character>,
        config: TtsRunConfig,
    ): SceneAudioResult
}
