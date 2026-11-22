package app.danskly.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import app.danskly.AppGraph
import app.danskly.core.exercise.ChoiceQuestion
import app.danskly.core.exercise.ExerciseGenerator
import app.danskly.core.exercise.ExerciseSession
import app.danskly.core.exercise.TilesQuestion
import app.danskly.core.exercise.TypingQuestion
import app.danskly.core.exercise.ExerciseType
import app.danskly.core.exercise.QuizMode
import app.danskly.core.learning.LearningRepository
import app.danskly.core.model.DailyTask
import app.danskly.core.model.Word
import app.danskly.core.navigation.Route
import app.danskly.support.FakeSpeaker
import app.danskly.support.UiTest
import app.danskly.support.testGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizScreenTest : UiTest() {

    private fun answerFirstOption() {
        compose.onAllNodes(tagStartsWith("option:"))[0].performClick()
        compose.waitForIdle()
    }

    @Test
    fun allModesWork() {
        launch(Route.Quiz(null))
        text("Далі").assertIsNotEnabled()
        answerFirstOption()
        text("Далі").assertIsEnabled()
        assertEquals(1, graph.repository.progress.value.quizTotal)
        assertTrue(speaker.spoken.isNotEmpty())
        click("Далі")
        for (mode in QuizMode.entries) {
            click(mode.label)
            answerFirstOption()
        }
        assertTrue(exists("Усі слова · правильно", substring = true))
    }

    @Test
    fun listeningHidesTheWordUntilAnswered() {
        launch(Route.Quiz("Подорожі"))
        click(QuizMode.LISTEN.label)
        assertTrue(exists("Послухай і обери переклад"))
        val heard = speaker.last!!
        answerFirstOption()
        assertTrue(exists(heard))
    }

    @Test
    fun articleModeHiddenForVerbTopics() {
        launch(Route.Quiz("Часті дієслова"))
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
