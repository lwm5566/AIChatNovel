package com.aichatnovel.app.data.remote.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DeepSeek Chat Completions 的请求体（线上传输格式 DTO）。
 *
 * 它与 domain 无关，也与 [com.aichatnovel.app.data.parser.dto.ParseResponseDto] 无关：
 * 本文件的类型只负责「把 prompt 发出去」。
 */
@Serializable
data class DeepSeekChatRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("response_format") val responseFormat: DeepSeekResponseFormat? = null,
    val stream: Boolean = false,
)

@Serializable
data class DeepSeekMessage(
    val role: String,
    val content: String,
) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"

        fun system(content: String) = DeepSeekMessage(ROLE_SYSTEM, content)

        fun user(content: String) = DeepSeekMessage(ROLE_USER, content)
    }
}

/** 要求模型只返回 JSON 对象。 */
@Serializable
data class DeepSeekResponseFormat(val type: String) {
    companion object {
        val JSON_OBJECT = DeepSeekResponseFormat("json_object")
    }
}

/** 响应信封。模型返回的正文在 [DeepSeekChatResponse.choices]。 */
@Serializable
data class DeepSeekChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<DeepSeekChoice> = emptyList(),
    val usage: DeepSeekUsage? = null,
)

@Serializable
data class DeepSeekChoice(
    val index: Int = 0,
    val message: DeepSeekMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class DeepSeekUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
)

/** 错误信封，用于把 HTTP 非 2xx 的信息呈现得更清楚（不包含任何凭据）。 */
@Serializable
data class DeepSeekErrorEnvelope(val error: DeepSeekError? = null)

@Serializable
data class DeepSeekError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)
