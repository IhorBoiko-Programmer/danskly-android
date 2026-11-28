package app.danskly.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import app.danskly.AppGraph
import app.danskly.core.exercise.ChoiceQuestion
import app.danskly.core.exercise.ExerciseGenerator
import app.danskly.core.exercise.ExerciseSession
import app.danskly.core.exercise.ExerciseType
import app.danskly.core.exercise.QuizGenerator
import app.danskly.core.exercise.QuizMode
import app.danskly.core.exercise.QuizRound
import app.danskly.core.exercise.TilesQuestion
import app.danskly.core.exercise.TypingQuestion
import app.danskly.core.learning.LearningRepository
import app.danskly.core.model.DailyTask
import app.danskly.core.model.Word
import app.danskly.core.navigation.Route
import app.danskly.support.FakeSpeaker
import app.danskly.support.UiTest
import app.danskly.support.testGraph
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizScreenTest : UiTest() {

    /** The same questions the screen will ask: same words, same seed as [testGraph]'s random. */
    private fun mirror(topic: String?, mode: QuizMode, length: Int) =
        QuizGenerator(graph.repository.wordsIn(topic), Random(7)).round(mode, length)

    private fun start(topic: String?, mode: QuizMode = QuizMode.TRANSLATE, length: Int = 10): QuizRound {
        launch(Route.Quiz(topic))
        val round = mirror(topic, mode, length)
        if (mode != QuizMode.TRANSLATE) click(mode.label)
        clickTag("length:$length")
        return round
    }

    /** Answers the first [right] questions correctly and the rest wrongly, then opens the result. */
    private fun play(round: QuizRound, right: Int) {
        round.questions.forEachIndexed { i, q ->
            val option = if (i < right) q.answer else q.options.first { it != q.answer }
            clickTag("option:$option")
            click(if (i == round.size - 1) "Результат" else "Далі")
        }
    }

    @Test
    fun setupOffersModesAndThreeLengths() {
        launch(Route.Quiz("Подорожі"))
        assertTrue(exists("Подорожі · ${graph.repository.wordsIn("Подорожі").size} слів"))
        QuizMode.entries.forEach { assertTrue(it.label, exists(it.label)) }
        listOf(10, 20, 50).forEach { assertTrue(exists("Тест: $it питань")) }
    }

    @Test
    fun aTestHasAFixedNumberOfQuestions() {
        val round = start(null, length = 20)
        tag("quiz-counter").assertTextEquals("Питання 1 з 20")
        text("Далі").assertIsNotEnabled()
        clickTag("option:${round.current.answer}")
        assertTrue(exists("✓ 1"))
        assertTrue(speaker.spoken.isNotEmpty())
        click("Далі")
        tag("quiz-counter").assertTextEquals("Питання 2 з 20")
        assertEquals(1, graph.repository.progress.value.quizTotal)
    }

    @Test
    fun resultListsMistakesAndCanRestart() {
        val round = start("Подорожі")
        play(round, right = 7)
        tag("quiz-score").assertTextEquals("7 з 10")
        assertTrue(exists("Правильно 70%"))
        assertTrue(exists("🎉"))
        assertTrue(exists("Повтори ці слова"))
        assertTrue(exists(round.questions.last().word.display))
        assertEquals(7, graph.repository.progress.value.quizCorrect)
        assertEquals(10, graph.repository.progress.value.quizTotal)
        click("Ще раз")
        tag("quiz-counter").assertTextEquals("Питання 1 з 10")
    }

    @Test
    fun perfectScoreHasNoMistakeList() {
        play(start("Подорожі"), right = 10)
        assertTrue(exists("🏆"))
        assertFalse(exists("Повтори ці слова"))
        click("Інший тест")
        assertTrue(exists("Тест: 50 питань"))
    }

    @Test
    fun halfScore() {
        play(start("Подорожі"), right = 5)
        assertTrue(exists("👍"))
    }

    @Test
    fun zeroScore() {
        play(start("Подорожі"), right = 0)
        assertTrue(exists("💪"))
        tag("quiz-score").assertTextEquals("0 з 10")
    }

    @Test
    fun listeningHidesTheWordUntilAnswered() {
        val round = start("Подорожі", QuizMode.LISTEN)
        assertTrue(exists("Послухай і обери переклад"))
        assertEquals(round.current.word.display, speaker.last)
        clickTag("option:${round.current.answer}")
        assertTrue(exists(round.current.word.display))
    }

    @Test
    fun articleModeAsksEnOrEt() {
        val round = start("Подорожі", QuizMode.ARTICLE)
        assertTrue(exists(round.current.prompt))
        assertTrue(exists("en"))
        assertTrue(exists("et"))
    }

    @Test
    fun articleModeHiddenForVerbTopics() {
        launch(Route.Quiz("Щоденні дії"))
        assertFalse(exists(QuizMode.ARTICLE.label))
    }

    @Test
    fun tinyTopicExplainsWhyThereIsNoQuiz() {
        val g = testGraph()
        g.repository.addCustomWord(Word("ord-1", "слово", LearningRepository.MY_WORDS))
        launch(Route.Quiz(LearningRepository.MY_WORDS), g)
        assertTrue(exists("Для тесту потрібно щонайменше два слова в темі."))
    }
}

class ExercisesScreenTest : UiTest() {

    @Test
    fun listsAllTypesAndOpensOne() {
        launch(Route.Exercises)
        ExerciseType.entries.forEach { assertTrue(it.title, exists(it.title)) }
        click("Встав слово")
        assertEquals(Route.Exercise(ExerciseType.GAP), graph.navigator.current)
    }

    @Test
    fun dictationHiddenWithoutSpeech() {
        launch(Route.Exercises, testGraph(speaker = FakeSpeaker(available = false)))
        assertFalse(exists(ExerciseType.DICTATION.title))
    }
}

class ExerciseSessionScreenTest : UiTest() {

    /**
     * A mirror of the session shown on screen. The app and the mirror use the same random seed,
     * so the mirror always knows the current question and its answer.
     */
    private lateinit var mirror: ExerciseSession

    private fun start(type: ExerciseType, g: AppGraph = testGraph()) {
        launch(Route.Exercise(type), g)
        val repo = g.repository
        mirror = ExerciseSession(type, ExerciseGenerator(repo.content, { repo.allWords }, { repo.progress.value.boxOf(it) >= 1 }, Random(7), g.speaker.isAvailable.value))
    }

    private fun answerChoice(correct: Boolean) {
        val q = mirror.question as ChoiceQuestion
        val option = if (correct) q.answer else q.options.first { it != q.answer }
        clickTag("option:$option")
        mirror.answerChoice(option)
    }

    private fun next() {
        click(if (mirror.isLast) "Результат" else "Далі")
        mirror.next()
    }

    @Test
    fun gapSessionToResultsAndAgain() {
        start(ExerciseType.GAP)
        repeat(10) { i ->
            assertTrue(exists((mirror.question as ChoiceQuestion).prompt))
            answerChoice(correct = i != 0)
            if (i == 0) {
                assertTrue(exists("Не зовсім."))
                assertTrue(exists("Правильна відповідь:", substring = true))
            } else {
                assertTrue(exists("Правильно!"))
            }
            next()
        }
        tag("score").assertExists()
        assertTrue(exists("9/10"))
        assertTrue(exists("🏆"))
        assertTrue(exists("Помилки"))
        assertTrue(DailyTask.EXERCISE in graph.repository.todayLog().done)
        click("Ще раз")
        assertTrue(exists("1 з 10", substring = true))
    }

    @Test
    fun perfectSessionListsNoMistakes() {
        start(ExerciseType.GAP)
        repeat(10) {
            answerChoice(correct = true)
            next()
        }
        assertTrue(exists("10/10"))
        assertFalse(exists("Помилки"))
    }

    @Test
    fun poorResultAndBackToList() {
        start(ExerciseType.NOUN)
        repeat(10) {
            answerChoice(correct = false)
            next()
        }
        assertTrue(exists("Повтори правила й спробуй ще раз."))
        click("До завдань")
        assertEquals(Route.Home, graph.navigator.current)
    }

    @Test
    fun goodButNotGreatResult() {
        start(ExerciseType.VERB)
        repeat(10) { i ->
            answerChoice(correct = i % 3 != 0)
            next()
        }
        assertTrue(exists("Непогано, ще трохи практики."))
    }

    @Test
    fun tilesExercise() {
        start(ExerciseType.BUILD)
        text("Перевірити").assertIsNotEnabled()
        clickTag("bank:0")
        clickTag("picked:0")
        val q = mirror.question as TilesQuestion
        val used = mutableSetOf<Int>()
        q.answer.forEach { token ->
            val index = q.tiles.indices.first { q.tiles[it] == token && it !in used }
            used += index
            clickTag("bank:$index")
        }
        click("Перевірити")
        assertTrue(exists("Правильно!"))
    }

    @Test
    fun typingWithDanishLetterAndEnter() {
        start(ExerciseType.WRITE)
        tag("answer-field").performTextInput("x")
        click("ø")
        tag("answer-field").performImeAction()
        compose.waitForIdle()
        assertTrue(exists("Не зовсім."))
    }

    @Test
    fun typingCorrectAnswerGradesTheWord() {
        start(ExerciseType.WRITE)
        val q = mirror.question as TypingQuestion
        assertTrue(exists(q.prompt))
        tag("answer-field").performTextInput(q.accepted.first())
        click("Перевірити")
        assertTrue(exists("Правильно!"))
        assertTrue(q.word!!.danish in graph.repository.progress.value.cards)
    }

    @Test
    fun forgivenMistakeShowsANote() {
        start(ExerciseType.WRITE)
        val q = mirror.question as TypingQuestion
        tag("answer-field").performTextInput(q.alternatives.first())
        click("Перевірити")
        assertTrue(exists("Правильно!"))
        assertTrue(exists("Не забудь артикль", substring = true) || exists("Повна форма", substring = true) || q.alternatives.first() == q.accepted.first())
    }

    @Test
    fun dictationSpeaksAndHidesTheWord() {
        start(ExerciseType.DICTATION)
        val q = mirror.question as TypingQuestion
        assertEquals(q.audio, speaker.last)
        click("Перевірити")
        tag("answer-field").performTextInput("noget helt forkert")
        click("Перевірити")
        assertTrue(exists("Не зовсім."))
    }

    @Test
    fun mixSessionRuns() {
        start(ExerciseType.MIX)
        assertTrue(exists("1 з 10", substring = true))
    }
}
