package app.danskly.data

import app.danskly.core.ai.AiErrorKind
import app.danskly.core.ai.AiException
import app.danskly.core.ai.ChatMessage
import app.danskly.core.ai.Role
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests the real HTTP client against a local fake of the Danskly server. */
class HttpAiClientTest {

    private val server = MockWebServer()
    private var code = ""
    private lateinit var client: HttpAiClient
    private val hello = listOf(ChatMessage(Role.USER, "Hej"))

    @Before
    fun setUp() {
        server.start()
        client = HttpAiClient(OkHttpClient(), { server.url("/").toString() }, { code }, "session-1")
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(status: Int, body: String, model: String? = null) {
        val b = MockResponse.Builder().code(status).body(body)
        if (model != null) b.addHeader("x-danskly-model", model)
        server.enqueue(b.build())
    }

    private fun expect(kind: AiErrorKind, block: suspend () -> Unit) = runBlocking {
        try {
            block()
            throw AssertionError("Expected $kind")
        } catch (e: AiException) {
            assertEquals(kind, e.kind)
        }
    }

    @Test
    fun completeSendsMessagesAndReadsTheAnswer() = runBlocking {
        code = "kode"
        enqueue(200, """{"text":"<think>x</think>Hej med dig","model":"m"}""", model = "deepseek-v4.1-flash")
        val reply = client.complete(hello, json = true)
        assertEquals("Hej med dig", reply.text)
        assertEquals("deepseek-v4.1-flash", reply.model)
        assertEquals("deepseek-v4.1-flash", client.lastModel)
        val request = server.takeRequest()
        assertEquals("/api/ai", request.url.encodedPath)
        assertEquals("kode", request.headers["x-access-code"])
        val body = request.body!!.utf8()
        assertTrue(body.contains("\"role\":\"user\"") && body.contains("\"json\":true") && body.contains("\"stream\":false") && body.contains("session-1"))
    }

    @Test
    fun modelHeaderIsOptional() = runBlocking {
        enqueue(200, """{"text":"a"}""")
        assertNull(client.complete(hello).model)
    }

    @Test
    fun emptyOrBrokenAnswersAreBadResponses() {
        enqueue(200, """{"text":"  "}""")
        expect(AiErrorKind.BAD_RESPONSE) { client.complete(hello) }
        enqueue(200, "not json")
        expect(AiErrorKind.BAD_RESPONSE) { client.complete(hello) }
    }

    @Test
    fun serverErrorsMapToFriendlyKinds() {
        enqueue(401, """{"code":"access_code"}""")
        expect(AiErrorKind.ACCESS_CODE) { client.complete(hello) }
        enqueue(401, "plain")
        expect(AiErrorKind.ACCESS_CODE) { client.complete(hello) }
        enqueue(429, """{"code":"rate_limited"}""")
        expect(AiErrorKind.RATE_LIMITED) { client.complete(hello) }
        enqueue(429, "")
        expect(AiErrorKind.RATE_LIMITED) { client.complete(hello) }
        enqueue(400, """{"code":"rate_limited"}""")
        expect(AiErrorKind.RATE_LIMITED) { client.complete(hello) }
        enqueue(403, """{"code":"access_code"}""")
        expect(AiErrorKind.ACCESS_CODE) { client.complete(hello) }
        enqueue(503, """{"code":"unavailable"}""")
        expect(AiErrorKind.UNAVAILABLE) { client.complete(hello) }
    }

    @Test
    fun missingOrInvalidServerAddressIsNotConfigured() {
        val noUrl = HttpAiClient(OkHttpClient(), { " " }, { "" }, "s")
        expect(AiErrorKind.NOT_CONFIGURED) { noUrl.complete(hello) }
        val badUrl = HttpAiClient(OkHttpClient(), { "not a url" }, { "" }, "s")
        expect(AiErrorKind.NOT_CONFIGURED) { badUrl.health() }
    }

    @Test
    fun unreachableServerIsANetworkError() {
        val url = server.url("/").toString()
        server.close()
        val offline = HttpAiClient(OkHttpClient(), { url }, { "" }, "s")
        expect(AiErrorKind.NETWORK) { offline.complete(hello) }
    }

    @Test
    fun streamEmitsGrowingText() = runBlocking {
        val sse = listOf("Hej", " med", " dig").joinToString("") { "data: {\"choices\":[{\"delta\":{\"content\":\"$it\"}}]}\n\n" } + "data: [DONE]\n\n"
        enqueue(200, sse, model = "glm-5.1")
        assertEquals(listOf("Hej", "Hej med", "Hej med dig"), client.stream(hello).toList())
        assertEquals("glm-5.1", client.lastModel)
        assertTrue(server.takeRequest().body!!.utf8().contains("\"stream\":true"))
    }

    @Test
    fun streamTimeoutBeforeAnyTextIsUnavailable() {
        enqueue(200, "data: {\"error\":\"timeout\"}\n\n")
        expect(AiErrorKind.UNAVAILABLE) { client.stream(hello).toList() }
    }

    @Test
    fun streamTimeoutAfterTextKeepsWhatArrived() = runBlocking {
        enqueue(200, "data: {\"choices\":[{\"delta\":{\"content\":\"Hej\"}}]}\n\ndata: {\"error\":\"timeout\"}\n\n")
        assertEquals(listOf("Hej"), client.stream(hello).toList())
    }

    @Test
    fun emptyStreamIsABadResponse() {
        enqueue(200, "data: [DONE]\n\n")
        expect(AiErrorKind.BAD_RESPONSE) { client.stream(hello).toList() }
    }

    @Test
    fun streamErrorsAreMapped() {
        enqueue(429, "{}")
        expect(AiErrorKind.RATE_LIMITED) { client.stream(hello).toList() }
    }

    @Test
    fun healthReportsKeysWithoutRevealingThem() = runBlocking {
        code = "abc"
        enqueue(200, """{"ok":true,"keys":3,"models":["a","b"],"accessCode":true}""")
        val h = client.health()
        assertEquals(3, h.keys)
        assertEquals(listOf("a", "b"), h.models)
        assertTrue(h.accessCodeRequired)
        val request = server.takeRequest()
        assertEquals("/api/health", request.url.encodedPath)
        assertEquals("abc", request.headers["x-access-code"])
        enqueue(200, """{"keys":0}""")
        assertFalse(client.health().accessCodeRequired)
    }

    @Test
    fun brokenHealthIsABadResponse() {
        enqueue(200, "<html>")
        expect(AiErrorKind.BAD_RESPONSE) { client.health() }
    }

    @Test
    fun reportSendsTheAnswerAndReason() = runBlocking {
        code = "k"
        enqueue(200, """{"ok":true}""")
        client.report("Bad answer", "offensive")
        val request = server.takeRequest()
        assertEquals("/api/report", request.url.encodedPath)
        assertEquals("k", request.headers["x-access-code"])
        val body = request.body!!.utf8()
        assertTrue(body.contains("\"content\":\"Bad answer\""))
        assertTrue(body.contains("\"reason\":\"offensive\""))
        assertTrue(body.contains("\"session\":\"session-1\""))
    }

    @Test
    fun reportErrorsAreMapped() {
        enqueue(500, "{}")
        expect(AiErrorKind.UNAVAILABLE) { client.report("x", "wrong") }
    }
}
