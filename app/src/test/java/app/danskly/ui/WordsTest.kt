package app.danskly.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.danskly.core.ai.AiErrorKind
import app.danskly.core.learning.LearningRepository
import app.danskly.core.model.Article
import app.danskly.core.model.Word
import app.danskly.core.navigation.Route
import app.danskly.support.FakeAiClient
import app.danskly.support.UiTest
import app.danskly.support.testGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicsScreenTest : UiTest() {

    @Test
    fun listsTopicsAndOpensThem() {
        launch(Route.Topics)
        click("Усі слова")
        assertEquals(Route.Topic(null), graph.navigator.current)
        clickTag("back")
        click("Подорожі")
        assertEquals(Route.Topic("Подорожі"), graph.navigator.current)
        clickTag("back")
        click("Додати своє слово")
        assertEquals(Route.AddWord(null), graph.navigator.current)
    }
}

class TopicScreenTest : UiTest() {

    @Test
    fun showsWordsAndActions() {
        launch(Route.Topic("Подорожі"))
        assertTrue(exists("вивчено 0", substring = true))
        compose.onNodeWithTag("word-list").performScrollToNode(hasTestTag("word:kuffert"))
        compose.onNodeWithTag("word:kuffert").assertExists()
        click("en kuffert")
        assertEquals(Route.WordDetails("kuffert"), graph.navigator.current)
        clickTag("back")
        click("Картки")
        assertEquals(Route.Cards("Подорожі"), graph.navigator.current)
        clickTag("back")
        click("Тест")
        assertEquals(Route.Quiz("Подорожі"), graph.navigator.current)
        clickTag("back")
        click("➕ Додати своє слово")
        assertEquals(Route.AddWord("Подорожі"), graph.navigator.current)
    }

    @Test
    fun speaksWordsAndMarksKnownOnes() {
        val g = testGraph()
        val w = g.repository.findWord("tog")!!
        repeat(3) { g.repository.grade(w, true) }
        launch(Route.Topic("Подорожі"), g)
        compose.onNodeWithTag("word-list").performScrollToNode(hasTestTag("word:tog"))
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Прослухати: et tog")).performClick()
        assertEquals("et tog", speaker.last)
    }

    @Test
    fun addsWordsWithAi() {
        launch(Route.Topic("Подорожі"))
        click("✨ Додати слова з ШІ")
        assertTrue(exists("Додано слів: 2"))
        assertNotNull(graph.repository.findWord("billetautomat"))
        click("✨ Додати слова з ШІ")
        assertTrue(exists("Нових слів не знайшлося, спробуй ще раз."))
    }

    @Test
    fun aiErrorsAreShown() {
        launch(Route.Topic("Подорожі"), testGraph(ai = FakeAiClient(error = AiErrorKind.RATE_LIMITED)))
        click("✨ Додати слова з ШІ")
        assertTrue(exists(AiErrorKind.RATE_LIMITED.message))
    }

    @Test
    fun allWordsHasNoAiButtonAndNoServerHidesIt() {
        launch(Route.Topic(null))
        assertFalse(exists("✨ Додати слова з ШІ"))
        assertTrue(exists("Усі слова"))
    }

    @Test
    fun noAiButtonWithoutServer() {
        launch(Route.Topic("Подорожі"), testGraph(serverUrl = ""))
        assertFalse(exists("✨ Додати слова з ШІ"))
    }
}

class CardsScreenTest : UiTest() {

    @Test
    fun flipGradeSpeakAndExample() {
        launch(Route.Cards("Подорожі"))
        assertTrue(exists("торкнись, щоб побачити переклад"))
        assertTrue(exists("Рівень слова: 0/4", substring = true))
        clickTag("flip-card")
        click("Показати приклад (ШІ)")
        assertTrue(exists("Jeg læser en bog."))
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Прослухати: Jeg læser en bog.")).performClick()
        assertEquals("Jeg læser en bog.", speaker.last)
        click("Знаю")
        assertEquals(1, graph.repository.progress.value.cards.size)
        clickTag("flip-card")
        click("Ще раз")
        assertEquals(2, graph.repository.todayLog().actions)
    }

    @Test
    fun autoSpeakToggle() {
        launch(Route.Cards(null))
        click("Автоозвучення")
        assertTrue(graph.repository.progress.value.settings.autoSpeak)
        assertTrue(speaker.spoken.isNotEmpty())
        click("Автоозвучення")
        assertFalse(graph.repository.progress.value.settings.autoSpeak)
    }

    @Test
    fun opensDetailsAndShowsExampleErrors() {
        launch(Route.Cards("Подорожі"), testGraph(ai = FakeAiClient(error = AiErrorKind.NETWORK)))
        clickTag("flip-card")
        click("Показати приклад (ШІ)")
        assertTrue(exists(AiErrorKind.NETWORK.message))
        click("Усі форми й приклади ›")
        assertTrue(graph.navigator.current is Route.WordDetails)
    }

    @Test
    fun noExampleButtonWithoutServer() {
        launch(Route.Cards("Подорожі"), testGraph(serverUrl = ""))
        clickTag("flip-card")
        assertFalse(exists("Показати приклад (ШІ)"))
    }
}

class AddWordScreenTest : UiTest() {

    @Test
    fun addsANounWithDetectedArticleAndDanishLetters() {
        launch(Route.AddWord(null))
        compose.onNodeWithTag("field-danish").performTextInput("en skraldespand")
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.isSelected()).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("field-ukrainian").performTextInput("смітник")
        click("Зберегти слово")
        val w = graph.repository.findWord("skraldespand")!!
        assertEquals(Article.EN, w.article)
        assertEquals(Route.Topic(LearningRepository.MY_WORDS), graph.navigator.current)
    }

    @Test
    fun verbWithDanishLettersInTopicFromPicker() {
        launch(Route.AddWord(null))
        compose.onNodeWithTag("field-danish").performTextInput("s")
        click("ø")
        compose.onNodeWithTag("field-danish").performTextInput("vsuge")
        compose.onNodeWithTag("field-ukrainian").performTextInput("пилососити")
        click("Дієслово")
        clickTag("topic-picker")
        click("Дім і побут")
        click("Зберегти слово")
        assertEquals("Дім і побут", graph.repository.findWord("at støvsuge")!!.topic)
    }

    @Test
    fun returnsToTheTopicItCameFrom() {
        launch(Route.Topic("Подорожі"))
        click("➕ Додати своє слово")
        compose.onNodeWithTag("field-danish").performTextInput("rygsæk")
        compose.onNodeWithTag("field-ukrainian").performTextInput("рюкзак")
        click("Зберегти слово")
        assertEquals(Route.Topic("Подорожі"), graph.navigator.current)
        assertEquals(2, graph.navigator.backStack.value.size)
    }

    @Test
    fun validatesAndDetectsDuplicates() {
        launch(Route.AddWord(null))
        click("Зберегти слово")
        assertTrue(exists("Заповни слово й переклад."))
        compose.onNodeWithTag("field-danish").performTextInput("bog")
        compose.onNodeWithTag("field-ukrainian").performTextInput("книга")
        click("Зберегти слово")
        assertTrue(exists("Таке слово вже є: en bog"))
        click("Відкрити «en bog» ›")
        assertEquals(Route.WordDetails("bog"), graph.navigator.current)
    }

    @Test
    fun replacingTextKeepsKindWhenNotDetectable() {
        launch(Route.AddWord("Подорожі"))
        compose.onNodeWithTag("field-danish").performTextReplacement("kuffertrem")
        click("Іменник et")
        compose.onNodeWithTag("field-ukrainian").performTextInput("ремінь для валізи")
        click("Зберегти слово")
        assertEquals(Article.ET, graph.repository.findWord("kuffertrem")!!.article)
        assertEquals("Подорожі", graph.repository.findWord("kuffertrem")!!.topic)
    }
}

class WordDetailsScreenTest : UiTest() {

    @Test
    fun verbShowsAllTensesAiDetailsAndGrammarExamples() {
        launch(Route.WordDetails("at gå"))
        assertTrue(exists("Усі часи й форми"))
        assertTrue(exists("er gået"))
        assertTrue(exists("Завершена дія з точним часом (i går, sidste år)."))
        assertTrue(exists("Вживається в подорожах."))
        assertTrue(exists("Приклади з граматики"))
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Прослухати: er gået")).performClick()
        assertEquals("er gået", speaker.last)
        val before = ai.requests.size
        click("Інші приклади")
        assertEquals(before + 1, ai.requests.size)
    }

    @Test
    fun aiFormsAreUsedWhenNoTableExists() {
        launch(Route.WordDetails("kuffert"))
        assertTrue(exists("kufferter"))
        assertTrue(exists("Форми згенерував ШІ, вони можуть містити помилки."))
        assertTrue(exists("pakke kufferten"))
    }

    @Test
    fun nounAndAdjectiveTables() {
        launch(Route.WordDetails("barn"))
        assertTrue(exists("børnene"))
        clickTag("back")
        graph.navigator.open(Route.WordDetails("god"))
        compose.waitForIdle()
        assertTrue(exists("bedst"))
    }

    @Test
    fun errorsAndRetry() {
        val fake = FakeAiClient(error = AiErrorKind.UNAVAILABLE)
        launch(Route.WordDetails("måske"), testGraph(ai = fake))
        assertTrue(exists(AiErrorKind.UNAVAILABLE.message))
        fake.error = null
        fake.reply = { FakeAiClient.DETAILS_JSON.replace("\"phrases\":[{\"da\":\"pakke kufferten\",\"uk\":\"пакувати валізу\"}]", "\"phrases\":[]").replace("\"usage\":\"Вживається в подорожах.\"", "\"usage\":\"\"") }
        click("Спробувати ще раз")
        assertTrue(exists("Min kuffert er tung."))
        assertFalse(exists("Сталі вирази"))
        assertFalse(exists("Коли вживається"))
    }

    @Test
    fun withoutServerExplainsWhereExamplesComeFrom() {
        launch(Route.WordDetails("måske"), testGraph(serverUrl = ""))
        assertTrue(exists("Приклади від ШІ з’являться, коли в Налаштуваннях буде вказано сервер ШІ."))
    }

    @Test
    fun customWordsCanBeDeleted() {
        val g = testGraph()
        g.repository.addCustomWord(Word("ord-x", "слово", LearningRepository.MY_WORDS))
        launch(Route.WordDetails("ord-x"), g)
        click("Видалити це слово")
        assertNull(graph.repository.findWord("ord-x"))
        assertEquals(Route.Home, graph.navigator.current)
    }

    @Test
    fun missingWordShowsNothing() {
        launch(Route.WordDetails("findes-ikke"))
        compose.onNodeWithTag("title").assertExists()
    }

    @Test
    fun loadingStateIsShownWhileWaiting() {
        val slow = object : app.danskly.core.ai.AiClient by FakeAiClient.smart() {
            override suspend fun complete(messages: List<app.danskly.core.ai.ChatMessage>, json: Boolean): app.danskly.core.ai.AiReply {
                kotlinx.coroutines.awaitCancellation()
            }
        }
        val g = testGraph()
        launch(Route.WordDetails("måske"), app.danskly.AppGraph(g.repository, g.speaker, slow, "https://x", g.navigator, g.random))
        assertTrue(exists("Готую пояснення й приклади…"))
    }
}
