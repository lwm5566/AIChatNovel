package com.aichatnovel.app.data.remote.deepseek

/**
 * 请求层的返回值。
 *
 * 这里只描述「网络调用本身」的结果，不涉及任何 domain 概念——
 * 网络层永远不会构造 Story / Scene / Beat / PerformanceEvent。
 */
sealed interface DeepSeekApiResult {

    /** 成功拿到响应信封（正文仍需后续解析）。 */
    data class Success(val response: DeepSeekChatResponse) : DeepSeekApiResult

    /** 未配置 API Key，直接短路，不发请求。 */
    data object MissingApiKey : DeepSeekApiResult

    /** HTTP 非 2xx。[body] 已截断，且不含任何请求头/凭据。 */
    data class HttpError(val statusCode: Int, val body: String?) : DeepSeekApiResult

    /** 连接超时/读取超时。 */
    data class Timeout(val message: String) : DeepSeekApiResult

    /** 网络层异常（DNS、连接失败等）。 */
    data class NetworkError(val message: String) : DeepSeekApiResult

    /** HTTP 成功但响应信封本身不是合法 JSON。 */
    data class MalformedEnvelope(val message: String) : DeepSeekApiResult
}

/**
 * 网络客户端抽象。实现可以是 OkHttp，也可以是测试里的假实现。
 */
interface DeepSeekApiClient {

    suspend fun completeChat(request: DeepSeekChatRequest): DeepSeekApiResult
}
