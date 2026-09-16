package com.aichatnovel.app.repository

import com.aichatnovel.app.domain.model.Beat
import kotlinx.coroutines.flow.Flow

/**
 * 演出数据入口：按场景获取演出节拍。
 *
 * 仅定义接口，具体实现位于 data 层。后续接入 AI / TTS / 视频生成后，
 * 这里返回的节拍流可能由实时生成产生。
 */
interface PerformanceRepository {

    fun observeBeats(sceneId: String): Flow<List<Beat>>
}
