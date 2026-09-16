package com.aichatnovel.app.repository

import com.aichatnovel.app.domain.model.TtsProviderId
import kotlinx.coroutines.flow.Flow

/**
 * 某个 TTS 供应商的运行期凭据。
 *
 * 约定：
 * - **只做运行时（内存）注入**，不写盘、不进 BuildConfig、不进日志；
 * - [secret] 为空表示未配置，调用方必须据此报「未配置」，而不是伪造可用；
 * - [toString] 只输出是否已配置与长度，永不输出明文。
 */
data class ProviderCredentials(
    val providerId: TtsProviderId,
    val secret: String? = null,
    val endpoint: String? = null,
    val appId: String? = null,
    val cluster: String? = null,
) {

    val isConfigured: Boolean get() = !secret.isNullOrBlank()

    override fun toString(): String =
        "ProviderCredentials(provider=$providerId, secret=${redact(secret)}, endpoint=$endpoint, " +
            "appId=${redact(appId)}, cluster=$cluster)"

    private fun redact(value: String?): String = when {
        value.isNullOrBlank() -> "(未配置)"
        else -> "****(len=${value.length})"
    }
}

/**
 * 凭据来源。UI 只读取「是否已配置」，**不读取明文**。
 */
interface ProviderCredentialStore {

    fun credentialsFor(providerId: TtsProviderId): ProviderCredentials?

    fun observeConfiguredProviders(): Flow<Set<TtsProviderId>>

    suspend fun put(credentials: ProviderCredentials)

    suspend fun clear(providerId: TtsProviderId)
}
