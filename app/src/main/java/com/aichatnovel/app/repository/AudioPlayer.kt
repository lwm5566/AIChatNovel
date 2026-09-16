package com.aichatnovel.app.repository

import kotlinx.coroutines.flow.Flow

/** 播放器状态。与 Timeline / PlaybackState 是两层：这里描述「播放器怎么想」，不是「剧情走到哪」。 */
enum class AudioPlayerStatus {
    IDLE,
    PREPARING,
    READY,
    PLAYING,
    PAUSED,
    ENDED,
    FAILED,
}

data class AudioPlayerState(
    val status: AudioPlayerStatus = AudioPlayerStatus.IDLE,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val error: String? = null,
)

/**
 * 音频播放能力入口。
 *
 * 只暴露「加载 / 播放 / 暂停 / 定位 / 停止」这几件事，**不暴露任何 ExoPlayer / Media3 类型**：
 * ViewModel 依赖这个抽象，实现（`Media3AudioPlayer`）待在 data 层。
 */
interface AudioPlayer {

    val state: Flow<AudioPlayerState>

    /** 加载一个音频引用。加载完成前不应认为自己可以播放。 */
    suspend fun load(reference: String)

    suspend fun play()

    suspend fun pause()

    suspend fun seekTo(positionMillis: Long)

    /** 停止并释放当前音频，回到 [AudioPlayerStatus.IDLE]。 */
    suspend fun stop()

    /** 释放底层播放器资源；调用后不应再使用本实例。 */
    fun release()
}
