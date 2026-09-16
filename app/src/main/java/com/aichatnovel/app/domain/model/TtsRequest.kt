package com.aichatnovel.app.domain.model

/**
 * 一次语音合成请求（与供应商无关）。
 *
 * [voiceId] 只是一个音色标识（来自 [VoiceProfile.voiceRef]），**不是**完整 TTS 请求；
 * `provider` / `endpoint` 这类运行配置不在这里，也不在剧情结构里。
 * 供应商专有参数放 [providerOptions]，它只是一组字符串，不构成对某个 SDK 的依赖。
 */
data class TtsRequest(
    val text: String,
    val voiceId: String?,
    val locale: String?,
    val speechParams: SpeechParams,
    val format: AudioFormat,
    val providerOptions: Map<String, String> = emptyMap(),
)

/**
 * 一次「场景语音生成」的运行配置。
 *
 * 它决定用哪个供应商、什么语言、什么格式、旁白用哪个音色——
 * 全是**运行期选择**，不写入 `Story` / `Chapter` / `Scene` / `Beat`。
 */
data class TtsRunConfig(
    val providerId: TtsProviderId,
    val locale: String? = DEFAULT_LOCALE,
    val format: AudioFormat = AudioFormat.MP3,
    /** 旁白的音色。旁白没有角色，因此必须显式配置，而不是虚构一个 Character。 */
    val narrationVoiceId: String? = null,
    /** 角色没有 [VoiceProfile] 时使用的兜底音色。 */
    val fallbackVoiceId: String? = null,
) {

    companion object {
        const val DEFAULT_LOCALE = "zh-CN"
    }
}
