package app.danskly.core.exercise

import app.danskly.core.model.Word

/** The exercise types offered on the "Exercises" screen. */
enum class ExerciseType(val title: String, val subtitle: String, val icon: String, val needsSpeech: Boolean = false) {
    MIX("Мікс", "Усього потроху", "🎲"),
    GAP("Встав слово", "Речення з пропуском", "🧩"),
    BUILD("Збери речення", "Переклад з української з плиток", "🧱"),
    WRITE("Напиши данською", "Переклад слова з клавіатури", "⌨️"),
    VERB("Форми дієслів", "Теперішній, минулий, перфект", "🏃"),
    NOUN("Рід і множина", "en чи et, множина іменників", "🏠"),
    DICTATION("Диктант", "Послухай і запиши", "🎧", needsSpeech = true),
}

/** One exercise question. [speak] is the Danish text read aloud after answering. */
sealed interface Question {
    val tag: String
    val speak: String
    /** Shown under the answer: full forms or the sentence translation. */
    val explanation: String?
}

/** Pick one of several options. */
data class ChoiceQuestion(
    override val tag: String,
    val prompt: String,
    val hint: String?,
    val options: List<String>,
    val answer: String,
    override val speak: String,
    override val explanation: String? = null,
) : Question

/** Build a Danish sentence from shuffled word tiles. */
data class TilesQuestion(
    override val tag: String,
    val prompt: String,
    val tiles: List<String>,
    val answer: List<String>,
    val ending: String,
    override val speak: String,
) : Question {
    override val explanation: String get() = prompt
    val answerText: String get() = answer.joinToString(" ") + ending
}

/** Type the answer. If [audio] is set, the prompt is spoken instead of shown (dictation). */
data class TypingQuestion(
    override val tag: String,
    val prompt: String,
    val hint: String?,
    val accepted: List<String>,
    val alternatives: List<String> = emptyList(),
    override val speak: String,
    val audio: String? = null,
    val word: Word? = null,
    override val explanation: String? = null,
) : Question

/** Result of checking one answer. [note] explains a forgiven mistake. */
data class Verdict(val correct: Boolean, val correctAnswer: String, val note: String? = null)
