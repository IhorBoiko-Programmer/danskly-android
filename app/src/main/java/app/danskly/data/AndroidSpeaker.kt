package app.danskly.data

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import app.danskly.core.model.Settings
import app.danskly.core.speech.Speaker
import app.danskly.core.speech.Voice
import app.danskly.core.speech.VoiceSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Speaks Danish with the phone's text-to-speech engine, using the voice and speed from [settings]. */
class AndroidSpeaker(
    context: Context,
    private val settings: () -> Settings,
    engineFactory: (Context, TextToSpeech.OnInitListener) -> TextToSpeech = { c, l -> TextToSpeech(c, l) },
) : Speaker {

    private val voiceList = MutableStateFlow<List<Voice>>(emptyList())
    override val voices: StateFlow<List<Voice>> = voiceList.asStateFlow()

    private val available = MutableStateFlow(true)
    override val isAvailable: StateFlow<Boolean> = available.asStateFlow()

    private var ready = false
    private var pendingStatus: Int? = null
    private lateinit var engine: TextToSpeech

    init {
        engine = engineFactory(context.applicationContext) { status -> onInit(status) }
        pendingStatus?.let(::onInit)
    }

    internal fun onInit(status: Int) {
        if (!this::engine.isInitialized) {
            // Some engines report readiness while still being constructed.
            pendingStatus = status
            return
        }
        if (status != TextToSpeech.SUCCESS) {
            available.value = false
            return
        }
        ready = true
        refreshVoices()
    }

    /** Re-reads the installed voices, e.g. after the learner downloads a new one. */
    fun refreshVoices() {
        val installed = engine.voices.orEmpty().map { v ->
            Voice(
                name = v.name,
                languageTag = v.locale.toLanguageTag(),
                highQuality = v.quality >= android.speech.tts.Voice.QUALITY_HIGH,
                features = v.features.orEmpty(),
            )
        }
        voiceList.value = VoiceSelector.danishVoices(installed)
    }

    override fun speak(text: String, slow: Boolean) {
        if (!ready) return
        val s = settings()
        val chosen = VoiceSelector.choose(voiceList.value, s)
        val native = chosen?.let { c -> engine.voices.orEmpty().firstOrNull { it.name == c.name } }
        if (native != null) engine.voice = native else engine.language = DANISH
        engine.setSpeechRate(VoiceSelector.rate(s, slow))
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, s.volume) }
        VoiceSelector.utterances(text).forEachIndexed { i, sentence ->
            engine.speak(sentence, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, "danskly-$i")
        }
    }

    override fun stop() {
        engine.stop()
    }

    fun shutdown() {
        engine.shutdown()
    }

    private companion object {
        val DANISH: Locale = Locale.forLanguageTag("da-DK")
    }
}
