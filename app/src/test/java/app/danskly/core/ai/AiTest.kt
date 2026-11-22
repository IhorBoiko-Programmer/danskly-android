package app.danskly.core.ai

import app.danskly.core.json.JsonValue
import app.danskly.core.model.Article
import app.danskly.core.model.Example
import app.danskly.core.model.Word
import app.danskly.core.model.WritingTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {

    private fun delta(text: String) = "data: {\"choices\":[{\"delta\":{\"content\":${'"'}$text${'"'}}}]}\n\n"

    @Test
    fun parsesDeltasAcrossChunkBoundaries() {
        val p = SseParser()
        val whole = delta("Hej") + delta(" med") + "data: [DONE]\n\n"
        val events = whole.chunked(7).flatMap { p.feed(it) }
        assertEquals(listOf(SseEvent.Delta("Hej"), SseEvent.Delta(" med")), events)
    }

    @Test
    fun ignoresNoiseAndReportsTimeouts() {
        val p = SseParser()
        val events = p.feed(
            ": comment\nevent: x\ndata:\ndata: not json\ndata: {\"choices\":[]}\ndata: {\"choices\":[{\"delta\":{}}]}\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}\ndata: {\"choices\":5}\ndata: {\"error\":\"timeout\"}\n",
        )
        assertEquals(listOf<SseEvent>(SseEvent.Timeout), events)
        assertEquals(emptyList<SseEvent>(), p.feed("data: {\"choi"))
    }
}

class AiTextTest {

    @Test
    fun stripsThinking() {
        assertEquals("Svar", AiText.stripThinking("<think>hmm\nlong</think>\n Svar"))
        assertEquals("", AiText.stripThinking("<think>never closed"))
    }

    @Test
    fun parsesJsonInsideFencesProseAndThinking() {
        assertEquals(JsonValue.Arr(emptyList()), AiText.parseJsonLoose("[]"))
        val obj = AiText.parseJsonLoose("<think>x</think>```json\n{\"a\":1}\n```") as JsonValue.Obj
        assertEquals(JsonValue.Num(1.0), obj.fields["a"])
        val arr = AiText.parseJsonLoose("Ось: [\"a|b\"] готово") as JsonValue.Arr
        assertEquals(1, arr.items.size)
    }

    @Test
    fun badJsonIsABadResponse() {
        for (text in listOf("no json here", "{ not closed", "] [", "{\"a\": }")) {
            try {
                AiText.parseJsonLoose(text)
                throw AssertionError(text)
            } catch (e: AiException) {
                assertEquals(AiErrorKind.BAD_RESPONSE, e.kind)
            }
        }
    }
}

class AiTasksTest {

    private val bog = Word("bog", "книга", "T", Article.EN)
    private val gaa = Word("at gå", "йти", "T")

    @Test
    fun promptsMentionTheWordAndFormat() {
        assertTrue(AiTasks.wordDetailsPrompt(bog).contains("«en bog»"))
        assertTrue(AiTasks.wordDetailsPrompt(bog).contains("6 коротких речень"))
        assertTrue(AiTasks.wordDetailsPrompt(gaa).contains("9 коротких речень"))
        assertTrue(AiTasks.newWordsPrompt("Подорожі", listOf(bog)).contains("en bog"))
        assertTrue(AiTasks.examplePrompt(bog).contains("en bog"))
        assertTrue(AiTasks.checkWritingPrompt(WritingTask("t", "Skriv", "s", "tips"), "Min tekst").contains("Min tekst"))
        assertTrue(AiTasks.translatePrompt("Hej").endsWith("Hej"))
        assertEquals(4, AiTasks.chatStarters.size)
        assertTrue(AiTasks.PING_PROMPT.isNotBlank())
    }

    @Test
    fun chatPrefixesTeacherOnlyOnFirstUserMessage() {
        val history = listOf(ChatMessage(Role.USER, "Hej"), ChatMessage(Role.ASSISTANT, "Hej!"), ChatMessage(Role.USER, "Tak"))
        val sent = AiTasks.chat(history)
        assertTrue(sent[0].content.startsWith(AiTasks.TEACHER))
        assertEquals("Tak", sent[2].content)
        assertEquals(history[1], AiTasks.chat(listOf(history[1])).first())
        assertEquals("user", Role.USER.wire)
    }

    @Test
    fun parsesWordDetailsAndLimitsSizes() {
        val ex = (1..15).joinToString(",") { """{"label":"L$it","da":"Sætning $it.","uk":"Речення $it."}""" }
        val d = AiTasks.parseWordDetails("""{"pos":"іменник","usage":"Часто.","forms":[["Однина","en bog"]],"examples":[$ex],"phrases":[]}""")
        assertEquals("іменник", d.partOfSpeech)
        assertEquals(10, d.examples.size)
        assertEquals(listOf("Однина" to "en bog"), d.forms)
        expectBad { AiTasks.parseWordDetails("""{"examples":[]}""") }
        expectBad { AiTasks.parseWordDetails("[1]") }
    }

    @Test
    fun parsesNewWordsSkippingJunk() {
        val words = AiTasks.parseNewWords("""Ось: ["en pas|паспорт","at rejse|подорожувати","hurtig|швидкий","bad",42,"|x","a|b|c"]""", "Подорожі")
        assertEquals(
            listOf(
                Word("pas", "паспорт", "Подорожі", Article.EN, true),
                Word("at rejse", "подорожувати", "Подорожі", null, true),
                Word("hurtig", "швидкий", "Подорожі", null, true),
            ),
            words,
        )
        expectBad { AiTasks.parseNewWords("""{"a":1}""", "T") }
    }

    @Test
    fun parsesExampleSentence() {
        assertEquals(Example("Jeg læser.", "Я читаю."), AiTasks.parseExample("""{"da":"Jeg læser.","uk":"Я читаю."}"""))
        expectBad { AiTasks.parseExample("""{"da":"","uk":"x"}""") }
        expectBad { AiTasks.parseExample("""{"da":"x"}""") }
        expectBad { AiTasks.parseExample("""["x"]""") }
    }

    @Test
    fun errorKindsHaveMessages() {
        AiErrorKind.entries.forEach { assertTrue(AiException(it).message!!.isNotBlank()) }
        assertEquals(AiReply("a", null), AiReply("a", null))
        assertEquals(3, ServerHealth(3, listOf("m"), false).keys)
    }

    private fun expectBad(block: () -> Unit) {
        try {
            block()
        } catch (e: AiException) {
            assertEquals(AiErrorKind.BAD_RESPONSE, e.kind)
            return
        }
        throw AssertionError("Expected BAD_RESPONSE")
    }
}
