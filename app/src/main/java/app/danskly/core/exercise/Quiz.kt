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

    fun next(mode: QuizMode): QuizQuestion {
        if (mode == QuizMode.ARTICLE && nouns.size >= 2) {
            val w = nouns.random(random)
            return QuizQuestion(w, mode, "${w.danish} (${w.ukrainian})", listOf("en", "et"), w.article!!.text)
        }
        val w = words.random(random)
        val others = words.filter { it != w && it.ukrainian != w.ukrainian && it.display != w.display }.shuffled(random).take(3)
        val choices = (others + w).shuffled(random)
        return if (mode == QuizMode.REVERSE) {
            QuizQuestion(w, mode, w.ukrainian, choices.map { it.display }, w.display)
        } else {
            QuizQuestion(w, if (mode == QuizMode.ARTICLE) QuizMode.TRANSLATE else mode, w.display, choices.map { it.ukrainian }, w.ukrainian)
        }
    }
}
