package app.danskly.core.speech

import app.danskly.core.exercise.ExerciseType
import app.danskly.core.model.Settings
import app.danskly.core.model.VoiceGender
import app.danskly.core.navigation.Navigator
import app.danskly.core.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSelectorTest {

    private val sara = Voice("Sara", "da-DK")
    private val magnus = Voice("Magnus (Enhanced)", "da-DK")
    private val google = Voice("Google dansk", "da_DK")
    private val code = Voice("da-dk-x-nmm-local", "da-DK", highQuality = true, features = setOf("male"))
    private val english = Voice("Alex", "en-US")

    @Test
    fun keepsOnlyDanishVoicesBestFirst() {
        assertEquals(listOf(magnus, code, google, sara), VoiceSelector.danishVoices(listOf(sara, english, google, magnus, code)))
        assertTrue(VoiceSelector.isDanish(Voice("x", "da")))
        assertFalse(VoiceSelector.isDanish(english))
    }

    @Test
    fun detectsGender() {
        assertEquals(VoiceGender.FEMALE, VoiceSelector.genderOf(sara))
        assertEquals(VoiceGender.FEMALE, VoiceSelector.genderOf(google))
        assertEquals(VoiceGender.MALE, VoiceSelector.genderOf(magnus))
        assertEquals(VoiceGender.MALE, VoiceSelector.genderOf(code))
        assertEquals(VoiceGender.FEMALE, VoiceSelector.genderOf(Voice("x", "da", features = setOf("female"))))
        assertNull(VoiceSelector.genderOf(Voice("da-dk-x-abc", "da-DK")))
    }

    @Test
    fun choosesNamedThenGenderThenBest() {
        val voices = VoiceSelector.danishVoices(listOf(sara, magnus, google))
        assertEquals(sara, VoiceSelector.choose(voices, Settings(voiceName = "Sara")))
        assertEquals(google, VoiceSelector.choose(voices, Settings(voiceGender = VoiceGender.FEMALE)))
        assertEquals(magnus, VoiceSelector.choose(voices, Settings(voiceGender = VoiceGender.MALE)))
        assertEquals(magnus, VoiceSelector.choose(voices, Settings()))
        assertEquals(magnus, VoiceSelector.choose(voices, Settings(voiceName = "Gone")))
        assertNull(VoiceSelector.choose(emptyList(), Settings()))
    }

    @Test
    fun slowRateHasAFloor() {
        assertEquals(0.95f, VoiceSelector.rate(Settings(), slow = false))
        assertEquals(0.57f, VoiceSelector.rate(Settings(), slow = true))
        assertEquals(VoiceSelector.MIN_RATE, VoiceSelector.rate(Settings(speechRate = 0.4f), slow = true))
        assertTrue(VoiceSelector.MAX_RATE > 1f)
    }

    @Test
    fun utterancesAreCleanSentences() {
        assertEquals(listOf("Lægehuset, det er Mette.", "Kan I komme kl. 14.30?"), VoiceSelector.utterances("Sekretær: Lægehuset, det er Mette.\nAhmad: Kan I komme kl. 14.30?"))
        assertEquals(listOf("at bo, jeg bor"), VoiceSelector.utterances("at bo → jeg bo*r*"))
        assertTrue(VoiceSelector.SAMPLE.isNotBlank() && VoiceSelector.PREVIEW.isNotBlank())
    }
}

class NavigatorTest {

    @Test
    fun opensReplacesAndGoesBack() {
        val n = Navigator()
        assertEquals(Route.Home, n.current)
        assertFalse(n.canGoBack)
        assertFalse(n.back())
        n.open(Route.Topics)
        n.open(Route.Topic("Подорожі"))
        assertEquals(3, n.backStack.value.size)
        n.replace(Route.Exercise(ExerciseType.GAP))
        assertEquals(Route.Exercise(ExerciseType.GAP), n.current)
        assertTrue(n.back())
        assertEquals(Route.Topics, n.current)
        assertTrue(n.canGoBack)
        assertEquals(Route.Today, Navigator(Route.Today).current)
    }
}
