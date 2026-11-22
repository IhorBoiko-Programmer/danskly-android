package app.danskly.core.ai

import kotlinx.coroutines.flow.Flow

enum class Role(val wire: String) { USER("user"), ASSISTANT("assistant") }

data class ChatMessage(val role: Role, val content: String)

data class AiReply(val text: String, val model: String?)

/** What the Danskly server reports about itself (never the keys themselves). */
data class ServerHealth(val keys: Int, val models: List<String>, val accessCodeRequired: Boolean)

enum class AiErrorKind(val message: String) {
    ACCESS_CODE("Потрібен код доступу. Введи його в Налаштуваннях (шестерня вгорі праворуч)."),
    RATE_LIMITED("Забагато запитів. Зачекай кілька хвилин."),
    UNAVAILABLE("Усі ключі й моделі зараз недоступні. Спробуй за хвилину."),
    NETWORK("Немає з’єднання з сервером. Перевір інтернет."),
    BAD_RESPONSE("ШІ відповів незрозуміло. Спробуй ще раз."),
    NOT_CONFIGURED("ШІ ще не налаштований: адреса сервера не вказана."),
}

class AiException(val kind: AiErrorKind, cause: Throwable? = null) : Exception(kind.message, cause)

/**
 * Talks to the Danskly AI server. The server holds the API keys and handles fallback
 * between keys and models, so the app never sees a key.
 */
interface AiClient {
    /** The model that answered the latest request, if the server said so. */
    val lastModel: String?

    suspend fun complete(messages: List<ChatMessage>, json: Boolean = false): AiReply

    /** Streams the answer; every emission is the whole text received so far. */
    fun stream(messages: List<ChatMessage>): Flow<String>

    suspend fun health(): ServerHealth

    /** Reports an offensive or wrong AI answer (required by Google Play for AI features). */
    suspend fun report(content: String, reason: String)
}
