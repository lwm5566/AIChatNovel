package com.aichatnovel.app.data.remote.deepseek

/**
 * DeepSeek 接入配置。
 *
 * [apiKey] 允许为空：为空时请求层会直接返回 [DeepSeekApiResult.MissingApiKey]，不会发起网络调用。
 *
 * 安全约定：**toString() 永远不输出明文 Key**，避免它被日志或异常信息带出去。
 */
data class DeepSeekConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val apiKey: String? = null,
    val connectTimeoutMillis: Long = 20_000L,
    val readTimeoutMillis: Long = 180_000L,
    val maxTokens: Int = 8_000,
    val temperature: Double = 0.0,
) {

    override fun toString(): String =
        "DeepSeekConfig(baseUrl=$baseUrl, model=$model, apiKey=${redact(apiKey)}, " +
            "connectTimeoutMillis=$connectTimeoutMillis, readTimeoutMillis=$readTimeoutMillis, " +
            "maxTokens=$maxTokens, temperature=$temperature)"

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"

        /** 开发/测试用的 Key 来源。Android 运行时通常读不到，需要显式注入。 */
        const val API_KEY_ENV = "DEEPSEEK_API_KEY"
        const val API_KEY_PROPERTY = "deepseek.api.key"

        /**
         * 从环境变量 / JVM 系统属性读取 Key。
         * 仅用于本机开发与集成测试，不是生产方案。
         */
        fun fromEnvironment(
            baseUrl: String = DEFAULT_BASE_URL,
            model: String = DEFAULT_MODEL,
        ): DeepSeekConfig = DeepSeekConfig(
            baseUrl = baseUrl,
            model = model,
            apiKey = apiKeyFromEnvironment(),
        )

        fun apiKeyFromEnvironment(): String? =
            System.getenv(API_KEY_ENV)?.takeIf { it.isNotBlank() }
                ?: System.getProperty(API_KEY_PROPERTY)?.takeIf { it.isNotBlank() }

        /** 脱敏展示：不暴露 Key 的任何字符，只给出是否已配置与长度。任何日志都只能用这个。 */
        fun redact(apiKey: String?): String = when {
            apiKey.isNullOrBlank() -> "(未配置)"
            else -> "****(len=${apiKey.length})"
        }
    }
}
