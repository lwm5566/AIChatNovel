package com.aichatnovel.app.data.remote.deepseek

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 用本地 MockWebServer 验证 HTTP 层的真实行为（不打 DeepSeek）。
 */
class OkHttpDeepSeekApiClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config(readTimeoutMillis: Long = 5_000L, apiKey: String? = TEST_KEY) = DeepSeekConfig(
        baseUrl = server.url("/").toString().trimEnd('/'),
        model = "deepseek-chat",
        apiKey = apiKey,
        connectTimeoutMillis = 2_000L,
        readTimeoutMillis = readTimeoutMillis,
    )

    private val request = DeepSeekChatRequest(
        model = "deepseek-chat",
        messages = listOf(DeepSeekMessage.user("hi")),
    )

    private suspend fun call(readTimeoutMillis: Long = 5_000L, logger: DeepSeekLogger = DeepSeekLogger.NoOp) =
        OkHttpDeepSeekApiClient(config(readTimeoutMillis), logger = logger).completeChat(request)

    @Test
    fun `successful http response returns the envelope`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                Json.encodeToString(
                    DeepSeekChatResponse.serializer(),
                    DeepSeekChatResponse(
                        id = "1",
                        model = "deepseek-chat",
                        choices = listOf(DeepSeekChoice(0, DeepSeekMessage("assistant", "{ }"), "stop")),
                    ),
                ),
            ),
        )

        val result = call()

        val success = result as DeepSeekApiResult.Success
        assertEquals("{ }", success.response.choices.single().message?.content)
    }

    @Test
    fun `http 401 is reported as http error with status code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Authentication Fails"}}"""))

        val result = call()

        val error = result as DeepSeekApiResult.HttpError
        assertEquals(401, error.statusCode)
        assertTrue(error.body.orEmpty().contains("Authentication"))
    }

    @Test
    fun `http 500 is reported as http error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        assertEquals(500, (call() as DeepSeekApiResult.HttpError).statusCode)
    }

    @Test
    fun `non json body on 200 is reported as malformed envelope`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("this is not json"))

        assertTrue(call() is DeepSeekApiResult.MalformedEnvelope)
    }

    @Test
    fun `read timeout is reported as timeout`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        assertTrue(call(readTimeoutMillis = 300L) is DeepSeekApiResult.Timeout)
    }

    @Test
    fun `missing api key short circuits without hitting the network`() = runTest {
        val result = OkHttpDeepSeekApiClient(config(apiKey = null)).completeChat(request)

        assertEquals(DeepSeekApiResult.MissingApiKey, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `authorization header is sent but never logged`() = runTest {
        val records = mutableListOf<String>()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[]}"""))

        call(logger = DeepSeekLogger { stage, message -> records += "$stage $message" })

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/chat/completions", recorded.path)
        assertEquals("Bearer $TEST_KEY", recorded.getHeader("Authorization"))

        assertTrue(records.any { it.startsWith(DeepSeekLogger.STAGE_REQUEST) })
        assertTrue(records.any { it.startsWith(DeepSeekLogger.STAGE_RAW_RESPONSE) })
        assertTrue("日志中不允许出现 API Key", records.none { it.contains(TEST_KEY) })
        assertFalse(records.any { it.contains("Bearer") })
    }

    private companion object {
        const val TEST_KEY = "test-key-not-real-value"
    }
}
