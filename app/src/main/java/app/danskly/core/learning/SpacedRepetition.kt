package app.danskly.core.learning

import app.danskly.core.model.CardState
import app.danskly.core.time.FixedClock

/**
 * Leitner-style spaced repetition. A correct answer moves a word one box up and schedules it
 * further in the future; a wrong answer sends it back to box 0, due immediately.
 */
class SpacedRepetition(private val intervalsDays: List<Int> = DEFAULT_INTERVALS) {

    init {
        require(intervalsDays.isNotEmpty()) { "At least one interval is needed" }
    }

    val maxBox: Int get() = intervalsDays.lastIndex

    fun grade(current: CardState?, correct: Boolean, nowMillis: Long): CardState {
        val box = if (correct) minOf(maxBox, (current?.box ?: 0) + 1) else 0
        return CardState(box, nowMillis + intervalsDays[box] * FixedClock.DAY_MILLIS)
    }

    fun isDue(state: CardState, nowMillis: Long): Boolean = state.dueAt <= nowMillis

    companion object {
        /** Days until the next review for boxes 0..4. */
        val DEFAULT_INTERVALS = listOf(0, 1, 3, 7, 21)
    }
}
