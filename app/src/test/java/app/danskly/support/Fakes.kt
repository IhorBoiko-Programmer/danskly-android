package app.danskly.support

import app.danskly.AppGraph
import app.danskly.core.TestContent
import app.danskly.core.ai.AiClient
import app.danskly.core.ai.AiErrorKind
import app.danskly.core.ai.AiException
import app.danskly.core.ai.AiReply
import app.danskly.core.ai.ChatMessage
import app.danskly.core.ai.ServerHealth
import app.danskly.core.learning.LearningRepository
import app.danskly.core.navigation.Navigator
import app.danskly.core.progress.InMemoryProgressStore
import app.danskly.core.speech.Speaker
import app.danskly.core.speech.Voice
import app.danskly.core.time.FixedClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/** Records what would have been spoken. */
class FakeSpeaker(voices: List<Voice> = emptyList(), available: Boolean = true) : Speaker {
    val spoken = mutableListOf<Pair<String, Boolean>>()
    var stops = 0
    override val voices: StateFlow<List<Voice>> = MutableStateFlow(voices)
    override val isAvailable: StateFlow<Boolean> = MutableStateFlow(available)

    override fun speak(text: String, slow: Boolean) {
        spoken += text to slow
    }

    override fun stop() {
        stops++
    }

    val last: String? get() = spoken.lastOrNull()?.first
}

/**
 * Answers AI requests from [reply] (by prompt), or fails with [error].
 * Every request is recorded in [requests].
 */
class FakeAiClient(
    var reply: (String) -> String = { "Godt!" },
    var error: AiErrorKind? = null,
    var health: ServerHealth = ServerHealth(2, listOf("deepseek-v4.1-flash"), false),
    var model: String? = "deepseek-v4.1-flash",
    var streamParts: List<String>? = null,
) : AiClient {
    val requests = mutableListOf<List<ChatMessage>>()
    val reports = mutableListOf<Pair<String, String>>()
    override var lastModel: String? = null

    override suspend fun complete(messages: List<ChatMessage>, json: Boolean): AiReply {
        requests += messages
        error?.let { throw AiException(it) }
        lastModel = model
        return AiReply(reply(messages.last().content), model)
    }

    override fun stream(messages: List<ChatMessage>): Flow<String> = flow {
        requests += messages
        error?.let { throw AiException(it) }
        lastModel = model
        val parts = streamParts ?: listOf(reply(messages.last().content))
        var text = ""
        for (p in parts) {
            text += p
            emit(text)
        }
    }

    override suspend fun health(): ServerHealth {
        error?.let { throw AiException(it) }
        return health
    }

    override suspend fun report(content: String, reason: String) {
        error?.let { throw AiException(it) }
        reports += content to reason
    }

    companion object {
        const val DETAILS_JSON =
            """{"pos":"іменник","usage":"Вживається в подорожах.","forms":[["Однина","en kuffert"],["Множина","kufferter"]],""" +
                """"examples":[{"label":"Аеропорт","da":"Min kuffert er tung.","uk":"Моя валіза важка."}],"phrases":[{"da":"pakke kufferten","uk":"пакувати валізу"}]}"""

        /** Replies like a well-behaved model for every prompt the app sends. */
        fun smart() = FakeAiClient(reply = { prompt ->
            when {
                prompt.contains("словникову статтю") -> DETAILS_JSON
                prompt.contains("12 нових") -> """["en billetautomat|квитковий автомат","et gate|вихід на посадку"]"""
                prompt.contains("одне коротке речення") -> """{"da":"Jeg læser en bog.","uk":"Я читаю книгу."}"""
                prompt.contains("Переклади українською") -> "Шановні слухачі курсів"
                else -> "Hej! Jeg er din dansklærer."
            }
        })
    }
}

/** A complete app graph with real content and progress in memory. */
fun testGraph(
    ai: FakeAiClient = FakeAiClient.smart(),
    // Speakers list voices best first, like AndroidSpeaker does.
    speaker: FakeSpeaker = FakeSpeaker(listOf(Voice("Magnus (Enhanced)", "da-DK"), Voice("Sara", "da-DK"))),
    serverUrl: String = "https://danskly.test",
    clock: FixedClock = FixedClock(20_000 * FixedClock.DAY_MILLIS),
    store: InMemoryProgressStore = InMemoryProgressStore(),
): AppGraph = AppGraph(
    repository = LearningRepository(TestContent.content, store, clock),
    speaker = speaker,
    ai = ai,
    builtInServerUrl = serverUrl,
    navigator = Navigator(),
    random = kotlin.random.Random(7),
)
