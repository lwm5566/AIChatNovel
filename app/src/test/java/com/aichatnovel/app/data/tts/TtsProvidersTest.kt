package com.aichatnovel.app.data.tts

import com.aichatnovel.app.data.repository.InMemoryProviderCredentialStore
import com.aichatnovel.app.domain.model.AudioFormat
import com.aichatnovel.app.domain.model.SpeechParams
import com.aichatnovel.app.domain.model.TtsProviderId
import com.aichatnovel.app.domain.model.TtsRequest
import com.aichatnovel.app.repository.ProviderCredentials
import com.aichatnovel.app.repository.TtsFailure
import com.aichatnovel.app.repository.TtsResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * 三个 TTS Provider 的请求/响应契约。
 *
 * 这些测试**不访问真实供应商**：全部走本地 MockWebServer，用 fake 凭据。
 * 它们锁定的是协议映射与错误分类，不代表任何真实 API 已验证通过。
 */
class TtsProvidersTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun request(
        text: String = "你好，世界",
        voice: String? = "voice-x",
        format: AudioFormat = AudioFormat.MP3,
        params: SpeechParams = SpeechParams(),
        locale: String? = "zh-CN",
    ) = TtsRequest(text = text, voiceId = voice, locale = locale, speechParams = params, format = format)

    // ---------------- Azure ----------------

    @Test
    fun `azure maps the request into escaped ssml and returns the binary audio`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(1, 2, 3))))
            server.start()
            val store = InMemoryProviderCredentialStore()
            store.put(
                ProviderCredentials(
                    providerId = TtsProviderId.AZURE,
                    secret = "fake-azure-key",
                    endpoint = server.url("/").toString().trimEnd('/'),
                ),
            )
            val provider = MicrosoftAzureTtsProvider(store, OkHttpClient())

            val result = provider.synthesize(request(text = "他写下 <3 & \"你\""))
            server.takeRequest().let { recorded ->
                assertEquals("fake-azure-key", recorded.getHeader("Ocp-Apim-Subscription-Key"))
                assertEquals("audio-16khz-128kbitrate-mono-mp3", recorded.getHeader("X-Microsoft-OutputFormat"))
                val body = recorded.body.readUtf8()
                assertTrue(body.contains("&lt;3 &amp; &quot;你&quot;"))
                assertTrue(body.contains("<voice name=\"voice-x\">"))
                assertTrue(body.contains("xml:lang=\"zh-CN\""))
            }

            assertTrue(result is TtsResult.Success)
            assertEquals(AudioFormat.MP3, (result as TtsResult.Success).format)
            assertEquals(3, result.audio.size)
        }
    }

    @Test
    fun `azure maps speech params into prosody`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(9))))
            server.start()
            val store = InMemoryProviderCredentialStore()
            store.put(ProviderCredentials(TtsProviderId.AZURE, secret = "k", endpoint = server.url("/").toString()))
            val provider = MicrosoftAzureTtsProvider(store, OkHttpClient())

            provider.synthesize(request(params = SpeechParams(rate = 1.5f, pitch = 0.5f)))
            val body = server.takeRequest().body.readUtf8()

            assertTrue(body.contains("rate=\"+50%\""))
            assertTrue(body.contains("pitch=\"-50%\""))
        }
    }

    @Test
    fun `azure without credentials never fires a request`() = runTest {
        val provider = MicrosoftAzureTtsProvider(InMemoryProviderCredentialStore(), OkHttpClient())

        val result = provider.synthesize(request())

        assertEquals(TtsFailure.MISSING_CREDENTIALS, (result as TtsResult.Failure).reason)
    }

    @Test
    fun `azure without a voice id is rejected instead of guessing one`() = runTest {
        val store = InMemoryProviderCredentialStore()
        store.put(ProviderCredentials(TtsProviderId.AZURE, secret = "k", endpoint = "https://example.invalid"))
        val provider = MicrosoftAzureTtsProvider(store, OkHttpClient())

        val result = provider.synthesize(request(voice = null))

        assertEquals(TtsFailure.UNSUPPORTED_VOICE, (result as TtsResult.Failure).reason)
    }

    @Test
    fun `azure http 401 becomes unauthorized`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("denied"))
            server.start()
            val store = InMemoryProviderCredentialStore()
            store.put(ProviderCredentials(TtsProviderId.AZURE, secret = "k", endpoint = server.url("/").toString()))
            val provider = MicrosoftAzureTtsProvider(store, OkHttpClient())

            val result = provider.synthesize(request())

            assertEquals(TtsFailure.UNAUTHORIZED, (result as TtsResult.Failure).reason)
            assertEquals(401, result.httpStatus)
        }
    }

    // ---------------- 火山引擎 ----------------

    @Test
    fun `volcengine uses the semicolon bearer format and decodes base64 audio`() = runTest {
        MockWebServer().use { server ->
            val audio = byteArrayOf(4, 5, 6, 7)
            server.enqueue(
                MockResponse().setBody(
                    """{"reqid":"r1","code":3000,"message":"ok","sequence":0,""" +
                        """"data":"${Base64.getEncoder().encodeToString(audio)}"}""",
                ),
            )
            server.start()
            val store = InMemoryProviderCredentialStore()
            store.put(
                ProviderCredentials(
                    providerId = TtsProviderId.VOLCENGINE,
                    secret = "fake-token",
                    appId = "123456",
                    cluster = "volcano_tts",
                ),
            )
            val provider = VolcengineTtsProvider(store, OkHttpClient(), server.url("/").toString())

            val result = provider.synthesize(request(voice = "zh_female_qingxin"))
            val recorded = server.takeRequest()
            assertEquals("Bearer;fake-token", recorded.getHeader("Authorization"))

            val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
            assertEquals("123456", body["app"]!!.jsonObject["appid"]!!.jsonPrimitive.content)
            assertEquals("volcano_tts", body["app"]!!.jsonObject["cluster"]!!.jsonPrimitive.content)
            assertEquals("query", body["request"]!!.jsonObject["operation"]!!.jsonPrimitive.content)
            assertEquals("你好，世界", body["request"]!!.jsonObject["text"]!!.jsonPrimitive.content)
            assertEquals("zh_female_qingxin", body["audio"]!!.jsonObject["voice_type"]!!.jsonPrimitive.content)
            assertEquals("mp3", body["audio"]!!.jsonObject["encoding"]!!.jsonPrimitive.content)

            assertTrue(result is TtsResult.Success)
            assertEquals(listOf<Byte>(4, 5, 6, 7), (result as TtsResult.Success).audio.toList())
        }
    }

    @Test
    fun `volcengine business error codes are classified`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":3050,"message":"voice not found"}"""))
            server.start()
            val store = InMemoryProviderCredentialStore()
            store.put(ProviderCredentials(TtsProviderId.VOLCENGINE, secret = "t", appId = "1"))
            val provider = VolcengineTtsProvider(store, OkHttpClient(), server.url("/").toString())

            val result = provider.synthesize(request())

            assertEquals(TtsFailure.UNSUPPORTED_VOICE, (result as TtsResult.Failure).reason)
        }
    }

    @Test
    fun `volcengine missing app id is reported as missing credentials`() = runTest {
        val store = InMemoryProviderCredentialStore()
        store.put(ProviderCredentials(TtsProviderId.VOLCENGINE, secret = "t", appId = null))
        val provider = VolcengineTtsProvider(store, OkHttpClient())

        val result = provider.synthesize(request())

        assertEquals(TtsFailure.MISSING_CREDENTIALS, (result as TtsResult.Failure).reason)
    }

    @Test
    fun `volcengine broken base64 is an invalid response`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":3000,"data":"not-base64!!"}"""))
            server.start()
            val store = InMemoryProviderCredentialStore()
            store.put(ProviderCredentials(TtsProviderId.VOLCENGINE, secret = "t", appId = "1"))
            val provider = VolcengineTtsProvider(store, OkHttpClient(), server.url("/").toString())

            val result = provider.synthesize(request())

            assertEquals(TtsFailure.INVALID_RESPONSE, (result as TtsResult.Failure).reason)
        }
    }

    // ---------------- 小米 MiMo ----------------

    @Test
    fun `mimo posts an openai compatible payload and reads the audio from the message`() = runTest {
        MockWebServer().use { server ->
            val audio = byteArrayOf(11, 12)
            server.enqueue(
                MockResponse().setBody(
                    """{"id":"1","model":"mimo-v2.5-tts","choices":[{"index":0,"message":""" +
                        """{"role":"assistant","audio":{"data":"${Base64.getEncoder().encodeToString(audio)}","format":"mp3"}}}]}""",
                ),
            )
            server.start()
            val store = InMemoryProviderCredentialStore()
            store.put(ProviderCredentials(TtsProviderId.XIAOMI_MIMO, secret = "fake-mimo-key"))
            val provider = XiaomiMiMoTtsProvider(store, OkHttpClient(), server.url("/").toString())

            val result = provider.synthesize(request(voice = "冰糖"))
            val recorded = server.takeRequest()
            assertEquals("fake-mimo-key", recorded.getHeader("api-key"))

            val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
            assertEquals("mimo-v2.5-tts", body["model"]!!.jsonPrimitive.content)
            assertEquals("冰糖", body["audio"]!!.jsonObject["voice"]!!.jsonPrimitive.content)

            assertTrue(result is TtsResult.Success)
            assertEquals(listOf<Byte>(11, 12), (result as TtsResult.Success).audio.toList())
        }
    }

    @Test
    fun `mimo rejects a voice outside the documented built-in list`() = runTest {
        val store = InMemoryProviderCredentialStore()
        store.put(ProviderCredentials(TtsProviderId.XIAOMI_MIMO, secret = "k"))
        val provider = XiaomiMiMoTtsProvider(store, OkHttpClient())

        val result = provider.synthesize(request(voice = "不存在的音色"))

        assertEquals(TtsFailure.UNSUPPORTED_VOICE, (result as TtsResult.Failure).reason)
    }

    @Test
    fun `mimo response without audio data is an invalid response`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"hi"}}]}"""))
            server.start()
            val store = InMemoryProviderCredentialStore()
            store.put(ProviderCredentials(TtsProviderId.XIAOMI_MIMO, secret = "k"))
            val provider = XiaomiMiMoTtsProvider(store, OkHttpClient(), server.url("/").toString())

            val result = provider.synthesize(request(voice = "mimo_default"))

            assertEquals(TtsFailure.INVALID_RESPONSE, (result as TtsResult.Failure).reason)
        }
    }

    @Test
    fun `an unsupported audio format is rejected before any request`() = runTest {
        val store = InMemoryProviderCredentialStore()
        store.put(ProviderCredentials(TtsProviderId.XIAOMI_MIMO, secret = "k"))
        val provider = XiaomiMiMoTtsProvider(store, OkHttpClient())

        val result = provider.synthesize(request(voice = "mimo_default", format = AudioFormat.PCM))

        assertEquals(TtsFailure.UNSUPPORTED_FORMAT, (result as TtsResult.Failure).reason)
    }

    @Test
    fun `empty text is rejected instead of being sent`() = runTest {
        val store = InMemoryProviderCredentialStore()
        store.put(ProviderCredentials(TtsProviderId.AZURE, secret = "k", endpoint = "https://example.invalid"))
        val provider = MicrosoftAzureTtsProvider(store, OkHttpClient())

        val result = provider.synthesize(request(text = "   "))

        assertEquals(TtsFailure.TEXT_REJECTED, (result as TtsResult.Failure).reason)
    }
}
