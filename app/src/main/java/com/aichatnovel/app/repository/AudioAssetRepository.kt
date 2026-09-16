package com.aichatnovel.app.repository

import com.aichatnovel.app.domain.model.AudioAsset
import kotlinx.coroutines.flow.Flow

/**
 * 当前已生成音频的存放处：**eventId → [AudioAsset]**。
 *
 * 它只保存「已经生成的产物」，不保存播放位置、不保存 UI 状态。
 * 时间轴据此把估算时长替换成真实音频时长。
 */
interface AudioAssetRepository {

    fun observeAudioAssets(): Flow<Map<String, AudioAsset>>

    /** 覆盖式写入（按 eventId 合并）。 */
    suspend fun putAll(assets: Map<String, AudioAsset>)

    /** 清空当前场景的音频（例如重新生成前）。 */
    suspend fun clear()
}
