package app.danskly.core.model

/** Spaced-repetition state of one word: its box (0–4) and when it is due again. */
data class CardState(val box: Int, val dueAt: Long)

/** The four things the "Today" screen asks the learner to do every day. */
enum class DailyTask { REVIEW, RULE, EXERCISE, READING }

/** What has been done on one calendar day (date as ISO "2026-11-22"). */
data class DayLog(val date: String, val actions: Int = 0, val done: Set<DailyTask> = emptySet())

/** The new words chosen for a given day, so the plan stays the same all day. */
data class DailyPlan(val date: String, val newWords: List<String>)

/** Whether a voice sounds female or male, as far as its name or features tell. Shown next to the voice name. */
enum class VoiceGender { FEMALE, MALE }

data class Settings(
    /** The chosen voice; null means "the best voice on the phone". */
    val voiceName: String? = null,
    val speechRate: Float = 0.95f,
    val volume: Float = 1f,
    val autoSpeak: Boolean = false,
    val accessCode: String = "",
    /** Overrides the AI server address built into the app (empty = use the built-in one). */
    val serverUrl: String = "",
)

data class LabeledExample(val label: String, val danish: String, val ukrainian: String)

/** An AI-written dictionary entry for a word, cached after the first request. */
data class WordDetails(
    val partOfSpeech: String,
    val usage: String,
    val forms: List<Pair<String, String>>,
    val examples: List<LabeledExample>,
    val phrases: List<Example>,
)

/** Everything the learner has done. Saved on the device after every change. */
data class Progress(
    val cards: Map<String, CardState> = emptyMap(),
    val streak: Int = 0,
    val lastActiveDate: String? = null,
    val day: DayLog? = null,
    val plan: DailyPlan? = null,
    val quizCorrect: Int = 0,
    val quizTotal: Int = 0,
    /** Best result per text index, for reading and for listening separately. */
    val readingScores: Map<Int, Int> = emptyMap(),
    val listeningScores: Map<Int, Int> = emptyMap(),
    val customWords: List<Word> = emptyList(),
    val wordDetails: Map<String, WordDetails> = emptyMap(),
    val exampleSentences: Map<String, Example> = emptyMap(),
    val settings: Settings = Settings(),
) {
    /** A word counts as learned once it reaches box 3. */
    fun isKnown(word: Word): Boolean = (cards[word.danish]?.box ?: 0) >= KNOWN_BOX

    fun boxOf(word: Word): Int = cards[word.danish]?.box ?: 0

    companion object {
        const val KNOWN_BOX = 3
    }
}
