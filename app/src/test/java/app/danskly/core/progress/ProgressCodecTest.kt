package app.danskly.core.progress

import app.danskly.core.json.Json
import app.danskly.core.json.obj
import app.danskly.core.model.Article
import app.danskly.core.model.CardState
import app.danskly.core.model.DailyPlan
import app.danskly.core.model.DailyTask
import app.danskly.core.model.DayLog
import app.danskly.core.model.Example
import app.danskly.core.model.LabeledExample
import app.danskly.core.model.Progress
import app.danskly.core.model.Settings
import app.danskly.core.model.Word
import app.danskly.core.model.WordDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressCodecTest {

    private val full = Progress(
        cards = mapOf("bog" to CardState(2, 123456789L)),
        streak = 4,
        lastActiveDate = "2026-11-22",
        day = DayLog("2026-11-22", 7, setOf(DailyTask.REVIEW, DailyTask.READING)),
        plan = DailyPlan("2026-11-22", listOf("bog", "hus")),
        quizCorrect = 3,
        quizTotal = 5,
        readingScores = mapOf(1 to 3, 4 to 0),
        listeningScores = mapOf(2 to 1),
        customWords = listOf(Word("skraldespand", "смітник", "Мої слова", Article.EN, true), Word("at støvsuge", "пилососити", "Мої слова", null, true)),
        wordDetails = mapOf("bog" to WordDetails("іменник", "часто", listOf("Множина" to "bøger"), listOf(LabeledExample("Дім", "Jeg læser.", "Я читаю.")), listOf(Example("en god bog", "добра книга")))),
        exampleSentences = mapOf("hus" to Example("Huset er stort.", "Будинок великий.")),
        settings = Settings("Sara", 0.7f, 0.5f, true, "kode", "https://danskly.example"),
    )

    @Test
    fun roundTripsEverything() {
        assertEquals(full, ProgressCodec.decode(ProgressCodec.encode(full)))
        assertEquals(Progress(), ProgressCodec.decode(ProgressCodec.encode(Progress())))
    }

    @Test
    fun damagedOrMissingDataGivesFreshProgress() {
        assertEquals(Progress(), ProgressCodec.decode(null))
        assertEquals(Progress(), ProgressCodec.decode(" "))
        assertEquals(Progress(), ProgressCodec.decode("{broken"))
        assertEquals(Progress(), ProgressCodec.decode("[1]"))
        assertEquals(Progress(), ProgressCodec.decode("{}"))
        assertEquals(Progress(), ProgressCodec.decode("""{"day":{"actions":1}}"""))
    }

    @Test
    fun toleratesOddValues() {
        val p = ProgressCodec.decode(
            """{"day":{"date":"d","done":["REVIEW","NOPE"]},"reading":{"x":1,"2":"y","3":2},"settings":{"gender":"ALIEN"}}""",
        )
        assertEquals(setOf(DailyTask.REVIEW), p.day!!.done)
        assertEquals(mapOf(2 to 0, 3 to 2), p.readingScores)
        assertNull(p.settings.voiceName)
        assertEquals(Settings().speechRate, p.settings.speechRate)
    }

    @Test
    fun detailsSkipIncompleteEntries() {
        val d = ProgressCodec.decodeDetails(
            Json.parse(
                """{"forms":[["a","b"],["c",""],["only"],"x"],"examples":[{"da":"x","uk":"y"},{"da":"","uk":"y"},{"da":"x"},5],"phrases":[{"da":"p","uk":"q"},{"da":" ","uk":"q"},{"uk":"q"},7]}""",
            ).obj(),
        )
        assertEquals(listOf("a" to "b"), d.forms)
        assertEquals(listOf(LabeledExample("", "x", "y")), d.examples)
        assertEquals(listOf(Example("p", "q")), d.phrases)
        assertEquals("", d.partOfSpeech)
        assertEquals("", d.usage)
    }

    @Test
    fun inMemoryStoreKeepsText() {
        val s = InMemoryProgressStore("a")
        assertEquals("a", s.load())
        s.save("b")
        assertEquals("b", s.load())
    }
}
