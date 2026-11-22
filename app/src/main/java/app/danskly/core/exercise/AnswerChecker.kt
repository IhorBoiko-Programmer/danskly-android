package app.danskly.core.exercise

import app.danskly.core.text.TextTools

/** Checks answers. Typed answers ignore case and punctuation and forgive one typo in longer words. */
object AnswerChecker {

    private const val TYPO_MIN_LENGTH = 5

    fun check(question: ChoiceQuestion, chosen: String): Verdict =
        Verdict(chosen == question.answer, question.answer)

    fun check(question: TilesQuestion, chosenTiles: List<String>): Verdict =
        Verdict(chosenTiles == question.answer, question.answerText)

    fun check(question: TypingQuestion, typed: String): Verdict {
        val answer = question.accepted.first()
        val input = TextTools.normalize(typed)
        val accepted = question.accepted.map(TextTools::normalize)
        if (input in accepted) return Verdict(true, answer)
        if (input.isNotEmpty() && input in question.alternatives.map(TextTools::normalize)) {
            val note = if (question.word?.article != null) "Не забудь артикль: $answer." else "Повна форма: $answer."
            return Verdict(true, answer, note)
        }
        if (accepted.any { it.length >= TYPO_MIN_LENGTH && TextTools.levenshtein(input, it) == 1 }) {
            return Verdict(true, answer, "Маленька помилка. Правильно: $answer.")
        }
        return Verdict(false, answer)
    }
}
