package com.aichatnovel.app.repository

import com.aichatnovel.app.domain.model.AudioFormat
import com.aichatnovel.app.domain.model.TtsProviderId
import com.aichatnovel.app.domain.model.TtsRequest

/** TTS 失败分类。**任何一类都不得被转换成「生成成功」。** */
enum class TtsFailure {
    /** 没有配置该供应商的凭据。 */
    MISSING_CREDENTIALS,
    /** 凭据被供应商拒绝。 */
    UNAUTHORIZED,
    /** 供应商不认识请求的音色。 */
    UNSUPPORTED_VOICE,
    /** 供应商不支持请求的音频格式。 */
    UNSUPPORTED_FORMAT,
    /** 文本被供应商拒绝（空文本、超长、语种不匹配等）。 */
    TEXT_REJECTED,
    TIMEOUT,
    NETWORK,
    HTTP_ERROR,
    /** HTTP 成功但响应体不符合契约（缺字段、base64 损坏等）。 */
    INVALID_RESPONSE,
    /** 音频已合成，但落盘或时长读取失败。 */
    STORAGE_FAILURE,
    CANCELLED,
}

sealed interface TtsResult {

    /**
     * 合成成功的原始音频。
     *
     * 这里**只有字节与格式**：文件路径由业务层（[AudioStorage]）决定，
     * 真实时长由 [AudioDurationProbe] 从音频本身读出——供应商不决定这两件事。
     */
    data class Success(
        val audio: ByteArray,
        val format: AudioFormat,
        val providerMetadata: Map<String, String> = emptyMap(),
    ) : TtsResult

    data class Failure(
        val reason: TtsFailure,
        val message: String?,
        val httpStatus: Int? = null,
    ) : TtsResult
}

/**
 * 语音合成能力入口。
 *
 * 实现负责 HTTP、认证与供应商 JSON；**供应商 JSON 不得越过这一层**
 * （它既不能进入 Domain，也不能进入 ViewModel 或 UI）。
 */
interface TtsProvider {

    val id: TtsProviderId

    suspend fun synthesize(request: TtsRequest): TtsResult
}
