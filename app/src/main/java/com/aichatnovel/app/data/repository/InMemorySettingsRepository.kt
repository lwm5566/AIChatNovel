package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.AppSettings
import com.aichatnovel.app.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 仅在内存中保存设置的 [SettingsRepository] 实现，进程重启即丢失。
 * 后续可替换为 DataStore 等持久化实现。
 */
class InMemorySettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override fun observeSettings(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun update(settings: AppSettings) {
        state.value = settings
    }
}
