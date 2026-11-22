package app.danskly.core.exercise

/** A mistake shown on the results screen. */
data class Mistake(val question: String, val correctAnswer: String)

/** An immutable snapshot of a session, for the UI to draw. */
data class SessionState(
    val type: ExerciseType,
    val index: Int,
    val length: Int,
    val score: Int,
    val question: Question,
    val verdict: Verdict?,
    val isLast: Boolean,
    val isFinished: Boolean,
    val mistakes: List<Mistake>,
)

/**
 * A run of [length] questions of one type. Keeps the score and the mistakes,
 * and never asks the same prompt twice in a row.
 */
class ExerciseSession(
    val type: ExerciseType,
    private val generator: ExerciseGenerator,
    val length: Int = DEFAULT_LENGTH,
) {
    var index: Int = 0
        private set
    var score: Int = 0
        private set
    var question: Question = generator.next(type)
        private set
    var verdict: Verdict? = null
        private set
    private val mistakeList = mutableListOf<Mistake>()
    val mistakes: List<Mistake> get() = mistakeList

    val isAnswered: Boolean get() = verdict != null
    val isLast: Boolean get() = index + 1 >= length
    var isFinished: Boolean = false
        private set

    fun state(): SessionState = SessionState(type, index, length, score, question, verdict, isLast, isFinished, mistakes.toList())

    fun answerChoice(option: String): Verdict = record(AnswerChecker.check(question as ChoiceQuestion, option))

    fun answerTiles(tiles: List<String>): Verdict = record(AnswerChecker.check(question as TilesQuestion, tiles))

    fun answerTyped(text: String): Verdict = record(AnswerChecker.check(question as TypingQuestion, text))

    /** Moves to the next question, or finishes the session after the last one. */
    fun next() {
        check(isAnswered) { "Answer the current question first" }
        if (isLast) {
            isFinished = true
            return
        }
        index++
        verdict = null
        var candidate = generator.next(type)
        var tries = 0
        while (promptOf(candidate) == promptOf(question) && tries++ < MAX_RETRIES) candidate = generator.next(type)
        question = candidate
    }

    private fun record(v: Verdict): Verdict {
        check(!isAnswered) { "Question already answered" }
        verdict = v
        if (v.correct) score++ else mistakeList += Mistake(promptOf(question), v.correctAnswer)
        return v
    }

    companion object {
        const val DEFAULT_LENGTH = 10
        private const val MAX_RETRIES = 6

        fun promptOf(q: Question): String = when (q) {
            is ChoiceQuestion -> q.prompt
            is TilesQuestion -> q.prompt
            is TypingQuestion -> q.audio ?: q.prompt
        }
    }
}
