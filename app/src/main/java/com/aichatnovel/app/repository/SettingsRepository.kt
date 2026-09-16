package com.aichatnovel.app.repository

import com.aichatnovel.app.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * 应用设置数据入口。仅定义接口，具体实现位于 data 层。
 */
interface SettingsRepository {

    fun observeSettings(): Flow<AppSettings>

    suspend fun update(settings: AppSettings)
}
