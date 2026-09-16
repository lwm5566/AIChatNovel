package com.aichatnovel.app.data.remote.deepseek

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * 基于 OkHttp 的实现。
 *
 * 边界：本类只做 HTTP 往返，把结果包装成 [DeepSeekApiResult]。
 * 它不解析业务 JSON、不接触 DTO、更不接触 domain。
 *
 * 安全：从不打印 Authorization 头或 API Key；日志里只出现模型名、字符数、HTTP 状态码。
 */
class OkHttpDeepSeekApiClient(
    private val config: DeepSeekConfig,
    private val json: Json = DeepSeekJson.instance,
    private val logger: DeepSeekLogger = DeepSeekLogger.NoOp,
    private val client: OkHttpClient = defaultClient(config),
) : DeepSeekApiClient {

    override suspend fun completeChat(request: DeepSeekChatRequest): DeepSeekApiResult {
        val apiKey = config.apiKey
        if (apiKey.isNullOrBlank()) return DeepSeekApiResult.MissingApiKey

        return withContext(Dispatchers.IO) {
            val payload = json.encodeToString(DeepSeekChatRequest.serializer(), request)
            logger.log(
                DeepSeekLogger.STAGE_REQUEST,
                "model=${request.model} messages=${request.messages.size} payloadChars=${payload.length} " +
                    "apiKey=${DeepSeekConfig.redact(apiKey)}",
            )

            val httpRequest = Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            try {
                client.newCall(httpRequest).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    logger.log(
                        DeepSeekLogger.STAGE_RAW_RESPONSE,
                        "status=${response.code} bodyChars=${body.length}",
                    )

                    if (!response.isSuccessful) {
                        return@use DeepSeekApiResult.HttpError(
                            statusCode = response.code,
                            body = body.take(MAX_ERROR_BODY_CHARS),
                        )
                    }

                    val envelope = try {
                        json.decodeFromString(DeepSeekChatResponse.serializer(), body)
                    } catch (e: SerializationException) {
                        return@use DeepSeekApiResult.MalformedEnvelope(
                            "响应信封不是合法 JSON：${e.message}",
                        )
                    }
                    DeepSeekApiResult.Success(envelope)
                }
            } catch (e: SocketTimeoutException) {
                DeepSeekApiResult.Timeout(e.message ?: "请求超时")
            } catch (e: IOException) {
                DeepSeekApiResult.NetworkError(e.message ?: "网络连接失败")
            }
        }
    }

    companion object {
        private const val MAX_ERROR_BODY_CHARS = 500

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(config: DeepSeekConfig): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(config.readTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()
    }
}

/** 网络层专用的 Json：容忍服务端新增字段。 */
internal object DeepSeekJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}
