package com.aichatnovel.app.data.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.aichatnovel.app.repository.AudioDurationProbe
import com.aichatnovel.app.repository.AudioPlayer
import com.aichatnovel.app.repository.AudioPlayerState
import com.aichatnovel.app.repository.AudioPlayerStatus
import com.aichatnovel.app.repository.AudioStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * app 私有目录下的音频落地存储。
 *
 * 公开的 Downloads / 外部存储不用于运行期音频：那是用户可见目录，不是应用的中间产物区。
 * 同名文件直接覆盖，保证同一事件重复生成时不会堆积。
 */
class FileAudioStorage(
    private val rootDir: File,
) : AudioStorage {

    override suspend fun save(fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        require(fileName.isNotBlank()) { "音频文件名不能为空" }
        rootDir.mkdirs()
        val file = File(rootDir, fileName)
        file.writeBytes(bytes)
        file.absolutePath
    }

    override suspend fun delete(reference: String) {
        withContext(Dispatchers.IO) { File(reference).delete() }
    }

    override suspend fun deleteAll() {
        withContext(Dispatchers.IO) { rootDir.listFiles()?.forEach { it.delete() } }
    }
}

/**
 * 用 [MediaMetadataRetriever] 从**真实音频文件**读取时长。
 *
 * 这是 `DurationSource.Audio` 的唯一合法来源：读不出来就返回 null，调用方据此判定失败。
 * 本类不做任何「按文本长度估算」或「固定 1000ms」的兜底。
 */
class AndroidAudioDurationProbe : AudioDurationProbe {

    override suspend fun probe(reference: String): Long? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(reference)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
        } catch (e: RuntimeException) {
            null
        } catch (e: IOException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

/**
 * 基于 Media3 / ExoPlayer 的播放器实现。
 *
 * 边界：上层只看到 [AudioPlayer] 与 [AudioPlayerState]，ExoPlayer 类型不越过本类。
 *
 * 时间来源：**播放位置一律读取播放器本身**（[ExoPlayer.getCurrentPosition]）。
 * 采样 coroutine 只负责「定期读取」并同步到状态，**不自行累加时间**——
 * 因此不存在「播放器时钟 + 自己累加时钟」两个时间源。
 */
class Media3AudioPlayer(
    context: Context,
    private val scope: CoroutineScope,
) : AudioPlayer {

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    private val _state = MutableStateFlow(AudioPlayerState())

    override val state: Flow<AudioPlayerState> = _state.asStateFlow()

    private var sampler: Job? = null

    private val listener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncFromPlayer()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncFromPlayer()
        }

        override fun onPlayerError(error: PlaybackException) {
            stopSampling()
            _state.value = _state.value.copy(
                status = AudioPlayerStatus.FAILED,
                error = error.message ?: "播放失败",
            )
        }
    }

    init {
        player.addListener(listener)
    }

    override suspend fun load(reference: String) {
        stopSampling()
        _state.value = AudioPlayerState(status = AudioPlayerStatus.PREPARING)
        withContext(Dispatchers.Main) {
            player.setMediaItem(MediaItem.fromUri(reference))
            player.prepare()
        }
        syncFromPlayer()
    }

    override suspend fun play() {
        withContext(Dispatchers.Main) { player.play() }
        startSampling()
    }

    override suspend fun pause() {
        withContext(Dispatchers.Main) { player.pause() }
        stopSampling()
        syncFromPlayer()
    }

    override suspend fun seekTo(positionMillis: Long) {
        withContext(Dispatchers.Main) { player.seekTo(positionMillis.coerceAtLeast(0L)) }
        syncFromPlayer()
    }

    override suspend fun stop() {
        stopSampling()
        withContext(Dispatchers.Main) {
            player.stop()
            player.clearMediaItems()
        }
        _state.value = AudioPlayerState()
    }

    override fun release() {
        stopSampling()
        player.removeListener(listener)
        player.release()
    }

    private fun startSampling() {
        stopSampling()
        sampler = scope.launch {
            while (isActive) {
                // 读取播放器状态必须在主线程。
                withContext(Dispatchers.Main) { syncFromPlayer() }
                delay(SAMPLE_INTERVAL_MILLIS)
            }
        }
    }

    private fun stopSampling() {
        sampler?.cancel()
        sampler = null
    }

    /** 只读取播放器的真实状态与位置，不自行推进时间。 */
    private fun syncFromPlayer() {
        val status = when (player.playbackState) {
            Player.STATE_IDLE -> AudioPlayerStatus.IDLE
            Player.STATE_BUFFERING -> AudioPlayerStatus.PREPARING
            Player.STATE_READY -> if (player.isPlaying) AudioPlayerStatus.PLAYING else AudioPlayerStatus.PAUSED
            Player.STATE_ENDED -> AudioPlayerStatus.ENDED
            else -> AudioPlayerStatus.IDLE
        }
        _state.value = AudioPlayerState(
            status = status,
            positionMillis = player.currentPosition.coerceAtLeast(0L),
            durationMillis = player.duration.takeIf { it > 0L } ?: 0L,
            error = _state.value.error,
        )
    }

    private companion object {
        const val SAMPLE_INTERVAL_MILLIS = 100L
    }
}

/**
 * 没有可用播放器时的占位实现（例如未注入 Android Context 的环境）。
 *
 * 它不做任何事，也不假装播放成功；[AudioPlayerStatus] 永远停在 IDLE。
 */
class NoOpAudioPlayer : AudioPlayer {

    private val _state = MutableStateFlow(AudioPlayerState())

    override val state: Flow<AudioPlayerState> = _state.asStateFlow()

    override suspend fun load(reference: String) = Unit

    override suspend fun play() = Unit

    override suspend fun pause() = Unit

    override suspend fun seekTo(positionMillis: Long) = Unit

    override suspend fun stop() = Unit

    override fun release() = Unit
}
