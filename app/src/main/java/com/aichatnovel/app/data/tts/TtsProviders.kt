package com.aichatnovel.app.data.tts

import com.aichatnovel.app.domain.model.AudioFormat
import com.aichatnovel.app.domain.model.SpeechParams
import com.aichatnovel.app.domain.model.TtsProviderId
import com.aichatnovel.app.domain.model.TtsRequest
import com.aichatnovel.app.repository.ProviderCredentialStore
import com.aichatnovel.app.repository.TtsFailure
import com.aichatnovel.app.repository.TtsProvider
import com.aichatnovel.app.repository.TtsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.UUID

// ---------------------------------------------------------------------------
// 供应商无关的共享部分
// ---------------------------------------------------------------------------

/** TTS 专用 Json：容忍供应商新增字段。 */
internal object TtsJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}

internal val JSON_MEDIA_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()

private const val MAX_ERROR_CHARS = 300

private fun missingCredentials(providerId: TtsProviderId) = TtsResult.Failure(
    TtsFailure.MISSING_CREDENTIALS,
    "${providerId.displayName} 未配置凭据，未发起请求",
)

/** 把 OkHttp 的异常统一归类；**绝不吞掉错误**。 */
private fun translateTransportErrors(providerId: TtsProviderId, block: () -> TtsResult): TtsResult = try {
    block()
} catch (e: SocketTimeoutException) {
    TtsResult.Failure(TtsFailure.TIMEOUT, "${providerId.displayName} 请求超时：${e.message}")
} catch (e: IOException) {
    TtsResult.Failure(TtsFailure.NETWORK, "无法连接 ${providerId.displayName}：${e.message}")
}

// ---------------------------------------------------------------------------
// Microsoft Azure Speech
// ---------------------------------------------------------------------------

/**
 * Microsoft Azure Speech 文本转语音（REST v1）。
 *
 * 官方 contract：
 * - `POST {endpoint}/cognitiveservices/v1`
 * - 认证：`Ocp-Apim-Subscription-Key`（本实现使用）
 * - `Content-Type: application/ssml+xml`，body 为 SSML
 * - `X-Microsoft-OutputFormat` 决定音频编码
 * - 响应是**二进制音频**，没有时长字段 → 时长必须由 `AudioDurationProbe` 从音频读
 */
class MicrosoftAzureTtsProvider(
    private val credentialStore: ProviderCredentialStore,
    private val client: OkHttpClient,
) : TtsProvider {

    override val id: TtsProviderId = TtsProviderId.AZURE

    override suspend fun synthesize(request: TtsRequest): TtsResult {
        val credentials = credentialStore.credentialsFor(id)
        val key = credentials?.secret?.takeIf { it.isNotBlank() } ?: return missingCredentials(id)
        val endpoint = credentials.endpoint
            ?.takeIf { it.isNotBlank() }
            ?.let { "${it.trimEnd('/')}/cognitiveservices/v1" }
            ?: return TtsResult.Failure(
                TtsFailure.MISSING_CREDENTIALS,
                "Azure 需要配置资源 endpoint（区域端点或资源域名）",
            )
        val voice = request.voiceId?.takeIf { it.isNotBlank() }
            ?: return TtsResult.Failure(
                TtsFailure.UNSUPPORTED_VOICE,
                "Azure 需要音色名（VoiceProfile.voiceRef），未提供",
            )
        val outputFormat = request.format.toAzureOutputFormat()
            ?: return TtsResult.Failure(TtsFailure.UNSUPPORTED_FORMAT, "Azure 不支持音频格式 ${request.format}")
        if (request.text.isBlank()) {
            return TtsResult.Failure(TtsFailure.TEXT_REJECTED, "文本为空，未发起请求")
        }

        val locale = request.locale?.takeIf { it.isNotBlank() } ?: DEFAULT_LOCALE
        val ssml = buildSsml(request.text, voice, locale, request.speechParams)

        return withContext(Dispatchers.IO) {
            translateTransportErrors(id) {
                val httpRequest = Request.Builder()
                    .url(endpoint)
                    .header("Ocp-Apim-Subscription-Key", key)
                    .header("X-Microsoft-OutputFormat", outputFormat)
                    .header("User-Agent", USER_AGENT)
                    .post(ssml.toRequestBody(SSML_MEDIA_TYPE))
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    val bytes = response.body?.bytes()
                    if (!response.isSuccessful) {
                        return@use TtsResult.Failure(
                            reason = when (response.code) {
                                HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> TtsFailure.UNAUTHORIZED
                                HTTP_BAD_REQUEST -> TtsFailure.TEXT_REJECTED
                                HTTP_NOT_FOUND -> TtsFailure.UNSUPPORTED_VOICE
                                else -> TtsFailure.HTTP_ERROR
                            },
                            message = "Azure HTTP ${response.code}：${bytes.errorSnippet()}",
                            httpStatus = response.code,
                        )
                    }
                    if (bytes == null || bytes.isEmpty()) {
                        return@use TtsResult.Failure(TtsFailure.INVALID_RESPONSE, "Azure 返回了空音频")
                    }
                    TtsResult.Success(
                        audio = bytes,
                        format = request.format,
                        providerMetadata = mapOf(
                            "voice" to voice,
                            "locale" to locale,
                            "outputFormat" to outputFormat,
                        ),
                    )
                }
            }
        }
    }

    private fun buildSsml(text: String, voice: String, locale: String, params: SpeechParams): String {
        val prosody = prosodyAttributes(params)
        val inner = if (prosody.isEmpty()) escapeXml(text) else "<prosody $prosody>${escapeXml(text)}</prosody>"
        return "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"" +
            escapeXml(locale) + "\"><voice name=\"" + escapeXml(voice) + "\">" + inner + "</voice></speak>"
    }

    /** 小说文本可能含 `<`、`&` 等字符，进入 SSML 前必须转义，否则请求会被拒绝或产生错误发音。 */
    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun prosodyAttributes(params: SpeechParams): String = listOfNotNull(
        params.rate?.let { "rate=\"${it.toProsodyPercent()}\"" },
        params.pitch?.let { "pitch=\"${it.toProsodyPercent()}\"" },
        params.volume?.let { "volume=\"${it.toProsodyPercent()}\"" },
    ).joinToString(" ")

    /** [SpeechParams] 以 1.0 表示「默认」，换算成 Azure prosody 的相对百分比。 */
    private fun Float.toProsodyPercent(): String {
        val delta = ((this - 1f) * 100).toInt()
        return if (delta >= 0) "+$delta%" else "$delta%"
    }

    companion object {
        const val DEFAULT_LOCALE = "zh-CN"

        private const val USER_AGENT = "AIChatNovel"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_NOT_FOUND = 404

        private val SSML_MEDIA_TYPE = "application/ssml+xml".toMediaType()

        /** 只接受官方文档列出的输出格式；不认识的一律判为不支持。 */
        fun AudioFormat.toAzureOutputFormat(): String? = when (this) {
            AudioFormat.MP3 -> "audio-16khz-128kbitrate-mono-mp3"
            AudioFormat.WAV -> "riff-16khz-16bit-mono-pcm"
            else -> null
        }
    }
}

// ---------------------------------------------------------------------------
// 火山引擎（豆包语音）V1 非流式
// ---------------------------------------------------------------------------

@Serializable
private data class VolcengineApp(
    val appid: String,
    val token: String,
    val cluster: String,
)

@Serializable
private data class VolcengineUser(val uid: String)

@Serializable
private data class VolcengineAudio(
    @SerialName("voice_type") val voiceType: String,
    val encoding: String,
    @SerialName("speed_ratio") val speedRatio: Float,
    @SerialName("volume_ratio") val volumeRatio: Float,
    @SerialName("pitch_ratio") val pitchRatio: Float,
)

@Serializable
private data class VolcengineRequestBody(
    val reqid: String,
    val text: String,
    val operation: String,
)

@Serializable
private data class VolcengineTtsPayload(
    val app: VolcengineApp,
    val user: VolcengineUser,
    val audio: VolcengineAudio,
    val request: VolcengineRequestBody,
)

@Serializable
private data class VolcengineTtsResponse(
    val reqid: String? = null,
    val code: Int? = null,
    val message: String? = null,
    val sequence: Int? = null,
    val data: String? = null,
)

/**
 * 火山引擎（豆包语音）V1 非流式语音合成。
 *
 * 官方 contract：
 * - `POST https://openspeech.bytedance.com/api/v1/tts`
 * - 认证头格式为 `Authorization: Bearer;{access_token}`（**分号分隔**，不是空格）
 * - body：`app{appid,token,cluster}` / `user{uid}` / `audio{voice_type,encoding,…}` / `request{reqid,text,operation:"query"}`
 * - 响应：`{reqid, code, message, sequence, data}`，`data` 是 base64 音频
 */
class VolcengineTtsProvider(
    private val credentialStore: ProviderCredentialStore,
    private val client: OkHttpClient,
    private val endpoint: String = DEFAULT_ENDPOINT,
) : TtsProvider {

    override val id: TtsProviderId = TtsProviderId.VOLCENGINE

    override suspend fun synthesize(request: TtsRequest): TtsResult {
        val credentials = credentialStore.credentialsFor(id)
        val token = credentials?.secret?.takeIf { it.isNotBlank() } ?: return missingCredentials(id)
        val appId = credentials.appId?.takeIf { it.isNotBlank() }
            ?: return TtsResult.Failure(TtsFailure.MISSING_CREDENTIALS, "火山引擎需要配置 appId")
        val voiceType = request.voiceId?.takeIf { it.isNotBlank() }
            ?: return TtsResult.Failure(
                TtsFailure.UNSUPPORTED_VOICE,
                "火山引擎需要音色代号（VoiceProfile.voiceRef），未提供",
            )
        val encoding = request.format.toVolcengineEncoding()
            ?: return TtsResult.Failure(TtsFailure.UNSUPPORTED_FORMAT, "火山引擎不支持音频格式 ${request.format}")
        if (request.text.isBlank()) {
            return TtsResult.Failure(TtsFailure.TEXT_REJECTED, "文本为空，未发起请求")
        }

        val cluster = credentials.cluster?.takeIf { it.isNotBlank() } ?: DEFAULT_CLUSTER
        val payload = TtsJson.instance.encodeToString(
            VolcengineTtsPayload.serializer(),
            VolcengineTtsPayload(
                app = VolcengineApp(appid = appId, token = token, cluster = cluster),
                user = VolcengineUser(uid = DEFAULT_UID),
                audio = VolcengineAudio(
                    voiceType = voiceType,
                    encoding = encoding,
                    speedRatio = request.speechParams.rate ?: 1.0f,
                    volumeRatio = request.speechParams.volume ?: 1.0f,
                    pitchRatio = request.speechParams.pitch ?: 1.0f,
                ),
                request = VolcengineRequestBody(
                    reqid = UUID.randomUUID().toString(),
                    text = request.text,
                    operation = OPERATION_QUERY,
                ),
            ),
        )

        return withContext(Dispatchers.IO) {
            translateTransportErrors(id) {
                val httpRequest = Request.Builder()
                    .url(endpoint)
                    .header("Authorization", "Bearer;$token")
                    .header("User-Agent", USER_AGENT)
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@use TtsResult.Failure(
                            reason = when (response.code) {
                                HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> TtsFailure.UNAUTHORIZED
                                else -> TtsFailure.HTTP_ERROR
                            },
                            message = "火山引擎 HTTP ${response.code}：${bodyText.take(MAX_ERROR_CHARS)}",
                            httpStatus = response.code,
                        )
                    }

                    val parsed = try {
                        TtsJson.instance.decodeFromString(VolcengineTtsResponse.serializer(), bodyText)
                    } catch (e: SerializationException) {
                        return@use TtsResult.Failure(
                            TtsFailure.INVALID_RESPONSE,
                            "火山引擎响应不是合法 JSON：${e.message}",
                        )
                    }

                    if (parsed.code != SUCCESS_CODE) {
                        return@use TtsResult.Failure(
                            reason = parsed.code.toVolcengineFailure(),
                            message = "火山引擎错误 code=${parsed.code} message=${parsed.message}",
                            httpStatus = response.code,
                        )
                    }

                    val encoded = parsed.data
                    if (encoded.isNullOrBlank()) {
                        return@use TtsResult.Failure(TtsFailure.INVALID_RESPONSE, "火山引擎响应缺少 audio data")
                    }
                    val audio = try {
                        Base64.getDecoder().decode(encoded)
                    } catch (e: IllegalArgumentException) {
                        return@use TtsResult.Failure(TtsFailure.INVALID_RESPONSE, "火山引擎音频 base64 解码失败")
                    }
                    if (audio.isEmpty()) {
                        return@use TtsResult.Failure(TtsFailure.INVALID_RESPONSE, "火山引擎返回了空音频")
                    }

                    TtsResult.Success(
                        audio = audio,
                        format = request.format,
                        providerMetadata = mapOf(
                            "voice_type" to voiceType,
                            "encoding" to encoding,
                            "cluster" to cluster,
                            "reqid" to parsed.reqid.orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://openspeech.bytedance.com/api/v1/tts"
        const val DEFAULT_CLUSTER = "volcano_tts"

        private const val USER_AGENT = "AIChatNovel"
        private const val DEFAULT_UID = "aichatnovel"
        private const val OPERATION_QUERY = "query"
        private const val SUCCESS_CODE = 3000

        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        // 官方错误码（HTTP 200 时也可能返回业务错误）
        private const val CODE_TEXT_TOO_LONG = 3010
        private const val CODE_INVALID_TEXT = 3011
        private const val CODE_VOICE_NOT_FOUND = 3050

        fun AudioFormat.toVolcengineEncoding(): String? = when (this) {
            AudioFormat.MP3 -> "mp3"
            AudioFormat.WAV -> "wav"
            AudioFormat.AAC -> "aac"
            else -> null
        }

        private fun Int?.toVolcengineFailure(): TtsFailure = when (this) {
            CODE_TEXT_TOO_LONG, CODE_INVALID_TEXT -> TtsFailure.TEXT_REJECTED
            CODE_VOICE_NOT_FOUND -> TtsFailure.UNSUPPORTED_VOICE
            else -> TtsFailure.HTTP_ERROR
        }
    }
}

// ---------------------------------------------------------------------------
// 小米 MiMo（OpenAI 兼容 Chat Completions）
// ---------------------------------------------------------------------------

@Serializable
private data class MiMoMessage(val role: String, val content: String)

@Serializable
private data class MiMoAudioOptions(val voice: String, val format: String)

@Serializable
private data class MiMoTtsPayload(
    val model: String,
    val messages: List<MiMoMessage>,
    val audio: MiMoAudioOptions,
    val stream: Boolean = false,
)

@Serializable
private data class MiMoAudioPayload(val data: String? = null, val format: String? = null)

@Serializable
private data class MiMoResponseMessage(
    val role: String? = null,
    val content: String? = null,
    val audio: MiMoAudioPayload? = null,
)

@Serializable
private data class MiMoChoice(val index: Int = 0, val message: MiMoResponseMessage? = null)

@Serializable
private data class MiMoChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<MiMoChoice> = emptyList(),
)

/**
 * 小米 MiMo 语音合成（OpenAI 兼容的 Chat Completions 端点）。
 *
 * 官方 contract：
 * - `POST https://api.xiaomimimo.com/v1/chat/completions`
 * - 认证：`api-key: {key}`（亦兼容 `Authorization: Bearer`）
 * - body：`model = "mimo-v2.5-tts"` + `messages` + `audio{voice,format}`
 * - 响应音频在 `choices[0].message.audio.data`（base64）
 *
 * 注意：MiMo 是 **TTS**，与 DeepSeek 文本 Provider 是两种能力，不共用协议。
 */
class XiaomiMiMoTtsProvider(
    private val credentialStore: ProviderCredentialStore,
    private val client: OkHttpClient,
    private val endpoint: String = DEFAULT_ENDPOINT,
) : TtsProvider {

    override val id: TtsProviderId = TtsProviderId.XIAOMI_MIMO

    override suspend fun synthesize(request: TtsRequest): TtsResult {
        val key = credentialStore.credentialsFor(id)?.secret?.takeIf { it.isNotBlank() }
            ?: return missingCredentials(id)
        val voice = request.voiceId?.takeIf { it.isNotBlank() }
            ?: return TtsResult.Failure(
                TtsFailure.UNSUPPORTED_VOICE,
                "MiMo 需要音色（VoiceProfile.voiceRef），未提供",
            )
        if (voice !in BUILT_IN_VOICES) {
            return TtsResult.Failure(
                TtsFailure.UNSUPPORTED_VOICE,
                "MiMo 内置音色不含「$voice」，可用：${BUILT_IN_VOICES.joinToString("、")}",
            )
        }
        val format = request.format.toMiMoFormat()
            ?: return TtsResult.Failure(TtsFailure.UNSUPPORTED_FORMAT, "MiMo 不支持音频格式 ${request.format}")
        if (request.text.isBlank()) {
            return TtsResult.Failure(TtsFailure.TEXT_REJECTED, "文本为空，未发起请求")
        }

        val payload = TtsJson.instance.encodeToString(
            MiMoTtsPayload.serializer(),
            MiMoTtsPayload(
                model = MODEL_ID,
                messages = listOf(MiMoMessage(role = ROLE_USER, content = request.text)),
                audio = MiMoAudioOptions(voice = voice, format = format),
            ),
        )

        return withContext(Dispatchers.IO) {
            translateTransportErrors(id) {
                val httpRequest = Request.Builder()
                    .url(endpoint)
                    .header("api-key", key)
                    .header("User-Agent", USER_AGENT)
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@use TtsResult.Failure(
                            reason = when (response.code) {
                                HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> TtsFailure.UNAUTHORIZED
                                HTTP_BAD_REQUEST -> TtsFailure.TEXT_REJECTED
                                else -> TtsFailure.HTTP_ERROR
                            },
                            message = "MiMo HTTP ${response.code}：${bodyText.take(MAX_ERROR_CHARS)}",
                            httpStatus = response.code,
                        )
                    }

                    val parsed = try {
                        TtsJson.instance.decodeFromString(MiMoChatResponse.serializer(), bodyText)
                    } catch (e: SerializationException) {
                        return@use TtsResult.Failure(
                            TtsFailure.INVALID_RESPONSE,
                            "MiMo 响应不是合法 JSON：${e.message}",
                        )
                    }

                    val encoded = parsed.choices.firstOrNull()?.message?.audio?.data
                    if (encoded.isNullOrBlank()) {
                        return@use TtsResult.Failure(
                            TtsFailure.INVALID_RESPONSE,
                            "MiMo 响应中缺少 choices[0].message.audio.data",
                        )
                    }
                    val audio = try {
                        Base64.getDecoder().decode(encoded)
                    } catch (e: IllegalArgumentException) {
                        return@use TtsResult.Failure(TtsFailure.INVALID_RESPONSE, "MiMo 音频 base64 解码失败")
                    }
                    if (audio.isEmpty()) {
                        return@use TtsResult.Failure(TtsFailure.INVALID_RESPONSE, "MiMo 返回了空音频")
                    }

                    TtsResult.Success(
                        audio = audio,
                        format = request.format,
                        providerMetadata = mapOf(
                            "voice" to voice,
                            "format" to format,
                            "model" to MODEL_ID,
                        ),
                    )
                }
            }
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.xiaomimimo.com/v1/chat/completions"
        const val MODEL_ID = "mimo-v2.5-tts"

        /** 官方文档列出的内置音色；不在列表内的一律拒绝，不盲发。 */
        val BUILT_IN_VOICES = listOf(
            "mimo_default",
            "冰糖",
            "茉莉",
            "苏打",
            "白桦",
            "Mia",
            "Chloe",
            "Milo",
            "Dean",
        )

        private const val USER_AGENT = "AIChatNovel"
        private const val ROLE_USER = "user"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_BAD_REQUEST = 400

        fun AudioFormat.toMiMoFormat(): String? = when (this) {
            AudioFormat.MP3 -> "mp3"
            AudioFormat.WAV -> "wav"
            else -> null
        }
    }
}

private fun ByteArray?.errorSnippet(): String = when {
    this == null || isEmpty() -> "(无响应体)"
    else -> String(this, Charsets.UTF_8).take(MAX_ERROR_CHARS)
}
