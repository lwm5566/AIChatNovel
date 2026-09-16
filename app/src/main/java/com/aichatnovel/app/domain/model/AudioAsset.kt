package com.aichatnovel.app.domain.model

/** 音频容器格式。 */
enum class AudioFormat {
    MP3,
    WAV,
    AAC,
    PCM,

    /** 供应商返回了无法识别的格式；此时不能声称音频可用。 */
    UNKNOWN,
    ;

    companion object {

        /** 从供应商返回的编码名解析；不认识的一律 [UNKNOWN]，不猜测。 */
        fun fromName(value: String?): AudioFormat = when (value?.trim()?.lowercase()) {
            "mp3", "audio/mpeg", "audio/mp3" -> MP3
            "wav", "audio/wav", "audio/x-wav", "riff" -> WAV
            "aac", "audio/aac" -> AAC
            "pcm", "raw", "audio/l16" -> PCM
            else -> UNKNOWN
        }
    }
}

/**
 * TTS 供应商标识。
 *
 * 它属于**语音运行配置**，不是剧情事实：`Story` / `Scene` / `Beat` / `PerformanceEvent`
 * 永远不携带它，剧情数据也不知道自己的声音将由谁合成。
 */
enum class TtsProviderId(val displayName: String) {
    AZURE("Microsoft Azure Speech"),
    VOLCENGINE("火山引擎"),
    XIAOMI_MIMO("小米 MiMo"),
}

/**
 * 已经生成、**可以直接播放**的音频资源。
 *
 * 与 [AssetRef] 的分工（两者不可合并）：
 * - [AssetRef] 是**演出数据对资源的引用**——「这里需要一段音频」，可以指向尚未存在的东西；
 * - [AudioAsset] 是**已经产出的音频本身**——文件在哪、多长、什么格式，一定指向真实存在的产物。
 *
 * [durationMillis] 必须来自真实音频（容器/解码信息）。它是 Timeline 的
 * [DurationSource.Audio] 成立的唯一依据；估算值绝不能构造出 [AudioAsset]。
 */
data class AudioAsset(
    val id: String,
    val reference: String,
    val durationMillis: Long,
    val format: AudioFormat,
    val source: TtsProviderId?,
) {

    init {
        require(durationMillis > 0L) { "音频时长必须为正数，实际为 $durationMillis" }
        require(reference.isNotBlank()) { "音频引用不能为空" }
    }

    /** 播放用的不透明引用；上层不得据此推断文件系统布局。 */
    val playbackReference: String get() = reference
}
