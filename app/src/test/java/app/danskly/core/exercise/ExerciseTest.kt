package app.danskly.core.exercise

import app.danskly.core.TestContent
import app.danskly.core.model.Article
import app.danskly.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExerciseGeneratorTest {

    private val content = TestContent.content
    private fun generator(seed: Int = 1, studied: (Word) -> Boolean = { false }, speech: Boolean = true) =
        ExerciseGenerator(content, { content.words }, studied, Random(seed), speech)

    @Test
    fun poolsAreBigEnough() {
        val g = generator()
        assertTrue(g.gapCount >= 60)
        assertTrue(g.sentenceCount >= 60)
    }

    /** Thousands of random questions: each one must be solvable with its own answer. */
    @Test
    fun everyGeneratedQuestionIsSolvable() {
        val g = generator()
        for (type in ExerciseType.entries) repeat(400) {
            when (val q = g.next(type)) {
                is ChoiceQuestion -> {
                    assertTrue(q.toString(), q.answer in q.options)
                    assertEquals(q.toString(), q.options.size, q.options.toSet().size)
                    assertTrue(q.options.size >= 2)
                    assertTrue(AnswerChecker.check(q, q.answer).correct)
                    assertFalse(q.prompt.contains('*'))
                }
                is TilesQuestion -> {
                    val pool = q.tiles.toMutableList()
                    assertTrue(q.answer.all { pool.remove(it) })
                    assertTrue(AnswerChecker.check(q, q.answer).correct)
                    assertTrue(q.answerText.endsWith(q.ending))
                    assertEquals(q.prompt, q.explanation)
                }
                is TypingQuestion -> assertTrue(AnswerChecker.check(q, q.accepted.first()).correct)
            }
        }
    }

    @Test
    fun gapQuestionsHaveABlankAndTranslation() {
        val q = generator().next(ExerciseType.GAP) as ChoiceQuestion
        assertTrue(q.prompt.contains(ExerciseGenerator.GAP))
        assertTrue(q.hint!!.isNotBlank())
        assertFalse(q.speak.contains(ExerciseGenerator.GAP))
    }

    @Test
    fun writePrefersStudiedWordsAndGivesHints() {
        val studied = content.words.filter { it.article != null }.take(10).toSet()
        val g = generator(studied = { it in studied })
        repeat(30) {
            val q = g.next(ExerciseType.WRITE) as TypingQuestion
            assertTrue(q.word in studied)
            assertEquals("Іменник: напиши разом з en або et", q.hint)
        }
        val verbs = content.words.filter { it.isVerb }.take(10).toSet()
        val gv = generator(studied = { it in verbs })
        assertEquals("Дієслово: напиши з at", (gv.next(ExerciseType.WRITE) as TypingQuestion).hint)
        val other = content.words.filter { !it.isVerb && it.article == null && it.danish.split(" ").size == 1 }.take(10).toSet()
        assertNull((generator(studied = { it in other }).next(ExerciseType.WRITE) as TypingQuestion).hint)
    }

    @Test
    fun dictationProducesWordsAndSentencesAndIsHiddenWithoutSpeech() {
        val g = generator(seed = 3)
        val kinds = (1..60).map { (g.next(ExerciseType.DICTATION) as TypingQuestion).word != null }.toSet()
        assertEquals(setOf(true, false), kinds)
        assertFalse(ExerciseType.DICTATION in generator(speech = false).availableTypes())
        assertTrue(ExerciseType.DICTATION in generator().availableTypes())
        repeat(100) { assertFalse(generator(seed = it, speech = false).next(ExerciseType.MIX).tag == ExerciseType.DICTATION.title) }
    }

    @Test
    fun nounQuestionsCoverGenderAndPlural() {
        val g = generator(seed = 5)
        val tags = (1..60).map { g.next(ExerciseType.NOUN).tag }.toSet()
        assertEquals(setOf("Рід іменника", "Множина"), tags)
    }

    @Test
    fun nounFallsBackToGenderWhenNoPluralTablesMatch() {
        val only = listOf(Word("stol", "стілець", "T", Article.EN))
        val g = ExerciseGenerator(content, { only }, { false }, Random(1))
        repeat(10) { assertEquals("Рід іменника", g.next(ExerciseType.NOUN).tag) }
    }

    @Test
    fun verbQuestionsAskAllThreeForms() {
        val g = generator(seed = 9)
        val asked = (1..120).map { (g.next(ExerciseType.VERB) as ChoiceQuestion).prompt.substringAfter("→ ") }.toSet()
        assertEquals(setOf("теперішній час", "минулий час", "перфект"), asked)
    }
}

class AnswerCheckerTest {

    private val noun = TypingQuestion("t", "книга", null, listOf("en bog"), listOf("bog"), "en bog", word = Word("bog", "книга", "T", Article.EN))
    private val verb = TypingQuestion("t", "йти", null, listOf("at gå"), listOf("gå"), "at gå", word = Word("at gå", "йти", "T"))

    @Test
    fun exactAnswersIgnoreCaseAndPunctuation() {
        assertEquals(Verdict(true, "en bog"), AnswerChecker.check(noun, " En Bog. "))
    }

    @Test
    fun missingArticleOrAtIsForgivenWithANote() {
        assertEquals("Не забудь артикль: en bog.", AnswerChecker.check(noun, "bog").note)
        assertEquals("Повна форма: at gå.", AnswerChecker.check(verb, "gå").note)
    }

    @Test
    fun oneTypoIsForgivenInLongerAnswers() {
        val v = AnswerChecker.check(noun, "en bok")
        assertTrue(v.correct)
        assertEquals("Маленька помилка. Правильно: en bog.", v.note)
        assertFalse(AnswerChecker.check(TypingQuestion("t", "", null, listOf("hus"), speak = "hus"), "hes").correct)
    }

    @Test
    fun wrongAndEmptyAnswersFail() {
        assertEquals(Verdict(false, "en bog"), AnswerChecker.check(noun, "et hus"))
        assertFalse(AnswerChecker.check(noun, "").correct)
    }

    @Test
    fun choiceAndTiles() {
        val c = ChoiceQuestion("t", "p", null, listOf("a", "b"), "a", "a")
        assertTrue(AnswerChecker.check(c, "a").correct)
        assertFalse(AnswerChecker.check(c, "b").correct)
        val t = TilesQuestion("t", "p", listOf("b", "a"), listOf("a", "b"), ".", "a b.")
        assertEquals(Verdict(true, "a b."), AnswerChecker.check(t, listOf("a", "b")))
        assertFalse(AnswerChecker.check(t, listOf("b", "a")).correct)
    }
}

class ExerciseSessionTest {

    private val content = TestContent.content
    private fun session(type: ExerciseType, seed: Int = 1, length: Int = 3) =
        ExerciseSession(type, ExerciseGenerator(content, { content.words }, { false }, Random(seed)), length)

    @Test
    fun scoresAndCollectsMistakesUntilFinished() {
        val s = session(ExerciseType.GAP)
        val first = s.question as ChoiceQuestion
        assertTrue(s.answerChoice(first.answer).correct)
        assertTrue(s.isAnswered)
        s.next()
        val second = s.question as ChoiceQuestion
        assertTrue(ExerciseSession.promptOf(second) != ExerciseSession.promptOf(first))
        assertFalse(s.answerChoice(second.options.first { it != second.answer }).correct)
        s.next()
        assertTrue(s.isLast)
        s.answerChoice((s.question as ChoiceQuestion).answer)
        s.next()
        assertTrue(s.isFinished)
        assertEquals(2, s.score)
        assertEquals(listOf(Mistake(second.prompt, second.answer)), s.mistakes)
        assertEquals(3, s.length)
        assertEquals(ExerciseType.GAP, s.type)
        val state = s.state()
        assertTrue(state.isFinished && state.isLast)
        assertEquals(2, state.score)
        assertEquals(2, state.index)
        assertEquals(s.mistakes, state.mistakes)
        assertEquals(ExerciseType.GAP, state.type)
        assertEquals(3, state.length)
        assertEquals(s.verdict, state.verdict)
        assertEquals(s.question, state.question)
    }

    @Test
    fun tilesAndTypingAnswers() {
        val tiles = session(ExerciseType.BUILD)
        assertTrue(tiles.answerTiles((tiles.question as TilesQuestion).answer).correct)
        val typing = session(ExerciseType.WRITE)
        assertTrue(typing.answerTyped((typing.question as TypingQuestion).accepted.first()).correct)
        val dict = session(ExerciseType.DICTATION, seed = 2)
        assertEquals((dict.question as TypingQuestion).audio, ExerciseSession.promptOf(dict.question))
    }

    @Test(expected = IllegalStateException::class)
    fun cannotSkipUnanswered() {
        session(ExerciseType.GAP).next()
    }

    @Test(expected = IllegalStateException::class)
    fun cannotAnswerTwice() {
        val s = session(ExerciseType.GAP)
        val q = s.question as ChoiceQuestion
        s.answerChoice(q.answer)
        s.answerChoice(q.answer)
    }

    @Test
    fun avoidsRepeatingTheSamePromptWhenPossible() {
        val tiny = content.copy(grammar = content.grammar.take(1).map { it.copy(rules = it.rules.take(1)) })
        val s = ExerciseSession(ExerciseType.NOUN, ExerciseGenerator(tiny, { listOf(Word("hus", "дім", "T", Article.ET)) }, { false }, Random(1)), 3)
        s.answerChoice("et")
        s.next()
        assertEquals("hus", (s.question as ChoiceQuestion).prompt)
        assertEquals(ExerciseSession.DEFAULT_LENGTH, 10)
    }
}
