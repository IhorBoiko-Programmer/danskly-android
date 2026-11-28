package app.danskly.core.exercise

import app.danskly.core.TestContent
import app.danskly.core.model.Article
import app.danskly.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizGeneratorTest {

    private val words = TestContent.content.words
    private val nouns = listOf(Word("bog", "книга", "T", Article.EN), Word("hus", "дім", "T", Article.ET))

    @Test
    fun everyModeGivesSolvableQuestions() {
        val g = QuizGenerator(words, Random(4))
        for (mode in QuizMode.entries) {
            g.round(mode, 50).questions.forEach { q ->
                assertTrue(q.answer in q.options)
                assertEquals(q.options.size, q.options.toSet().size)
            }
        }
    }

    @Test
    fun roundHasTheChosenLengthWithoutRepeats() {
        val g = QuizGenerator(words, Random(2))
        for (n in QuizGenerator.LENGTHS) {
            val round = g.round(QuizMode.TRANSLATE, n)
            assertEquals(n, round.size)
            assertEquals(n, round.questions.map { it.word }.toSet().size)
        }
    }

    @Test
    fun smallTopicsRepeatOnlyAfterEveryWordWasAsked() {
        val few = words.take(4)
        val round = QuizGenerator(few, Random(3)).round(QuizMode.TRANSLATE, 10)
        assertEquals(10, round.size)
        assertEquals(few.toSet(), round.questions.take(4).map { it.word }.toSet())
        assertEquals(few.toSet(), round.questions.drop(4).take(4).map { it.word }.toSet())
    }

    @Test
    fun reverseShowsUkrainianAndAsksDanish() {
        val q = QuizGenerator(words, Random(1)).round(QuizMode.REVERSE, 1).current
        assertEquals(q.word.ukrainian, q.prompt)
        assertEquals(q.word.display, q.answer)
        assertEquals(QuizMode.REVERSE, q.mode)
    }

    @Test
    fun articleModeNeedsNouns() {
        val verbs = words.filter { it.isVerb }.take(5)
        val g = QuizGenerator(verbs, Random(1))
        assertFalse(QuizMode.ARTICLE in g.modes())
        assertEquals(QuizMode.TRANSLATE, g.round(QuizMode.ARTICLE, 1).current.mode)
        val n = QuizGenerator(nouns, Random(1))
        assertTrue(QuizMode.ARTICLE in n.modes())
        val q = n.round(QuizMode.ARTICLE, 1).current
        assertEquals(listOf("en", "et"), q.options)
        assertEquals(q.word.article!!.text, q.answer)
        assertEquals(QuizMode.LISTEN, n.round(QuizMode.LISTEN, 1).current.mode)
    }

    @Test
    fun articleRoundsAskOnlyNouns() {
        val mixed = nouns + words.filter { it.isVerb }.take(3)
        val round = QuizGenerator(mixed, Random(5)).round(QuizMode.ARTICLE, 10)
        assertTrue(round.questions.all { it.mode == QuizMode.ARTICLE && it.word.article != null })
    }

    @Test
    fun roundCountsAnswersAndMistakes() {
        var round = QuizGenerator(words, Random(6)).round(QuizMode.TRANSLATE, 3)
        assertFalse(round.isFinished)
        round = round.answer(round.current.answer)
        assertSame(round, round.answer("again"))
        assertFalse(round.isLast)
        round = round.next()
        val missed = round.current
        round = round.answer(missed.options.first { it != missed.answer }).next()
        assertTrue(round.isLast)
        round = round.answer(round.current.answer).next()
        assertTrue(round.isFinished)
        assertEquals(2, round.correct)
        assertEquals(listOf(missed), round.mistakes)
        assertEquals(66, round.percent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun needsTwoWords() {
        QuizGenerator(words.take(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun needsAtLeastOneQuestion() {
        QuizGenerator(words).round(QuizMode.TRANSLATE, 0)
    }
}
