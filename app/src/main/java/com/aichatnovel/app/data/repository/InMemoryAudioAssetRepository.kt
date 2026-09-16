package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.AudioAsset
import com.aichatnovel.app.repository.AudioAssetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 内存实现：`eventId → AudioAsset`。
 *
 * 音频**文件**本身由 `AudioStorage` 落在 app 私有目录；这里只保存索引。
 * 进程重启即丢失——本阶段明确不引入 Room / SQLite / DataStore。
 */
class InMemoryAudioAssetRepository(
    initial: Map<String, AudioAsset> = emptyMap(),
) : AudioAssetRepository {

    private val state = MutableStateFlow(initial)

    override fun observeAudioAssets(): Flow<Map<String, AudioAsset>> = state.asStateFlow()

    override suspend fun putAll(assets: Map<String, AudioAsset>) {
        if (assets.isEmpty()) return
        state.update { current -> current + assets }
    }

    override suspend fun clear() {
        state.value = emptyMap()
    }
}
