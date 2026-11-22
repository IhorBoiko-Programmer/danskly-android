package app.danskly.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import app.danskly.core.ai.AiErrorKind
import app.danskly.core.ai.ServerHealth
import app.danskly.core.model.VoiceGender
import app.danskly.core.navigation.Route
import app.danskly.core.speech.Voice
import app.danskly.core.speech.VoiceSelector
import app.danskly.support.FakeAiClient
import app.danskly.support.FakeSpeaker
import app.danskly.support.UiTest
import app.danskly.support.testGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScreenTest : UiTest() {

    private val voices = listOf(Voice("Magnus (Enhanced)", "da-DK"), Voice("Sara", "da-DK"), Voice("da-dk-x-abc", "da-DK"))
    private val settings get() = graph.repository.progress.value.settings

    private fun setSlider(tag: String, value: Float) {
        tag(tag).scrollIfPossible().performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        compose.waitForIdle()
    }

    @Test
    fun genderAndVoiceChoice() {
        launch(Route.Settings, testGraph(speaker = FakeSpeaker(voices)))
        click("Жіночий")
        assertEquals(VoiceGender.FEMALE, settings.voiceGender)
        assertEquals(VoiceSelector.SAMPLE, speaker.last)
        click("Чоловічий")
        assertEquals(VoiceGender.MALE, settings.voiceGender)
        click("Sara")
        assertEquals("Sara", settings.voiceName)
        assertEquals(VoiceGender.FEMALE, settings.voiceGender)
        click("da-dk-x-abc")
        assertEquals(VoiceGender.BEST, settings.voiceGender)
        assertTrue(exists("стать не вказана"))
        assertTrue(exists("чоловічий · якісний"))
        click("Найкращий")
        assertNull(settings.voiceName)
    }

    @Test
    fun speedPresetsSlidersAndTest() {
        launch(Route.Settings)
        click("Швидко")
        assertEquals(1.25f, settings.speechRate)
        assertEquals(VoiceSelector.PREVIEW, speaker.last)
        assertTrue(exists("1.25×"))
        setSlider("rate-slider", 0.7f)
        assertEquals(0.7f, settings.speechRate, 0.001f)
        setSlider("volume-slider", 0.5f)
        assertEquals(0.5f, settings.volume, 0.001f)
        assertTrue(exists("50%"))
        click("Перевірити голос")
        assertEquals(VoiceSelector.SAMPLE, speaker.last)
    }

    @Test
    fun noVoicesExplainsHowToInstall() {
        launch(Route.Settings, testGraph(speaker = FakeSpeaker(emptyList())))
        assertTrue(exists("Данських голосів на телефоні не знайдено", substring = true))
    }

    @Test
    fun aiSettingsAndConnectionCheck() {
        launch(Route.Settings)
        tag("server-url").performTextInput("https://my.server")
        tag("access-code").performTextInput("kode")
        assertEquals("https://my.server", settings.serverUrl)
        assertEquals("kode", settings.accessCode)
        click("Перевірити з’єднання")
        assertTrue(exists("✓ Працює. Ключів: 2. Модель deepseek-v4.1-flash", substring = true))
    }

    @Test
    fun connectionCheckReportsMissingKeysAndErrors() {
        val fake = FakeAiClient(health = ServerHealth(0, emptyList(), false))
        launch(Route.Settings, testGraph(ai = fake))
        click("Перевірити з’єднання")
        assertTrue(exists("ключі ще не додані", substring = true))
        fake.error = AiErrorKind.NETWORK
        click("Перевірити з’єднання")
        assertTrue(exists("✗ ${AiErrorKind.NETWORK.message}"))
        fake.error = null
        fake.health = ServerHealth(1, listOf("m"), false)
        fake.model = null
        click("Перевірити з’єднання")
        assertTrue(exists("Модель ? відповіла", substring = true))
    }

    @Test
    fun learningOptionsAndResetWithConfirmation() {
        val g = testGraph()
        g.repository.grade(g.repository.findWord("bog")!!, true)
        launch(Route.Settings, g)
        click("Автоозвучення в картках")
        assertTrue(settings.autoSpeak)
        assertTrue(exists("Увімкнено"))
        click("Очистити збережені приклади ШІ")
        click("Скинути прогрес")
        click("Скасувати")
        assertFalse(graph.repository.progress.value.cards.isEmpty())
        click("Скинути прогрес")
        click("Скинути")
        assertTrue(graph.repository.progress.value.cards.isEmpty())
    }

    @Test
    fun placeholderUsesBuiltInServer() {
        launch(Route.Settings, testGraph(serverUrl = ""))
        assertTrue(exists("https://danskly.….workers.dev"))
    }
}
