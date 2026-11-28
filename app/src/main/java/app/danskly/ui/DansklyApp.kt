package app.danskly.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.danskly.AppGraph
import app.danskly.core.navigation.Route
import app.danskly.feature.exercises.ExerciseScreen
import app.danskly.feature.exercises.ExercisesScreen
import app.danskly.feature.grammar.GrammarScreen
import app.danskly.feature.grammar.GrammarSectionScreen
import app.danskly.feature.grammar.PronunciationScreen
import app.danskly.feature.home.HomeScreen
import app.danskly.feature.quiz.QuizScreen
import app.danskly.feature.reading.ReadingListScreen
import app.danskly.feature.reading.ReadingScreen
import app.danskly.feature.reading.TextsScreen
import app.danskly.feature.settings.SettingsScreen
import app.danskly.feature.today.ReviewScreen
import app.danskly.feature.today.RuleScreen
import app.danskly.feature.today.TodayScreen
import app.danskly.feature.tutor.TutorScreen
import app.danskly.feature.words.AddWordScreen
import app.danskly.feature.words.CardsScreen
import app.danskly.feature.words.TopicScreen
import app.danskly.feature.words.TopicsScreen
import app.danskly.feature.words.WordDetailsScreen
import app.danskly.feature.writing.WritingListScreen
import app.danskly.feature.writing.WritingScreen
import app.danskly.ui.components.AppBackground
import app.danskly.ui.components.LocalSpeaker
import app.danskly.ui.components.TopBar
import app.danskly.ui.theme.DansklyTheme

/** The whole app: theme, top bar, back handling and the current screen. */
@Composable
fun DansklyApp(graph: AppGraph, dark: Boolean? = null) {
    val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalSpeaker provides graph.speaker) {
            val stack by graph.navigator.backStack.collectAsState()
            val route = stack.last()
            BackHandler(enabled = stack.size > 1) { graph.navigator.back() }
            AppBackground {
                Column(Modifier.fillMaxSize()) {
                    TopBar(
                        title = titleOf(route, graph),
                        showBack = stack.size > 1,
                        onBack = { graph.navigator.back() },
                        showSettings = route != Route.Settings,
                        onSettings = { graph.navigator.open(Route.Settings) },
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        key(stack.size, route) { RouteScreen(route, graph) }
                    }
                }
            }
        }
    }
    if (dark == null) DansklyTheme(content = content) else DansklyTheme(dark = dark, content = content)
}

@Composable
private fun RouteScreen(route: Route, graph: AppGraph) = when (route) {
    Route.Home -> HomeScreen(graph)
    Route.Today -> TodayScreen(graph)
    Route.Review -> ReviewScreen(graph)
    Route.Topics -> TopicsScreen(graph)
    is Route.Topic -> TopicScreen(graph, route.name)
    is Route.Cards -> CardsScreen(graph, route.topic)
    is Route.WordDetails -> WordDetailsScreen(graph, route.danish)
    is Route.AddWord -> AddWordScreen(graph, route.topic)
    is Route.Quiz -> QuizScreen(graph, route.topic)
    Route.Exercises -> ExercisesScreen(graph)
    is Route.Exercise -> ExerciseScreen(graph, route.type)
    Route.ReadingList -> ReadingListScreen(graph)
    is Route.Texts -> TextsScreen(graph, route.listening)
    is Route.Reading -> ReadingScreen(graph, route.index, route.listening)
    Route.Grammar -> GrammarScreen(graph)
    is Route.GrammarSection -> GrammarSectionScreen(graph, route.index)
    is Route.Rule -> RuleScreen(graph, route.section, route.rule)
    Route.Pronunciation -> PronunciationScreen(graph)
    Route.WritingList -> WritingListScreen(graph)
    is Route.Writing -> WritingScreen(graph, route.index)
    Route.Tutor -> TutorScreen(graph)
    Route.Settings -> SettingsScreen(graph)
}

/** The title shown in the top bar for each screen. */
fun titleOf(route: Route, graph: AppGraph): String {
    val content = graph.repository.content
    return when (route) {
        Route.Home -> ""
        Route.Today -> "Сьогодні"
        Route.Review -> "Повторення"
        Route.Topics -> "Слова"
        is Route.Topic -> route.name ?: "Усі слова"
        is Route.Cards -> route.topic ?: "Усі слова"
        is Route.WordDetails -> graph.repository.findWord(route.danish)?.display ?: route.danish
        is Route.AddWord -> "Нове слово"
        is Route.Quiz -> "Тест"
        Route.Exercises -> "Завдання"
        is Route.Exercise -> route.type.title
        Route.ReadingList -> "Читання й слухання"
        is Route.Texts -> if (route.listening) "Слухання" else "Читання"
        is Route.Reading -> content.reading[route.index].kind
        Route.Grammar -> "Граматика"
        is Route.GrammarSection -> content.grammar[route.index].title
        is Route.Rule -> "Правило"
        Route.Pronunciation -> "Вимова"
        Route.WritingList -> "Письмо"
        is Route.Writing -> content.writingTasks[route.index].title
        Route.Tutor -> "ШІ-тренер"
        Route.Settings -> "Налаштування"
    }
}

/** Standard scrollable screen body with comfortable margins. */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content,
    )
}
