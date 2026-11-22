package app.danskly.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import app.danskly.core.ai.AiErrorKind
import app.danskly.core.navigation.Route
import app.danskly.support.FakeAiClient
import app.danskly.support.UiTest
import app.danskly.support.testGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every AI answer can be reported from inside the app (Google Play AI-content policy). */
class ReportAiTest : UiTest() {

    private fun reportButtons() = compose.onAllNodes(hasTestTag("report")).fetchSemanticsNodes().size

    @Test
    fun tutorAnswerCanBeReportedAsOffensive() {
        launch(Route.Tutor)
        click("Розмова: подорожі")
        assertEquals(1, reportButtons())
        clickTag("report")
        assertTrue(exists("Що не так із відповіддю?"))
        click("Образливий або неприйнятний вміст")
        assertTrue(exists("Дякуємо! Скаргу надіслано."))
        assertEquals(listOf("Hej! Jeg er din dansklærer." to "offensive"), ai.reports)
        assertEquals(0, reportButtons())
    }

    @Test
    fun cancelSendsNothing() {
        launch(Route.Tutor)
        click("Розмова: подорожі")
        clickTag("report")
        click("Скасувати")
        assertFalse(exists("Що не так із відповіддю?"))
        assertTrue(ai.reports.isEmpty())
        assertEquals(1, reportButtons())
    }

    @Test
    fun failedReportExplainsWhy() {
        launch(Route.Writing(0))
        tag("draft").performTextInput("Kære Anne, jeg kan desværre ikke komme til prøven.")
        click("Перевірити з ШІ")
        clickTag("report")
        ai.error = AiErrorKind.NETWORK
        click("Неправильна інформація")
        assertTrue(exists("Не вдалося надіслати: ${AiErrorKind.NETWORK.message}"))
    }

    @Test
    fun errorsCannotBeReported() {
        launch(Route.Tutor, testGraph(ai = FakeAiClient(error = AiErrorKind.UNAVAILABLE)))
        click("Вправа на переклад")
        assertEquals(0, reportButtons())
    }

    @Test
    fun hintsCannotBeReported() {
        launch(Route.Writing(0))
        click("Перевірити з ШІ")
        assertTrue(exists("Спочатку напиши текст."))
        assertEquals(0, reportButtons())
    }
}
