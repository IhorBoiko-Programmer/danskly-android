package app.danskly.data

import app.danskly.core.ai.AiClient
import app.danskly.core.ai.AiErrorKind
import app.danskly.core.ai.AiException
import app.danskly.core.ai.AiReply
import app.danskly.core.ai.AiText
import app.danskly.core.ai.ChatMessage
import app.danskly.core.ai.ServerHealth
import app.danskly.core.ai.SseEvent
import app.danskly.core.ai.SseParser
import app.danskly.core.json.Json
import app.danskly.core.json.JsonException
import app.danskly.core.json.bool
import app.danskly.core.json.int
import app.danskly.core.json.json
import app.danskly.core.json.obj
import app.danskly.core.json.strOrNull
import app.danskly.core.json.strings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Talks to the Danskly server (`/api/ai`, `/api/health`). The server keeps the API keys and
 * falls back between keys and models; this client only sends messages and reads answers.
 */
class HttpAiClient(
    private val http: OkHttpClient,
    private val serverUrl: () -> String,
    private val accessCode: () -> String,
    private val sessionId: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AiClient {

    @Volatile
    override var lastModel: String? = null
        private set

    override suspend fun complete(messages: List<ChatMessage>, json: Boolean): AiReply = withContext(io) {
        execute(post(messages, stream = false, json = json)).use { response ->
            val text = try {
                Json.parse(response.body.string()).obj().strOrNull("text")
            } catch (e: JsonException) {
                null
            }
            val clean = AiText.stripThinking(text.orEmpty())
            if (clean.isBlank()) throw AiException(AiErrorKind.BAD_RESPONSE)
            AiReply(clean, lastModel)
        }
    }

    override fun stream(messages: List<ChatMessage>): Flow<String> = flow {
        execute(post(messages, stream = true, json = false)).use { response ->
            val source = response.body.source()
            val parser = SseParser()
            val text = StringBuilder()
            while (true) {
                val line = source.readUtf8Line() ?: break
                for (event in parser.feed(line + "\n")) {
                    when (event) {
                        is SseEvent.Delta -> {
                            text.append(event.text)
                            emit(AiText.stripThinking(text.toString()))
                        }
                        SseEvent.Timeout -> if (text.isBlank()) throw AiException(AiErrorKind.UNAVAILABLE)
                    }
                }
            }
            if (AiText.stripThinking(text.toString()).isBlank()) throw AiException(AiErrorKind.BAD_RESPONSE)
        }
    }.flowOn(io)

    override suspend fun health(): ServerHealth = withContext(io) {
        val request = Request.Builder().url(url("/api/health")).header(ACCESS_HEADER, accessCode()).get().build()
        execute(request).use { response ->
            try {
                val o = Json.parse(response.body.string()).obj()
                ServerHealth(keys = o.int("keys"), models = o.strings("models"), accessCodeRequired = o.bool("accessCode"))
            } catch (e: JsonException) {
                throw AiException(AiErrorKind.BAD_RESPONSE, e)
            }
        }
    }

    override suspend fun report(content: String, reason: String): Unit = withContext(io) {
        val body = json("content" to content, "reason" to reason, "session" to sessionId)
        val request = Request.Builder()
            .url(url("/api/report"))
            .header(ACCESS_HEADER, accessCode())
            .post(Json.write(body).toRequestBody(JSON_TYPE))
            .build()
        execute(request).close()
    }

    private fun post(messages: List<ChatMessage>, stream: Boolean, json: Boolean): Request {
        val body = json(
            "messages" to messages.map { json("role" to it.role.wire, "content" to it.content) },
            "stream" to stream,
            "json" to json,
            "session" to sessionId,
        )
        return Request.Builder()
            .url(url("/api/ai"))
            .header(ACCESS_HEADER, accessCode())
            .post(Json.write(body).toRequestBody(JSON_TYPE))
            .build()
    }

    private fun url(path: String): HttpUrl {
        val base = serverUrl().trim().trimEnd('/')
        if (base.isEmpty()) throw AiException(AiErrorKind.NOT_CONFIGURED)
        return "$base$path".toHttpUrlOrNull() ?: throw AiException(AiErrorKind.NOT_CONFIGURED)
    }

    private fun execute(request: Request): Response {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw AiException(AiErrorKind.NETWORK, e)
        }
        if (response.isSuccessful) {
            lastModel = response.header(MODEL_HEADER) ?: lastModel
            return response
        }
        val code = response.use {
            try {
                Json.parse(it.body.string()).obj().strOrNull("code")
            } catch (e: JsonException) {
                null
            }
        }
        throw AiException(
            when {
                code == "access_code" || response.code == HTTP_UNAUTHORIZED -> AiErrorKind.ACCESS_CODE
                code == "rate_limited" || response.code == HTTP_TOO_MANY -> AiErrorKind.RATE_LIMITED
                else -> AiErrorKind.UNAVAILABLE
            },
        )
    }

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        const val ACCESS_HEADER = "x-access-code"
        const val MODEL_HEADER = "x-danskly-model"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_TOO_MANY = 429
    }
}
