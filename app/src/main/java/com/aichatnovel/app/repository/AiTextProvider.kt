package com.aichatnovel.app.repository

/** 一次文本补全请求。与具体供应商无关，也不含任何凭据。 */
data class AiTextRequest(
    val userPrompt: String,
    val systemPrompt: String? = null,
    val requireJsonObject: Boolean = true,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
)

/** 文本 provider 的失败分类。调用方据此区分「没配 Key」「网络挂了」「返回不可用」，不做兜底伪造。 */
enum class AiTextFailure {
    MISSING_CREDENTIALS,
    UNAUTHORIZED,
    TIMEOUT,
    NETWORK,
    HTTP_ERROR,
    MALFORMED_RESPONSE,
    EMPTY_RESPONSE,
}

sealed interface AiTextResult {

    /** [text] 是模型返回的正文原文。**它仍然是文本**，不经过任何解析，更不是 Domain。 */
    data class Success(val text: String, val model: String? = null) : AiTextResult

    data class Failure(
        val reason: AiTextFailure,
        val message: String?,
        val httpStatus: Int? = null,
    ) : AiTextResult
}

/**
 * AI 文本能力入口。
 *
 * 它只回答「给定提示词，模型回了什么文本」。实现负责 HTTP 与供应商协议；
 * **它不解析业务 JSON、不接触 DTO、更不接触 domain**——结构化解析仍归
 * `ParseJson` / `ParseValidator` / `AiParseMapper`。
 */
interface AiTextProvider {

    val id: String

    suspend fun complete(request: AiTextRequest): AiTextResult
}
