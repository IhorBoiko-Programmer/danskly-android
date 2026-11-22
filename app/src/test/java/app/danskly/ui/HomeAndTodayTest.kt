package app.danskly.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import app.danskly.core.exercise.ExerciseType
import app.danskly.core.model.DailyTask
import app.danskly.core.navigation.Route
import app.danskly.feature.home.greetingFor
import app.danskly.support.UiTest
import app.danskly.support.testGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenTest : UiTest() {

    @Test
    fun everyTileOpensItsSection() {
        launch()
        val expected = listOf(
            "Сьогодні" to Route.Today, "Слова" to Route.Topics, "Завдання" to Route.Exercises,
            "Читання й слухання" to Route.ReadingList, "Граматика" to Route.Grammar, "Вимова" to Route.Pronunciation,
            "Письмо" to Route.WritingList, "Тест" to Route.Quiz(null), "ШІ-тренер" to Route.Tutor,
        )
        for ((title, route) in expected) {
            clickTag("tile:$title")
            assertEquals(route, graph.navigator.current)
            clickTag("back")
            assertEquals(Route.Home, graph.navigator.current)
        }
    }

    @Test
    fun showsBrandGreetingAndDailyProgress() {
        launch()
        assertTrue(exists("Dansk", substring = true))
        assertTrue(exists("0 на повторення · 10 нових"))
        assertTrue(exists("0 з 20 дій"))
        graph.repository.recordAction()
        graph.repository.grade(graph.repository.findWord("bog")!!, true)
        compose.waitForIdle()
        assertTrue(exists("серія 1 дн.", substring = true))
        assertTrue(exists("Вивчено 0"))
    }

    @Test
    fun saysWhenTodaysRevisionIsDone() {
        val g = testGraph()
        g.repository.newWordsToday().forEach { g.repository.grade(it, true) }
        launch(g = g)
        assertTrue(exists("Повторення на сьогодні виконано"))
    }

    @Test
    fun settingsGearOpensSettingsAndHidesThere() {
        launch()
        clickTag("settings")
        assertEquals(Route.Settings, graph.navigator.current)
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasTestTag("settings")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun darkThemeAndSystemThemeBothRender() {
        launch(dark = true)
        tag("tile:Слова").assertExists()
    }

    @Test
    fun greetingDependsOnTimeOfDay() {
        assertEquals("Godmorgen", greetingFor(7).first)
        assertEquals("Goddag", greetingFor(12).first)
        assertEquals("Godaften", greetingFor(20).first)
    }
}

class SystemThemeTest : UiTest() {
    @Test
    fun followsSystemTheme() {
        launch(dark = null)
        tag("tile:Слова").assertExists()
    }
}

class TodayScreenTest : UiTest() {

    @Test
    fun listsFourDailyTasksAndOpensThem() {
        launch(Route.Today)
        assertTrue(exists("Повторити слова"))
        tag("goal-ring").assertExists()
        click("Вправа дня")
        assertEquals(Route.Exercise(ExerciseType.MIX), graph.navigator.current)
        clickTag("back")
        click("Текст дня")
        assertEquals(Route.Reading(graph.repository.textOfDay()), graph.navigator.current)
        clickTag("back")
        click("Правило дня")
        val (s, r) = graph.repository.ruleOfDay()
        assertEquals(Route.Rule(s, r), graph.navigator.current)
        assertTrue(DailyTask.RULE in graph.repository.todayLog().done)
        clickTag("back")
        assertTrue(exists("✅"))
        click("Повторити слова")
        assertEquals(Route.Review, graph.navigator.current)
    }

    @Test
    fun showsGoalReachedAndEmptyRevision() {
        val g = testGraph()
        repeat(20) { g.repository.recordAction() }
        g.repository.newWordsToday().forEach { g.repository.grade(it, true) }
        launch(Route.Today, g)
        assertTrue(exists("Денну мету виконано!", substring = true))
        assertTrue(exists("На сьогодні все повторено"))
    }
}

class ReviewScreenTest : UiTest() {

    @Test
    fun goesThroughNewWordsUntilDone() {
        launch(Route.Review)
        assertTrue(exists("0 з 10 · нове слово"))
        tag("flip-card").assertExists()
        text("Знаю").assertIsNotEnabled()
        clickTag("flip-card")
        click("Ще раз")
        assertTrue(exists("0 з 10", substring = true))
        repeat(10) {
            clickTag("flip-card")
            click("Знаю")
        }
        assertTrue(exists("Готово!"))
        assertTrue(DailyTask.REVIEW in graph.repository.todayLog().done)
        click("Зробити вправу дня")
        assertEquals(Route.Exercise(ExerciseType.MIX), graph.navigator.current)
    }

    @Test
    fun opensWordDetailsFromTheBackOfTheCard() {
        launch(Route.Review)
        clickTag("flip-card")
        click("Усі форми й приклади ›")
        assertTrue(graph.navigator.current is Route.WordDetails)
    }

    @Test
    fun autoSpeaksAndShowsDueWords() {
        val g = testGraph()
        g.repository.updateSettings { it.copy(autoSpeak = true) }
        val w = g.repository.findWord("bog")!!
        g.repository.grade(w, false)
        launch(Route.Review, g)
        assertTrue(speaker.spoken.isNotEmpty())
        assertFalse(exists("0 з 11 · нове слово"))
    }

    @Test
    fun nothingToReview() {
        val g = testGraph()
        g.repository.newWordsToday().forEach { g.repository.grade(it, true) }
        launch(Route.Review, g)
        assertTrue(exists("Усе повторено"))
        assertFalse(DailyTask.REVIEW in graph.repository.todayLog().done)
    }
}

class RuleScreenTest : UiTest() {

    @Test
    fun showsRuleWithExamplesAndLinks() {
        launch(Route.Rule(2, 0))
        val rule = graph.repository.content.grammar[2].rules[0]
        assertTrue(exists(rule.title))
        compose.onNodeWithTag("title").assertTextEquals("Правило")
        click("Потренуватися: встав слово")
        assertEquals(Route.Exercise(ExerciseType.GAP), graph.navigator.current)
        clickTag("back")
        click("Усі правила розділу", substring = true)
        assertEquals(Route.GrammarSection(2), graph.navigator.current)
    }
}
