package app.danskly.core.exercise

import app.danskly.core.TestContent
import app.danskly.core.model.Article
import app.danskly.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizGeneratorTest {

    private val words = TestContent.content.words

    @Test
    fun everyModeGivesASolvableQuestion() {
        val g = QuizGenerator(words, Random(4))
        for (mode in QuizMode.entries) repeat(300) {
            val q = g.next(mode)
            assertTrue(q.answer in q.options)
            assertEquals(q.options.size, q.options.toSet().size)
        }
    }

    @Test
    fun reverseShowsUkrainianAndAsksDanish() {
        val q = QuizGenerator(words, Random(1)).next(QuizMode.REVERSE)
        assertEquals(q.word.ukrainian, q.prompt)
        assertEquals(q.word.display, q.answer)
        assertEquals(QuizMode.REVERSE, q.mode)
    }

    @Test
    fun articleModeNeedsNouns() {
        val verbs = words.filter { it.isVerb }.take(5)
        val g = QuizGenerator(verbs, Random(1))
        assertFalse(QuizMode.ARTICLE in g.modes())
        assertEquals(QuizMode.TRANSLATE, g.next(QuizMode.ARTICLE).mode)
        val nouns = listOf(Word("bog", "книга", "T", Article.EN), Word("hus", "дім", "T", Article.ET))
        val n = QuizGenerator(nouns, Random(1))
        assertTrue(QuizMode.ARTICLE in n.modes())
        val q = n.next(QuizMode.ARTICLE)
        assertEquals(listOf("en", "et"), q.options)
        assertEquals(q.word.article!!.text, q.answer)
        assertEquals(QuizMode.LISTEN, n.next(QuizMode.LISTEN).mode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun needsTwoWords() {
        QuizGenerator(words.take(1))
    }
}
