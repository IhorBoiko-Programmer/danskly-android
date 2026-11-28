package app.danskly.data

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.test.core.app.ApplicationProvider
import app.danskly.AppGraph
import app.danskly.BuildConfig
import app.danskly.core.content.ContentUpdate
import app.danskly.core.model.Settings
import app.danskly.support.FakeSpeaker
import app.danskly.support.testGraph
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A text-to-speech engine that records calls instead of speaking. */
class RecordingEngine(context: Context, listener: OnInitListener, private val installed: Set<Voice>) : TextToSpeech(context, listener) {
    val spoken = mutableListOf<Triple<String, Int, Float>>()
    var voiceSet: Voice? = null
    var languageSet: Locale? = null
    var rate = 1f
    var stopped = false
    var shut = false

    override fun getVoices(): Set<Voice> = installed
    override fun setVoice(voice: Voice): Int {
        voiceSet = voice
        return SUCCESS
    }
    override fun setLanguage(loc: Locale): Int {
        languageSet = loc
        return LANG_AVAILABLE
    }
    override fun setSpeechRate(speechRate: Float): Int {
        rate = speechRate
        return SUCCESS
    }
    override fun speak(text: CharSequence, queueMode: Int, params: Bundle?, utteranceId: String?): Int {
        spoken += Triple(text.toString(), queueMode, params?.getFloat(Engine.KEY_PARAM_VOLUME) ?: -1f)
        return SUCCESS
    }
    override fun stop(): Int {
        stopped = true
        return SUCCESS
    }
    override fun shutdown() {
        shut = true
    }
}

@RunWith(RobolectricTestRunner::class)
class AndroidSpeakerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sara = Voice("Sara", Locale.forLanguageTag("da-DK"), Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, setOf("female"))
    private val premium = Voice("da-dk-x-premium", Locale.forLanguageTag("da-DK"), Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, true, null)
    private val english = Voice("Alex", Locale.US, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, emptySet())
    private var settings = Settings()
    private lateinit var engine: RecordingEngine

    private fun speaker(voices: Set<Voice> = setOf(sara, premium, english), initNow: Int? = null): AndroidSpeaker =
        AndroidSpeaker(context, { settings }) { c, l ->
            RecordingEngine(c, l, voices).also {
                engine = it
                initNow?.let(l::onInit)
            }
        }

    @Test
    fun listsDanishVoicesBestFirstAfterInit() {
        val s = speaker()
        assertTrue(s.voices.value.isEmpty())
        s.onInit(TextToSpeech.SUCCESS)
        assertEquals(listOf("da-dk-x-premium", "Sara"), s.voices.value.map { it.name })
        assertTrue(s.voices.value.first().highQuality)
        assertTrue(s.isAvailable.value)
    }

    @Test
    fun failedEngineIsUnavailableAndSilent() {
        val s = speaker()
        s.onInit(TextToSpeech.ERROR)
        assertFalse(s.isAvailable.value)
        s.speak("Hej")
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun handlesReadinessReportedDuringConstruction() {
        val s = speaker(initNow = TextToSpeech.SUCCESS)
        assertEquals(2, s.voices.value.size)
    }

    @Test
    fun speaksSentencesWithChosenVoiceRateAndVolume() {
        settings = Settings(voiceName = "Sara", speechRate = 1.1f, volume = 0.5f)
        val s = speaker()
        s.onInit(TextToSpeech.SUCCESS)
        s.speak("Sekretær: Hej! Kan I komme kl. 14.30?")
        assertEquals("Sara", engine.voiceSet!!.name)
        assertEquals(1.1f, engine.rate)
        assertEquals(
            listOf(Triple("Hej!", TextToSpeech.QUEUE_FLUSH, 0.5f), Triple("Kan I komme kl. 14.30?", TextToSpeech.QUEUE_ADD, 0.5f)),
            engine.spoken,
        )
        s.speak("Hej", slow = true)
        assertEquals(0.66f, engine.rate, 0.001f)
    }

    @Test
    fun fallsBackToDanishLanguageWithoutVoices() {
        val s = speaker(voices = setOf(english))
        s.onInit(TextToSpeech.SUCCESS)
        s.speak("Hej")
        assertNull(engine.voiceSet)
        assertEquals("da-DK", engine.languageSet!!.toLanguageTag())
    }

    @Test
    fun stopsAndShutsDown() {
        val s = speaker()
        s.stop()
        s.shutdown()
        assertTrue(engine.stopped && engine.shut)
    }

    @Test
    fun defaultFactoryCreatesARealEngine() {
        assertNotNull(AndroidSpeaker(context, { Settings() }))
    }
}

@RunWith(RobolectricTestRunner::class)
class AndroidStorageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun loadsBundledContentFromAssets() {
        val content = AssetContent.load(context)
        assertTrue(content.words.size >= 1000)
        assertEquals(6, content.reading.size)
    }

    @Test
    fun keepsProgressAndAStableSessionId() {
        val store = SharedPrefsProgressStore(context)
        assertNull(store.load())
        store.save("{\"streak\":2}")
        assertEquals("{\"streak\":2}", SharedPrefsProgressStore(context).load())
        val id = store.sessionId()
        assertEquals(id, SharedPrefsProgressStore(context).sessionId())
    }
}

@RunWith(RobolectricTestRunner::class)
class AppGraphTest {

    @Test
    fun serverAddressFromSettingsWinsOverBuiltIn() {
        val g = testGraph(serverUrl = "https://built.in")
        assertEquals("https://built.in", g.serverUrl)
        assertTrue(g.isAiConfigured)
        g.repository.updateSettings { it.copy(serverUrl = "https://mine") }
        assertEquals("https://mine", g.serverUrl)
        val none = testGraph(serverUrl = "")
        assertFalse(none.isAiConfigured)
    }

    @Test
    fun exercisesHideDictationWithoutSpeech() {
        val g = testGraph(speaker = FakeSpeaker(available = false))
        assertFalse(g.exerciseGenerator().availableTypes().any { it.needsSpeech })
    }

    @Test
    fun createsTheRealGraph() {
        val g = AppGraph.create(ApplicationProvider.getApplicationContext())
        assertTrue(g.repository.allWords.size >= 1000)
        assertTrue(g.speaker is AndroidSpeaker)
        assertTrue(g.ai is HttpAiClient)
        assertEquals(g.builtInServerUrl, g.serverUrl)
        assertNull(g.updates.contentVersion)
        assertEquals(BuildConfig.VERSION_NAME, g.updates.appVersion)
        // Tests have no server address built in, so the check fails quietly.
        assertTrue(runBlocking { g.updates.check() } is ContentUpdate.Failed)
    }
}
