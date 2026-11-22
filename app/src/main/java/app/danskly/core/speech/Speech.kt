package app.danskly.core.speech

import app.danskly.core.model.Settings
import app.danskly.core.model.VoiceGender
import app.danskly.core.text.TextTools
import kotlinx.coroutines.flow.StateFlow

/** A text-to-speech voice installed on the device. */
data class Voice(
    val name: String,
    val languageTag: String,
    val highQuality: Boolean = false,
    val features: Set<String> = emptySet(),
)

/** Reads Danish text aloud. Implemented on Android with the system text-to-speech engine. */
interface Speaker {
    /** Danish voices on the device, best first. Empty until the engine is ready. */
    val voices: StateFlow<List<Voice>>

    /** False if the device has no working text-to-speech at all. */
    val isAvailable: StateFlow<Boolean>

    fun speak(text: String, slow: Boolean = false)

    fun stop()
}

/** Picks the voice to use and prepares text and speed. Pure logic, shared by all speakers. */
object VoiceSelector {

    private val female = Regex("\\b(sara|helle|christel|ida|naja|tanja|mette|nanna|signe|freja)\\b|google dansk", RegexOption.IGNORE_CASE)
    private val male = Regex("\\b(magnus|jeppe|mads|lars|anders|jens)\\b", RegexOption.IGNORE_CASE)
    private val quality = Regex("natural|neural|premium|enhanced|forbedret|wavenet|network", RegexOption.IGNORE_CASE)

    fun isDanish(v: Voice): Boolean = v.languageTag.lowercase().let { it == "da" || it.startsWith("da-") || it.startsWith("da_") }

    fun genderOf(v: Voice): VoiceGender? = when {
        "female" in v.features || female.containsMatchIn(v.name) -> VoiceGender.FEMALE
        "male" in v.features || male.containsMatchIn(v.name) -> VoiceGender.MALE
        else -> null
    }

    fun score(v: Voice): Int = (if (v.highQuality || quality.containsMatchIn(v.name)) 10 else 0) + (if (v.name.contains("google", true)) 3 else 0)

    /** Danish voices only, best quality first. */
    fun danishVoices(all: List<Voice>): List<Voice> = all.filter(::isDanish).sortedByDescending(::score)

    /** The learner's chosen voice, else the best voice of the chosen gender, else the best voice. */
    fun choose(voices: List<Voice>, settings: Settings): Voice? =
        voices.firstOrNull { it.name == settings.voiceName }
            ?: voices.firstOrNull { settings.voiceGender != VoiceGender.BEST && genderOf(it) == settings.voiceGender }
            ?: voices.firstOrNull()

    /** Speech rate for normal or slow (turtle) playback. */
    fun rate(settings: Settings, slow: Boolean): Float =
        if (slow) maxOf(MIN_RATE, Math.round(settings.speechRate * SLOW_FACTOR * 100) / 100f) else settings.speechRate

    /** Splits text into utterances: no highlight marks, no "Name:" prefixes, one sentence each. */
    fun utterances(text: String): List<String> = TextTools.sentences(TextTools.forSpeech(TextTools.withoutSpeakerNames(text)))

    const val MIN_RATE = 0.4f
    const val MAX_RATE = 1.6f
    private const val SLOW_FACTOR = 0.6f

    const val SAMPLE = "Hej! Velkommen. Jeg hjælper dig med at lære dansk."
    const val PREVIEW = "Hej! Hvordan går det?"
}
