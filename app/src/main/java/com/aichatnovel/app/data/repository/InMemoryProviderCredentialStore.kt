package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.TtsProviderId
import com.aichatnovel.app.repository.ProviderCredentialStore
import com.aichatnovel.app.repository.ProviderCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 只在内存中保存凭据的实现。
 *
 * **刻意不做持久化**：没有可用的安全存储方案之前，写入磁盘只会扩大泄露面。
 * 进程结束即失效，用户需要在下次会话重新注入。
 *
 * 对外只暴露「是否已配置」，[ProviderCredentials.toString] 亦已脱敏。
 */
class InMemoryProviderCredentialStore(
    initial: Map<TtsProviderId, ProviderCredentials> = emptyMap(),
) : ProviderCredentialStore {

    private val state = MutableStateFlow(initial)

    private val configured = MutableStateFlow(initial.filterValues { it.isConfigured }.keys)

    override fun credentialsFor(providerId: TtsProviderId): ProviderCredentials? = state.value[providerId]

    override fun observeConfiguredProviders(): Flow<Set<TtsProviderId>> = configured.asStateFlow()

    override suspend fun put(credentials: ProviderCredentials) {
        state.update { it + (credentials.providerId to credentials) }
        configured.value = state.value.filterValues { it.isConfigured }.keys
    }

    override suspend fun clear(providerId: TtsProviderId) {
        state.update { it - providerId }
        configured.value = state.value.filterValues { it.isConfigured }.keys
    }
}
