package app.danskly.core.navigation

import app.danskly.core.exercise.ExerciseType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Every screen in the app. Adding a feature means adding a route here and a screen for it. */
sealed interface Route {
    data object Home : Route
    data object Today : Route
    data object Review : Route
    data object Topics : Route
    data class Topic(val name: String?) : Route
    data class Cards(val topic: String?) : Route
    data class WordDetails(val danish: String) : Route
    data class AddWord(val topic: String?) : Route
    data class Quiz(val topic: String?) : Route
    data object Exercises : Route
    data class Exercise(val type: ExerciseType) : Route
    data object ReadingList : Route
    data class Reading(val index: Int) : Route
    data object Grammar : Route
    data class GrammarSection(val index: Int) : Route
    data class Rule(val section: Int, val rule: Int) : Route
    data object Pronunciation : Route
    data object WritingList : Route
    data class Writing(val index: Int) : Route
    data object Tutor : Route
    data object Settings : Route
}

/** A simple back stack. The UI shows whatever is on top. */
class Navigator(start: Route = Route.Home) {
    private val stack = MutableStateFlow(listOf(start))
    val backStack: StateFlow<List<Route>> = stack.asStateFlow()

    val current: Route get() = stack.value.last()
    val canGoBack: Boolean get() = stack.value.size > 1

    fun open(route: Route) {
        stack.value = stack.value + route
    }

    /** Replaces the top screen, e.g. "next text" without growing the stack. */
    fun replace(route: Route) {
        stack.value = stack.value.dropLast(1) + route
    }

    /** Returns false when already on the first screen (the system should then close the app). */
    fun back(): Boolean {
        if (!canGoBack) return false
        stack.value = stack.value.dropLast(1)
        return true
    }
}
