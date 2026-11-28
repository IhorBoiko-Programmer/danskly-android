package app.danskly.core.exercise

import app.danskly.core.model.Word
import kotlin.random.Random

enum class QuizMode(val label: String) {
    TRANSLATE("Данська → укр"),
    REVERSE("Укр → данська"),
    LISTEN("На слух"),
    ARTICLE("en чи et"),
}

/** One quiz question about [word]. In [QuizMode.LISTEN] the prompt is spoken, not shown. */
data class QuizQuestion(val word: Word, val mode: QuizMode, val prompt: String, val options: List<String>, val answer: String)

/** Multiple-choice vocabulary quiz over a set of words (a topic or all words). */
class QuizGenerator(private val words: List<Word>, private val random: Random = Random.Default) {

    private val nouns = words.filter { it.article != null }

    init {
        require(words.size >= 2) { "A quiz needs at least two words" }
    }

    /** "en or et" is offered only when the set has enough nouns. */
    fun modes(): List<QuizMode> = QuizMode.entries.filter { it != QuizMode.ARTICLE || nouns.size >= 2 }

    /**
     * [count] questions in [mode]. Words do not repeat until every word of the set has been asked,
     * so a 50-question test on a small topic goes through the topic several times.
     */
    fun round(mode: QuizMode, count: Int): QuizRound {
        require(count > 0) { "A round needs at least one question" }
        val pool = if (mode == QuizMode.ARTICLE && nouns.size >= 2) nouns else words
        val picked = generateSequence { pool.shuffled(random) }.flatten().take(count).toList()
        return QuizRound(picked.map { question(mode, it) })
    }

    private fun question(mode: QuizMode, w: Word): QuizQuestion {
        if (mode == QuizMode.ARTICLE && w.article != null && nouns.size >= 2) {
            return QuizQuestion(w, mode, "${w.danish} (${w.ukrainian})", listOf("en", "et"), w.article.text)
        }
        val others = words.filter { it != w && it.ukrainian != w.ukrainian && it.display != w.display }.shuffled(random).take(3)
        val choices = (others + w).shuffled(random)
        return if (mode == QuizMode.REVERSE) {
            QuizQuestion(w, mode, w.ukrainian, choices.map { it.display }, w.display)
        } else {
            QuizQuestion(w, if (mode == QuizMode.ARTICLE) QuizMode.TRANSLATE else mode, w.display, choices.map { it.ukrainian }, w.ukrainian)
        }
    }

    companion object {
        /** The test lengths a learner can choose from. */
        val LENGTHS = listOf(10, 20, 50)
    }
}

/**
 * A test of a fixed number of questions. Immutable: every answer returns a new round,
 * which keeps the screen state simple.
 */
data class QuizRound(
    val questions: List<QuizQuestion>,
    val index: Int = 0,
    val chosen: String? = null,
    val correct: Int = 0,
    val mistakes: List<QuizQuestion> = emptyList(),
) {
    val size: Int get() = questions.size
    val isFinished: Boolean get() = index >= size
    val current: QuizQuestion get() = questions[index]
    val isLast: Boolean get() = index == size - 1
    val percent: Int get() = correct * 100 / size

    /** The first answer counts; later taps on the same question are ignored. */
    fun answer(option: String): QuizRound {
        if (chosen != null) return this
        val right = option == current.answer
        return copy(chosen = option, correct = correct + if (right) 1 else 0, mistakes = if (right) mistakes else mistakes + current)
    }

    fun next(): QuizRound = copy(index = index + 1, chosen = null)
}
