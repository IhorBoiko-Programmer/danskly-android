package app.danskly.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.danskly.AppGraph
import app.danskly.core.ai.AiErrorKind
import app.danskly.core.exercise.ExerciseType
import app.danskly.core.navigation.Route
import app.danskly.core.speech.Voice
import app.danskly.core.speech.VoiceSelector
import app.danskly.support.FakeAiClient
import app.danskly.support.FakeSpeaker
import app.danskly.support.UiTest
import app.danskly.support.testGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingScreensTest : UiTest() {

    private fun answerAll(correct: Boolean) {
        val text = graph.repository.content.reading[(graph.navigator.current as Route.Reading).index]
        text.questions.forEach { q ->
            val option = if (correct) q.options[0] else q.options[1]
            clickTag("option:$option")
        }
    }

    @Test
    fun listOpensTexts() {
        launch(Route.ReadingList)
        click("Лист від орендодавця")
        assertEquals(Route.Reading(1), graph.navigator.current)
    }

    @Test
    fun readTranslateAnswerAndScore() {
        launch(Route.Reading(1))
        assertTrue(exists("vandhanen", substring = true))
        click("Показати переклад")
        assertTrue(exists("Олено", substring = true))
        click("Показати данською")
        text("Перевірити відповіді").assertIsNotEnabled()
        answerAll(correct = true)
        click("Перевірити відповіді")
        assertTrue(exists("Результат: 3 з 3"))
        assertTrue(exists("Усе правильно!"))
        assertEquals(3, graph.repository.progress.value.readingScores[1])
        click("Ще раз")
        text("Перевірити відповіді").assertIsNotEnabled()
    }

    @Test
    fun listeningHidesTextAndReadsWithoutSpeakerNames() {
        launch(Route.Reading(3))
        click("🎧 Слухати")
        assertTrue(speaker.last!!.startsWith("Lægehuset, det er Mette."))
        assertFalse(speaker.last!!.contains("Sekretær:"))
        assertTrue(compose.onAllNodes(hasTestTag("reading-text")).fetchSemanticsNodes().isEmpty())
        answerAll(correct = false)
        click("Перевірити відповіді")
        assertTrue(exists("Результат: 0 з 3"))
        assertTrue(exists("Текст відкрито нижче."))
        tag("reading-text").assertExists()
        click("📖 Читати")
        click("Наступний текст")
        assertEquals(Route.Reading(4), graph.navigator.current)
    }

    @Test
    fun lastTextWrapsToTheFirst() {
        launch(Route.Reading(5))
        answerAll(correct = true)
        click("Перевірити відповіді")
        click("Наступний текст")
        assertEquals(Route.Reading(0), graph.navigator.current)
    }

    @Test
    fun scoresAppearInTheList() {
        val g = testGraph()
        g.repository.saveReadingScore(0, 2)
        launch(Route.ReadingList, g)
        assertTrue(exists("Оголошення · ✓ 2/3"))
    }
}

class GrammarScreensTest : UiTest() {

    @Test
    fun sectionsAndRules() {
        launch(Route.Grammar)
        click("Дієслова")
        assertEquals(Route.GrammarSection(2), graph.navigator.current)
        assertTrue(exists("Теперішній час"))
        compose.onAllNodes(hasContentDescription("Прослухати:", substring = true))[0].performClick()
        assertTrue(speaker.spoken.isNotEmpty())
        // Highlight marks are removed before speaking (the real speaker speaks these utterances).
        assertFalse(VoiceSelector.utterances(speaker.last!!).any { it.contains("*") })
    }
}

class PronunciationScreenTest : UiTest() {

    @Test
    fun lettersAndSoundsSpeak() {
        launch(Route.Pronunciation)
        assertTrue(exists("Голос: Magnus (Enhanced)", substring = true))
        clickTag("letter:Ø")
        assertEquals("Ø. øl", speaker.last)
        compose.onAllNodes(hasContentDescription("Повільно: hund"))[0].performScrollTo().performClick()
        assertEquals("hund" to true, speaker.spoken.last())
    }

    @Test
    fun explainsMissingVoiceOrSpeech() {
        launch(Route.Pronunciation, testGraph(speaker = FakeSpeaker(emptyList())))
        assertTrue(exists("Данського голосу не знайдено", substring = true))
    }

    @Test
    fun explainsWhenSpeechIsUnavailable() {
        launch(Route.Pronunciation, testGraph(speaker = FakeSpeaker(emptyList(), available = false)))
        assertTrue(exists("не працює синтез мовлення", substring = true))
    }
}

class WritingScreensTest : UiTest() {

    @Test
    fun listOpensTasks() {
        launch(Route.WritingList)
        click("Скарга")
        assertEquals(Route.Writing(3), graph.navigator.current)
    }

    @Test
    fun sampleTranslationPhrasesAndAiCheck() {
        launch(Route.Writing(0))
        click("Приклад відповіді")
        tag("sample").assertExists()
        click("Переклад (ШІ)")
        assertTrue(exists("Шановні слухачі курсів"))
        click("Сховати приклад відповіді")
        assertTrue(exists("Корисні фрази"))
        click("Перевірити з ШІ")
        assertTrue(exists("Спочатку напиши текст."))
        tag("draft").performTextInput("Kære Anne, jeg kan desværre ikke komme til prøven.")
        click("Перевірити з ШІ")
        assertTrue(exists("Hej! Jeg er din dansklærer."))
    }

    @Test
    fun aiErrorsAreShown() {
        launch(Route.Writing(0), testGraph(ai = FakeAiClient(error = AiErrorKind.ACCESS_CODE)))
        click("Приклад відповіді")
        click("Переклад (ШІ)")
        tag("translation").assertExists()
        tag("draft").performTextInput("Kære Anne, jeg kan desværre ikke komme til prøven.")
        click("Перевірити з ШІ")
        assertTrue(exists(AiErrorKind.ACCESS_CODE.message))
    }

    @Test
    fun withoutServerTheCheckIsExplained() {
        launch(Route.Writing(1), testGraph(serverUrl = ""))
        click("Приклад відповіді")
        assertFalse(exists("Переклад (ШІ)"))
        assertTrue(exists("Перевірка тексту запрацює", substring = true))
    }

    @Test
    fun unknownTaskIconsFallBack() {
        launch(Route.WritingList)
        assertTrue(exists("Пропозиція"))
    }
}

class TutorScreenTest : UiTest() {

    @Test
    fun startersAndMessagesStream() {
        val g = testGraph(ai = FakeAiClient.smart().apply { streamParts = listOf("Hej! ", "Hvordan ", "går det?") })
        launch(Route.Tutor, g)
        click("Розмова: подорожі")
        assertTrue(exists("Hej! Hvordan går det?"))
        assertTrue(ai.requests.last().first().content.startsWith("Ти — доброзичливий"))
        tag("chat-input").performTextInput("Jeg vil rejse til Aarhus.")
        click("Надіслати")
        tag("message:3").assertExists()
        click("Надіслати")
        assertEquals(4, compose.onAllNodes(tagStartsWith("message:")).fetchSemanticsNodes().size)
    }

    @Test
    fun errorsAppearAsAMessage() {
        launch(Route.Tutor, testGraph(ai = FakeAiClient(error = AiErrorKind.UNAVAILABLE)))
        click("Вправа на переклад")
        assertTrue(exists(AiErrorKind.UNAVAILABLE.message))
    }

    @Test
    fun unconfiguredServerExplains() {
        launch(Route.Tutor, testGraph(serverUrl = ""))
        assertTrue(exists("ШІ-тренер запрацює", substring = true))
    }

    @Test
    fun nonAiErrorsAreShownToo() {
        val broken = object : app.danskly.core.ai.AiClient by FakeAiClient() {
            override fun stream(messages: List<app.danskly.core.ai.ChatMessage>) = kotlinx.coroutines.flow.flow<String> { throw IllegalStateException("boom") }
        }
        val g = testGraph()
        launch(Route.Tutor, AppGraph(g.repository, g.speaker, broken, "https://x", g.navigator, g.random))
        click("Пояснити правило")
        assertTrue(exists("boom"))
    }
}

class TitlesTest : UiTest() {

    @Test
    fun everyRouteHasATitle() {
        launch()
        val routes = listOf(
            Route.Home to "", Route.Today to "Сьогодні", Route.Review to "Повторення", Route.Topics to "Слова",
            Route.Topic("Подорожі") to "Подорожі", Route.Topic(null) to "Усі слова", Route.Cards("Подорожі") to "Подорожі",
            Route.Cards(null) to "Усі слова", Route.WordDetails("bog") to "en bog", Route.WordDetails("xyz") to "xyz",
            Route.AddWord(null) to "Нове слово", Route.Quiz(null) to "Тест", Route.Exercises to "Завдання",
            Route.Exercise(ExerciseType.GAP) to "Встав слово", Route.ReadingList to "Читання й слухання", Route.Reading(0) to "Оголошення",
            Route.Grammar to "Граматика", Route.GrammarSection(0) to "Іменники", Route.Rule(0, 0) to "Правило",
            Route.Pronunciation to "Вимова", Route.WritingList to "Письмо", Route.Writing(3) to "Скарга",
            Route.Tutor to "ШІ-тренер", Route.Settings to "Налаштування",
        )
        routes.forEach { (route, title) -> assertEquals(route.toString(), title, titleOf(route, graph)) }
    }

    @Test
    fun voicesListedForGraphs() {
        assertTrue(testGraph().speaker.voices.value.contains(Voice("Sara", "da-DK")))
    }
}
