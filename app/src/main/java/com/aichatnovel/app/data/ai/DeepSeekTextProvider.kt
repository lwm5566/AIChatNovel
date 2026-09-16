package com.aichatnovel.app.data.ai

import com.aichatnovel.app.data.remote.deepseek.DeepSeekApiClient
import com.aichatnovel.app.data.remote.deepseek.DeepSeekApiResult
import com.aichatnovel.app.data.remote.deepseek.DeepSeekChatRequest
import com.aichatnovel.app.data.remote.deepseek.DeepSeekConfig
import com.aichatnovel.app.data.remote.deepseek.DeepSeekMessage
import com.aichatnovel.app.data.remote.deepseek.DeepSeekResponseFormat
import com.aichatnovel.app.repository.AiTextFailure
import com.aichatnovel.app.repository.AiTextProvider
import com.aichatnovel.app.repository.AiTextRequest
import com.aichatnovel.app.repository.AiTextResult

/**
 * DeepSeek 的文本补全实现。
 *
 * 职责边界（Phase 6C 整理后）：
 * - 本类负责「把 [AiTextRequest] 发出去」以及「从响应**信封**里取出正文文本」；
 * - 它**不**解析业务 JSON、**不**接触 `ParseResponseDto`、**不**接触 domain。
 *
 * 因此 `DeepSeekChatResponse.choices[0].message.content` 这一层拆信封的逻辑落在 provider，
 * 而 `ParseJson → ParseResponseDto → ParseValidator → AiParseMapper` 仍是唯一一条解析链路。
 */
class DeepSeekTextProvider(
    private val apiClient: DeepSeekApiClient,
    private val config: DeepSeekConfig,
) : AiTextProvider {

    override val id: String = ID

    override suspend fun complete(request: AiTextRequest): AiTextResult {
        val messages = buildList {
            request.systemPrompt
                ?.takeIf { it.isNotBlank() }
                ?.let { add(DeepSeekMessage.system(it)) }
            add(DeepSeekMessage.user(request.userPrompt))
        }

        val apiRequest = DeepSeekChatRequest(
            model = config.model,
            messages = messages,
            temperature = request.temperature ?: config.temperature,
            maxTokens = request.maxTokens ?: config.maxTokens,
            responseFormat = if (request.requireJsonObject) DeepSeekResponseFormat.JSON_OBJECT else null,
        )

        return when (val result = apiClient.completeChat(apiRequest)) {
            is DeepSeekApiResult.Success -> result.toText()
            DeepSeekApiResult.MissingApiKey -> AiTextResult.Failure(
                AiTextFailure.MISSING_CREDENTIALS,
                "未配置 DeepSeek API Key，未发起请求",
            )

            is DeepSeekApiResult.HttpError -> AiTextResult.Failure(
                reason = when (result.statusCode) {
                    HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> AiTextFailure.UNAUTHORIZED
                    else -> AiTextFailure.HTTP_ERROR
                },
                message = "DeepSeek 返回 HTTP ${result.statusCode}",
                httpStatus = result.statusCode,
            )

            is DeepSeekApiResult.Timeout -> AiTextResult.Failure(
                AiTextFailure.TIMEOUT,
                "请求 DeepSeek 超时：${result.message}",
            )

            is DeepSeekApiResult.NetworkError -> AiTextResult.Failure(
                AiTextFailure.NETWORK,
                "无法连接 DeepSeek：${result.message}",
            )

            is DeepSeekApiResult.MalformedEnvelope -> AiTextResult.Failure(
                AiTextFailure.MALFORMED_RESPONSE,
                result.message,
            )
        }
    }

    private fun DeepSeekApiResult.Success.toText(): AiTextResult {
        val content = response.choices.firstOrNull()?.message?.content
        if (content.isNullOrBlank()) {
            return AiTextResult.Failure(
                AiTextFailure.EMPTY_RESPONSE,
                "模型响应中没有可用正文（choices 为空或 content 为空）",
            )
        }
        return AiTextResult.Success(text = content.trim(), model = response.model)
    }

    companion object {
        const val ID = "deepseek"

        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }
}
